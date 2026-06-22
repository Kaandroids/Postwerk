import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  tone?: 'danger' | 'accent';
}

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  readonly isOpen = signal(false);
  readonly options = signal<ConfirmOptions>({ title: '', message: '' });

  private resolveFn: ((value: boolean) => void) | null = null;

  confirm(options: ConfirmOptions): Promise<boolean> {
    this.options.set(options);
    this.isOpen.set(true);
    return new Promise<boolean>(resolve => {
      this.resolveFn = resolve;
    });
  }

  resolve(value: boolean): void {
    this.isOpen.set(false);
    this.resolveFn?.(value);
    this.resolveFn = null;
  }
}
