import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ShellStateService {
  private readonly _collapsed = signal(false);

  readonly collapsed = this._collapsed.asReadonly();

  toggle(): void {
    this._collapsed.update(value => !value);
  }

  setCollapsed(value: boolean): void {
    this._collapsed.set(value);
  }
}
