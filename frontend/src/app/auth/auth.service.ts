import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

interface LoginResponseDto {
  token: string;
}

declare const Buffer:
  | undefined
  | {
      from(input: string, encoding: string): { toString(encoding: string): string };
    };

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenStorageKey = 'sip.jwt';
  private readonly tokenSignal = signal<string | null>(null);
  private readonly roleSignal = signal<string | null>(null);
  private readonly expirySignal = signal<number | null>(null);
  private readonly http = inject(HttpClient);
  private logoutTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    if (typeof window !== 'undefined' && window.localStorage) {
      const storedToken = window.localStorage.getItem(this.tokenStorageKey);
      if (storedToken) {
        this.persistToken(storedToken);
      }
    }
  }

  token(): string | null {
    if (this.isExpired()) {
      this.logout();
      return null;
    }
    return this.tokenSignal();
  }

  role(): string | null {
    return this.roleSignal();
  }

  isAuthenticated(): boolean {
    return !!this.token() && !this.isExpired();
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<LoginResponseDto>('/api/auth/login', { email, password })
    );
    this.persistToken(response.token);
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>('/api/auth/change-password', {
        currentPassword,
        newPassword
      })
    );
    this.logout();
  }

  logout(): void {
    if (this.logoutTimer) {
      clearTimeout(this.logoutTimer);
      this.logoutTimer = null;
    }
    this.tokenSignal.set(null);
    this.roleSignal.set(null);
    this.expirySignal.set(null);
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.removeItem(this.tokenStorageKey);
    }
  }

  private persistToken(token: string): void {
    this.tokenSignal.set(token);
    const decoded = this.decode(token);
    const role = decoded ? decoded['role'] : null;
    const expValue = decoded ? decoded['exp'] : null;
    const exp = typeof expValue === 'number' ? expValue * 1000 : null;
    this.roleSignal.set(typeof role === 'string' ? role : null);
    this.expirySignal.set(exp);
    this.scheduleAutoLogout(exp);
    if (typeof window !== 'undefined' && window.localStorage) {
      window.localStorage.setItem(this.tokenStorageKey, token);
    }
  }

  private decode(token: string): Record<string, unknown> | null {
    try {
      const [, payload] = token.split('.');
      if (!payload) {
        return null;
      }
      let json: string;
      if (typeof atob === 'function') {
        json = atob(payload);
      } else if (typeof Buffer !== 'undefined') {
        json = Buffer.from(payload, 'base64').toString('utf-8');
      } else {
        return null;
      }
      return JSON.parse(json) as Record<string, unknown>;
    } catch (error) {
      return null;
    }
  }

  private isExpired(): boolean {
    const expiry = this.expirySignal();
    return typeof expiry === 'number' && Date.now() >= expiry;
  }

  private scheduleAutoLogout(expiry: number | null): void {
    if (this.logoutTimer) {
      clearTimeout(this.logoutTimer);
      this.logoutTimer = null;
    }
    if (expiry && expiry > Date.now()) {
      const delay = expiry - Date.now();
      this.logoutTimer = setTimeout(() => this.logout(), delay);
    }
  }
}
