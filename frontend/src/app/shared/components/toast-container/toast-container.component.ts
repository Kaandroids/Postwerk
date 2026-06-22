import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService, ToastSeverity } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/** App-wide stack of auto-dismissing toasts. Mounted once at the app root (see app.html). */
@Component({
  selector: 'app-toast-container',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="toast-wrap" data-testid="toast-container">
      @for (t of toasts.toasts(); track t.id) {
        <div class="toast" [attr.data-sev]="t.severity" data-testid="toast">
          <span class="toast-icon"><app-icon [name]="icon(t.severity)" /></span>
          <div class="toast-body">
            @if (t.title) { <div class="toast-title">{{ t.title }}</div> }
            <div class="toast-msg">{{ t.message }}</div>
          </div>
          <button class="toast-close" (click)="toasts.dismiss(t.id)" aria-label="Close">
            <app-icon name="x" />
          </button>
        </div>
      }
    </div>
  `,
  styleUrl: './toast-container.component.scss',
})
export class ToastContainerComponent {
  protected toasts = inject(ToastService);

  icon(severity: ToastSeverity): string {
    switch (severity) {
      case 'success': return 'checkCircle';
      case 'warning': return 'alert';
      case 'danger': return 'alertTriangle';
      default: return 'info';
    }
  }
}
