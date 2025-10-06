import { ChangeDetectionStrategy, Component, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, isPlatformBrowser } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { UsersService } from './users.service';
import { AuthService } from '../auth/auth.service';
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
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
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
    password: [
      '',
      [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]
    ],
    role: ['TECH', Validators.required]
  });

  protected readonly editForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    role: ['TECH', Validators.required],
    password: ['']
  });

  protected readonly changePasswordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: [
      '',
      [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]
    ],
    confirmPassword: ['', Validators.required]
  });

  protected readonly page = signal<UsersPageResponseDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly updating = signal(false);
  protected readonly deletingId = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly changePasswordError = signal<string | null>(null);
  protected readonly changePasswordSuccess = signal(false);
  protected readonly changingPassword = signal(false);
  private readonly _currentPage = signal(0);
  protected readonly currentPage = this._currentPage;
  protected readonly editingUser = signal<UserResponseDto | null>(null);
  protected readonly activeRole = computed(() => this.auth.role());
  protected readonly currentUserEmail = computed(() => this.auth.email());
  protected readonly isAdmin = computed(() => this.activeRole() === 'ADMIN');
  protected readonly showingCreate = signal(false);

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
    if (!this.isAdmin()) {
      return;
    }
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
          password: payload.password.trim(),
          role: payload.role as UserRole
        })
      );
      this.resetCreateForm();
      this.showingCreate.set(false);
      await this.loadUsers(0);
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  startEdit(user: UserResponseDto): void {
    if (!this.canEditUser(user)) {
      return;
    }
    this.editingUser.set(user);
    this.editForm.enable({ emitEvent: false });
    if (!this.isAdmin()) {
      this.editForm.get('email')?.disable({ emitEvent: false });
      this.editForm.get('fullName')?.disable({ emitEvent: false });
      this.editForm.get('role')?.disable({ emitEvent: false });
      this.editForm.get('password')?.disable({ emitEvent: false });
    }
    this.editForm.setValue({
      email: user.email,
      fullName: user.fullName,
      role: user.role,
      password: ''
    });
    this.changePasswordForm.reset({ currentPassword: '', newPassword: '', confirmPassword: '' });
    this.changePasswordError.set(null);
    this.changePasswordSuccess.set(false);
  }

  cancelEdit(): void {
    this.editingUser.set(null);
    this.editForm.reset({ email: '', fullName: '', role: 'TECH', password: '' });
    this.editForm.enable({ emitEvent: false });
    this.changePasswordForm.reset({ currentPassword: '', newPassword: '', confirmPassword: '' });
    this.changePasswordError.set(null);
    this.changePasswordSuccess.set(false);
  }

  startCreate(): void {
    if (!this.isAdmin()) {
      return;
    }
    this.resetCreateForm();
    this.showingCreate.set(true);
  }

  cancelCreate(): void {
    if (!this.showingCreate()) {
      return;
    }
    this.resetCreateForm();
    this.showingCreate.set(false);
  }

  async onUpdateUser(): Promise<void> {
    const target = this.editingUser();
    if (!target) {
      return;
    }
    if (!this.isAdmin()) {
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
          role: payload.role as UserRole,
          password: payload.password?.trim()
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
    if (!this.isAdmin() || this.deletingId() !== null) {
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

  async onChangePassword(): Promise<void> {
    const target = this.editingUser();
    if (!target || !this.isSelf(target)) {
      return;
    }
    if (this.changePasswordForm.invalid || this.changingPassword()) {
      this.changePasswordForm.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword, confirmPassword } = this.changePasswordForm.getRawValue();
    if (newPassword !== confirmPassword) {
      this.changePasswordError.set('Passwords do not match');
      return;
    }
    this.changingPassword.set(true);
    this.changePasswordError.set(null);
    this.changePasswordSuccess.set(false);
    try {
      await this.auth.changePassword(currentPassword, newPassword);
      this.changePasswordSuccess.set(true);
      this.changePasswordForm.reset({ currentPassword: '', newPassword: '', confirmPassword: '' });
      await this.router.navigate(['/login']);
    } catch (error) {
      this.changePasswordError.set(this.describeError(error));
    } finally {
      this.changingPassword.set(false);
    }
  }

  logout(): void {
    this.auth.logout();
    this.cancelEdit();
    this.page.set(null);
    void this.router.navigate(['/login']);
  }

  protected canEditUser(user: UserResponseDto): boolean {
    if (this.isAdmin()) {
      return true;
    }
    return this.isSelf(user);
  }

  protected canDeleteUser(user: UserResponseDto): boolean {
    return this.isAdmin();
  }

  protected isSelf(user: UserResponseDto): boolean {
    const email = this.currentUserEmail();
    return !!email && user.email.toLowerCase() === email.toLowerCase();
  }

  private resetCreateForm(): void {
    this.createForm.reset({ email: '', fullName: '', password: '', role: 'TECH' });
    this.createForm.markAsPristine();
    this.createForm.markAsUntouched();
  }

  protected describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        this.auth.logout();
        void this.router.navigate(['/login']);
        return 'Authentication required';
      }
      const detail = (error.error?.detail as string | undefined) ?? error.message;
      return detail || 'Request failed';
    }
    return 'Request failed';
  }
}
