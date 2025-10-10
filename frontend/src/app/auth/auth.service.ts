import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

interface LoginResponseDto {
  token: string;
  email: string;
  role: string;
}

interface SessionResponseDto {
  email: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly tokenSignal = signal<string | null>(null);
  private readonly roleSignal = signal<string | null>(null);
  private readonly emailSignal = signal<string | null>(null);
  private readonly authenticatedSignal = signal(false);
  private sessionInitPromise: Promise<void> | null = null;
  private sessionInitialized = false;

  async ensureSessionInitialized(): Promise<void> {
    if (this.sessionInitialized) {
      return;
    }
    if (!this.isBrowser) {
      this.resetSessionState();
      this.sessionInitialized = true;
      return;
    }
    if (!this.sessionInitPromise) {
      this.sessionInitPromise = this.restoreSession().finally(() => {
        this.sessionInitPromise = null;
      });
    }
    await this.sessionInitPromise;
  }

  token(): string | null {
    return this.tokenSignal();
  }

  role(): string | null {
    return this.roleSignal();
  }

  email(): string | null {
    return this.emailSignal();
  }

  isAuthenticated(): boolean {
    return this.authenticatedSignal();
  }

  async login(email: string, password: string): Promise<void> {
    if (!this.isBrowser) {
      throw new Error('Login is not available during server-side rendering');
    }
    const response = await firstValueFrom(
      this.http.post<LoginResponseDto>('/api/auth/login', { email, password })
    );
    this.tokenSignal.set(response.token ?? null);
    this.applySession({ email: response.email, role: response.role });
    this.sessionInitialized = true;
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>('/api/auth/change-password', {
        currentPassword,
        newPassword
      })
    );
    await this.logout();
  }

  async logout(): Promise<void> {
    if (!this.isBrowser) {
      this.resetSessionState();
      this.sessionInitialized = true;
      return;
    }
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', {}));
    } catch {
      // ignore logout errors but still clear local state
    } finally {
      this.clearSession();
    }
  }

  private async restoreSession(): Promise<void> {
    try {
      const session = await firstValueFrom(this.http.get<SessionResponseDto>('/api/auth/session'));
      this.applySession(session);
    } catch {
      this.resetSessionState();
      this.sessionInitialized = true;
    }
  }

  private applySession(session: SessionResponseDto): void {
    this.emailSignal.set(typeof session.email === 'string' ? session.email : null);
    this.roleSignal.set(this.normalizeRole(session.role));
    this.authenticatedSignal.set(true);
    this.sessionInitialized = true;
  }

  private clearSession(): void {
    this.resetSessionState();
    this.sessionInitialized = false;
  }

  private resetSessionState(): void {
    this.tokenSignal.set(null);
    this.roleSignal.set(null);
    this.emailSignal.set(null);
    this.authenticatedSignal.set(false);
  }

  private normalizeRole(role: unknown): string | null {
    if (typeof role !== 'string') {
      return null;
    }
    const value = role.trim().toUpperCase();
    return value.length > 0 ? value : null;
  }
}
