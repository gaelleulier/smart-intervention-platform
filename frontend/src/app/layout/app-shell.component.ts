import { CommonModule, DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ShellStateService } from './shell-state.service';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss'
})
export class AppShellComponent {
  protected readonly shellState = inject(ShellStateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  protected readonly demoBanner = computed(() => {
    const email = this.auth.email();
    const role = this.auth.role();
    if (!email || !role) {
      return null;
    }
    return {
      email,
      role,
      roleLabel: this.formatRole(role)
    };
  });

  constructor() {
    const documentRef = inject(DOCUMENT, { optional: true }) as Document | undefined;
    effect(() => {
      if (!documentRef) {
        return;
      }
      if (this.shellState.collapsed()) {
        documentRef.body.classList.add('app-shell-collapsed');
      } else {
        documentRef.body.classList.remove('app-shell-collapsed');
      }
    });

    this.destroyRef.onDestroy(() => {
      documentRef?.body.classList.remove('app-shell-collapsed');
    });
  }

  protected toggleSidebar(): void {
    this.shellState.toggle();
  }

  private formatRole(role: string): string {
    const normalized = role.trim().toUpperCase();
    switch (normalized) {
      case 'DISPATCHER':
        return 'Dispatcher';
      case 'TECH':
      case 'TECHNICIAN':
        return 'Technician';
      case 'ADMIN':
        return 'Admin';
      default:
        return normalized.charAt(0) + normalized.slice(1).toLowerCase();
    }
  }
}
