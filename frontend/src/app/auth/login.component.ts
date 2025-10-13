import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from './auth.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly demoPassword = 'Admin123!';
  private readonly dispatcherDemoAccounts = ['lucie.fabre@sip.local', 'pierre.leroy@sip.local'];
  private readonly technicianDemoAccounts = [
    'alexandre.martin@sip.local',
    'lea.bernard@sip.local',
    'yacine.benali@sip.local',
    'sophia.renard@sip.local',
    'nicolas.petit@sip.local'
  ];

  protected readonly loginForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  async onSubmit(): Promise<void> {
    if (this.loginForm.invalid || this.loading()) {
      this.loginForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.loginForm.getRawValue();
    try {
      await this.auth.login(email.trim(), password);
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/users';
      await this.router.navigateByUrl(returnUrl);
    } catch (err) {
      this.error.set(this.describeError(err));
    } finally {
      this.loading.set(false);
    }
  }

  protected loginAsDemo(role: 'dispatcher' | 'technician'): void {
    if (this.loading()) {
      return;
    }
    const pool =
      role === 'dispatcher' ? this.dispatcherDemoAccounts : this.technicianDemoAccounts;
    if (!pool.length) {
      return;
    }
    const email = pool[Math.floor(Math.random() * pool.length)];
    this.loginForm.setValue({ email, password: this.demoPassword });
    void this.onSubmit();
  }

  private describeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'Email ou mot de passe invalide';
      }
      const detail = (error.error?.detail as string | undefined) ?? error.message;
      return detail || 'Échec de l’authentification';
    }
    if (error instanceof Error) {
      return error.message || 'Échec de l’authentification';
    }
    return 'Échec de l’authentification';
  }
}
