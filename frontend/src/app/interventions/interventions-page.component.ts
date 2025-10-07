import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, Subscription } from 'rxjs';

import {
  CreateInterventionPayload,
  InterventionAssignmentMode,
  InterventionResponseDto,
  InterventionStatus,
  InterventionsPageResponseDto,
  TechnicianSummary,
  UpdateInterventionPayload
} from './intervention.models';
import { InterventionsService } from './interventions.service';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-interventions-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DatePipe],
  templateUrl: './interventions-page.component.html',
  styleUrl: './interventions-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InterventionsPageComponent implements OnDestroy {
  private readonly interventionsService = inject(InterventionsService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly statuses: readonly InterventionStatus[] = [
    'SCHEDULED',
    'IN_PROGRESS',
    'COMPLETED',
    'VALIDATED'
  ];
  protected readonly assignmentModes: readonly InterventionAssignmentMode[] = ['MANUAL', 'AUTO'];

  protected readonly filtersForm = this.fb.nonNullable.group({
    query: [''],
    status: [''],
    assignmentMode: [''],
    technicianId: [''],
    size: [20]
  });

  protected readonly createForm = this.fb.nonNullable.group({
    reference: ['', [Validators.required, Validators.maxLength(50)]],
    title: ['', [Validators.required, Validators.maxLength(160)]],
    description: [''],
    plannedAt: [this.defaultPlannedAt(), Validators.required],
    assignmentMode: ['MANUAL', Validators.required],
    technicianId: [''],
    latitude: ['', [Validators.min(-90), Validators.max(90)]],
    longitude: ['', [Validators.min(-180), Validators.max(180)]]
  });

  protected readonly editForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(160)]],
    description: [''],
    plannedAt: ['', Validators.required],
    assignmentMode: ['MANUAL', Validators.required],
    technicianId: [''],
    latitude: ['', [Validators.min(-90), Validators.max(90)]],
    longitude: ['', [Validators.min(-180), Validators.max(180)]]
  });

  protected readonly page = signal<InterventionsPageResponseDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly updating = signal(false);
  protected readonly statusUpdatingId = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly technicians = signal<TechnicianSummary[]>([]);
  protected readonly editingIntervention = signal<InterventionResponseDto | null>(null);
  protected readonly showingCreate = signal(false);
  protected readonly currentPage = signal(0);
  protected readonly isManager = computed(() => {
    const role = this.auth.role();
    return role === 'ADMIN' || role === 'DISPATCHER';
  });
  protected readonly isTechnician = computed(() => this.auth.role() === 'TECH');

  private readonly subscriptions = new Subscription();

  constructor() {
    this.subscriptions.add(
      this.createForm.controls.assignmentMode.valueChanges.subscribe(mode => {
        if (mode === 'AUTO') {
          this.createForm.patchValue({ technicianId: '' }, { emitEvent: false });
        }
      })
    );
    this.subscriptions.add(
      this.editForm.controls.assignmentMode.valueChanges.subscribe(mode => {
        if (mode === 'AUTO') {
          this.editForm.patchValue({ technicianId: '' }, { emitEvent: false });
        }
      })
    );

    void this.initialize();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  protected get canManage(): boolean {
    return this.isManager();
  }

  protected get canProgress(): boolean {
    return this.isManager() || this.isTechnician();
  }

  async onFilterSubmit(): Promise<void> {
    await this.loadInterventions(0);
  }

  async onResetFilters(): Promise<void> {
    this.filtersForm.reset({ query: '', status: '', assignmentMode: '', technicianId: '', size: 20 });
    await this.loadInterventions(0);
  }

  async onRefresh(): Promise<void> {
    await this.loadInterventions(this.currentPage());
  }

  startCreate(): void {
    if (!this.canManage) {
      return;
    }
    this.resetCreateForm();
    this.showingCreate.set(true);
  }

  cancelCreate(): void {
    this.showingCreate.set(false);
    this.resetCreateForm();
  }

  async onCreateIntervention(): Promise<void> {
    if (!this.canManage || this.createForm.invalid || this.saving()) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const formValue = this.createForm.getRawValue();
    const payload: CreateInterventionPayload = {
      reference: formValue.reference.trim(),
      title: formValue.title.trim(),
      description: this.normalizeOptional(formValue.description),
      plannedAt: this.toIsoString(formValue.plannedAt),
      assignmentMode: formValue.assignmentMode as InterventionAssignmentMode,
      technicianId: this.parseTechnicianId(formValue.technicianId),
      latitude: this.parseCoordinate(formValue.latitude),
      longitude: this.parseCoordinate(formValue.longitude)
    };
    if (payload.assignmentMode === 'AUTO') {
      payload.technicianId = null;
    }
    try {
      await firstValueFrom(this.interventionsService.create(payload));
      this.showingCreate.set(false);
      this.resetCreateForm();
      await this.loadInterventions(0);
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  startEdit(intervention: InterventionResponseDto): void {
    if (!this.canManage) {
      return;
    }
    this.editingIntervention.set(intervention);
    this.editForm.setValue({
      title: intervention.title,
      description: intervention.description ?? '',
      plannedAt: this.toLocalDateTimeInput(intervention.plannedAt),
      assignmentMode: intervention.assignmentMode,
      technicianId: intervention.technician ? String(intervention.technician.id) : '',
      latitude: intervention.latitude != null ? String(intervention.latitude) : '',
      longitude: intervention.longitude != null ? String(intervention.longitude) : ''
    });
  }

  cancelEdit(): void {
    this.editingIntervention.set(null);
    this.editForm.reset({
      title: '',
      description: '',
      plannedAt: '',
      assignmentMode: 'MANUAL',
      technicianId: '',
      latitude: '',
      longitude: ''
    });
  }

  async onUpdateIntervention(): Promise<void> {
    const intervention = this.editingIntervention();
    if (!intervention || !this.canManage || this.editForm.invalid || this.updating()) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.updating.set(true);
    this.error.set(null);
    const formValue = this.editForm.getRawValue();
    const payload: UpdateInterventionPayload = {
      title: formValue.title.trim(),
      description: this.normalizeOptional(formValue.description),
      plannedAt: this.toIsoString(formValue.plannedAt),
      assignmentMode: formValue.assignmentMode as InterventionAssignmentMode,
      technicianId: this.parseTechnicianId(formValue.technicianId),
      latitude: this.parseCoordinate(formValue.latitude),
      longitude: this.parseCoordinate(formValue.longitude)
    };
    if (payload.assignmentMode === 'AUTO') {
      payload.technicianId = null;
    }
    try {
      const response = await firstValueFrom(this.interventionsService.update(intervention.id, payload));
      this.editingIntervention.set(response);
      await this.loadInterventions(this.currentPage());
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.updating.set(false);
    }
  }

  async advanceStatus(intervention: InterventionResponseDto): Promise<void> {
    const next = this.nextStatus(intervention.status);
    if (!next || !this.canProgress || this.statusUpdatingId() || !this.canAdvanceTo(next)) {
      return;
    }
    this.statusUpdatingId.set(intervention.id);
    this.error.set(null);
    try {
      await firstValueFrom(this.interventionsService.updateStatus(intervention.id, { status: next }));
      await this.loadInterventions(this.currentPage());
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.statusUpdatingId.set(null);
    }
  }

  async loadPage(pageIndex: number): Promise<void> {
    await this.loadInterventions(pageIndex);
  }

  async changePage(step: number): Promise<void> {
    const page = this.page();
    if (!page) {
      return;
    }
    const next = page.page + step;
    if (next < 0 || (page.totalPages && next >= page.totalPages)) {
      return;
    }
    await this.loadInterventions(next);
  }

  protected trackById(_index: number, intervention: InterventionResponseDto): number {
    return intervention.id;
  }

  protected nextStatus(status: InterventionStatus): InterventionStatus | null {
    switch (status) {
      case 'SCHEDULED':
        return 'IN_PROGRESS';
      case 'IN_PROGRESS':
        return 'COMPLETED';
      case 'COMPLETED':
        return 'VALIDATED';
      default:
        return null;
    }
  }

  protected canAdvanceTo(nextStatus: InterventionStatus): boolean {
    if (this.isManager()) {
      return true;
    }
    if (this.isTechnician()) {
      return nextStatus === 'IN_PROGRESS' || nextStatus === 'COMPLETED';
    }
    return false;
  }

  protected statusLabel(status: InterventionStatus): string {
    switch (status) {
      case 'SCHEDULED':
        return 'Planifiée';
      case 'IN_PROGRESS':
        return 'En cours';
      case 'COMPLETED':
        return 'Terminée';
      case 'VALIDATED':
      default:
        return 'Validée';
    }
  }

  protected assignmentLabel(mode: InterventionAssignmentMode): string {
    return mode === 'AUTO' ? 'Auto' : 'Manuelle';
  }

  protected describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.error && typeof error.error === 'object' && 'detail' in error.error) {
        return String(error.error.detail);
      }
      return error.message;
    }
    if (error instanceof Error) {
      return error.message;
    }
    return 'Une erreur est survenue.';
  }

  private async initialize(): Promise<void> {
    await Promise.all([this.loadTechnicians(), this.loadInterventions(0)]);
  }

  private async loadInterventions(pageIndex = this.currentPage()): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    const { query, status, assignmentMode, technicianId, size } = this.filtersForm.getRawValue();
    try {
      const response = await firstValueFrom(
        this.interventionsService.list({
          page: pageIndex,
          size,
          query: query.trim() ? query.trim() : undefined,
          status: status ? (status as InterventionStatus) : undefined,
          assignmentMode: assignmentMode ? (assignmentMode as InterventionAssignmentMode) : undefined,
          technicianId: technicianId ? Number(technicianId) : undefined
        })
      );
      this.page.set(response);
      this.currentPage.set(response.page);
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  private async loadTechnicians(): Promise<void> {
    try {
      const technicians = await firstValueFrom(this.interventionsService.listTechnicians());
      this.technicians.set(technicians);
    } catch (error) {
      // silently ignore but keep visible error state if needed
    }
  }

  private parseTechnicianId(value: string): number | null {
    if (!value) {
      return null;
    }
    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : parsed;
  }

  private normalizeOptional(value: string | null | undefined): string | null {
    if (!value) {
      return null;
    }
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }

  private toIsoString(value: string): string {
    const date = new Date(value);
    return date.toISOString();
  }

  private toLocalDateTimeInput(value: string): string {
    const date = new Date(value);
    const pad = (num: number) => String(num).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(
      date.getMinutes()
    )}`;
  }

  private defaultPlannedAt(): string {
    const now = new Date();
    now.setMinutes(now.getMinutes() + 30);
    return this.toLocalDateTimeInput(now.toISOString());
  }

  private resetCreateForm(): void {
    this.createForm.reset({
      reference: '',
      title: '',
      description: '',
      plannedAt: this.defaultPlannedAt(),
      assignmentMode: 'MANUAL',
      technicianId: '',
      latitude: '',
      longitude: ''
    });
  }

  private parseCoordinate(value: string): number | null {
    if (!value) {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
}
