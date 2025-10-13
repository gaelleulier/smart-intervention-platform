import { CommonModule, DOCUMENT } from '@angular/common';
import { Component, DestroyRef, effect, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ShellStateService } from './shell-state.service';

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
}
