import { ChangeDetectionStrategy, Component, PLATFORM_ID, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, isPlatformBrowser } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { UsersService } from './users.service';
import {
  CreateUserPayload,
  UpdateUserPayload,
  UserResponseDto,
  UserRole,
  UsersPageResponseDto
} from './user.models';

@Component({
  selector: 'app-users-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DatePipe],
  templateUrl: './users-page.component.html',
  styleUrl: './users-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsersPageComponent {
  private readonly usersService = inject(UsersService);
  private readonly fb = inject(FormBuilder);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  protected readonly roles: readonly UserRole[] = ['ADMIN', 'DISPATCHER', 'TECH'];

  protected readonly filtersForm = this.fb.nonNullable.group({
    query: [''],
    role: [''],
    size: [20]
  });

  protected readonly createForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    role: ['TECH', Validators.required]
  });

  protected readonly editForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    role: ['TECH', Validators.required]
  });

  protected readonly page = signal<UsersPageResponseDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly updating = signal(false);
  protected readonly deletingId = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  private readonly _currentPage = signal(0);
  protected readonly currentPage = this._currentPage;
  protected readonly editingUser = signal<UserResponseDto | null>(null);

  constructor() {
    if (this.isBrowser) {
      void this.loadUsers(0);
    }
  }

  onRefresh(): void {
    void this.loadUsers(this._currentPage());
  }

  async loadUsers(pageIndex = this._currentPage()): Promise<void> {
    if (!this.isBrowser) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { query, role, size } = this.filtersForm.getRawValue();
    try {
      const response = await firstValueFrom(
        this.usersService.list({
          page: pageIndex,
          size,
          query: query.trim() ? query.trim() : undefined,
          role: role ? (role as UserRole) : undefined
        })
      );
      this.page.set(response);
      this._currentPage.set(response.page);
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async onFilterSubmit(): Promise<void> {
    await this.loadUsers(0);
  }

  async onResetFilters(): Promise<void> {
    this.filtersForm.setValue({ query: '', role: '', size: 20 });
    await this.loadUsers(0);
  }

  async onCreateUser(): Promise<void> {
    if (this.createForm.invalid || this.saving()) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const payload = this.createForm.getRawValue() as CreateUserPayload;
    try {
      await firstValueFrom(
        this.usersService.create({
          email: payload.email.trim(),
          fullName: payload.fullName.trim(),
          role: payload.role as UserRole
        })
      );
      this.createForm.reset({ email: '', fullName: '', role: 'TECH' });
      await this.loadUsers(0);
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  startEdit(user: UserResponseDto): void {
    this.editingUser.set(user);
    this.editForm.setValue({
      email: user.email,
      fullName: user.fullName,
      role: user.role
    });
  }

  cancelEdit(): void {
    this.editingUser.set(null);
    this.editForm.reset({ email: '', fullName: '', role: 'TECH' });
  }

  async onUpdateUser(): Promise<void> {
    const target = this.editingUser();
    if (!target) {
      return;
    }
    if (this.editForm.invalid || this.updating()) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.updating.set(true);
    const payload = this.editForm.getRawValue() as UpdateUserPayload;
    try {
      await firstValueFrom(
        this.usersService.update(target.id, {
          email: payload.email.trim(),
          fullName: payload.fullName.trim(),
          role: payload.role as UserRole
        })
      );
      this.cancelEdit();
      await this.loadUsers(this._currentPage());
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.updating.set(false);
    }
  }

  async onDeleteUser(user: UserResponseDto): Promise<void> {
    if (this.deletingId() !== null) {
      return;
    }
    const confirmed = window.confirm(`Delete ${user.email}?`);
    if (!confirmed) {
      return;
    }
    this.deletingId.set(user.id);
    if (this.editingUser()?.id === user.id) {
      this.cancelEdit();
    }
    try {
      await firstValueFrom(this.usersService.delete(user.id));
      const pageData = this.page();
      const hasOnlyOneItem = pageData ? pageData.content.length === 1 : false;
      const targetPage = hasOnlyOneItem && this._currentPage() > 0 ? this._currentPage() - 1 : this._currentPage();
      await this.loadUsers(targetPage);
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.deletingId.set(null);
    }
  }

  async changePage(delta: number): Promise<void> {
    const pageData = this.page();
    if (!pageData || this.loading()) {
      return;
    }
    const target = pageData.page + delta;
    if (target < 0 || target > pageData.totalPages - 1) {
      return;
    }
    await this.loadUsers(target);
  }

  protected describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const detail = (error.error?.detail as string | undefined) ?? error.message;
      return detail || 'Request failed';
    }
    return 'Request failed';
  }
}
