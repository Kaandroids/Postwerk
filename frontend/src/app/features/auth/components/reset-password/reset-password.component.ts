import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { AuthService } from '../../services/auth.service';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';

/** Page component for requesting a password reset via email. */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, InputComponent, ButtonComponent, ErrorBannerComponent, IconComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private router = inject(Router);

  email = signal('');
  error = signal('');
  fieldErr = signal(false);
  loading = signal(false);
  success = signal<string | null>(null);

  async submit(): Promise<void> {
    this.error.set('');
    this.fieldErr.set(false);

    if (!this.email() || !this.auth.isValidEmail(this.email())) {
      this.error.set(this.i18n.t('register_error_email'));
      this.fieldErr.set(true);
      return;
    }

    this.loading.set(true);
    const result = await this.auth.resetPassword(this.email());
    this.loading.set(false);

    if (result.success) {
      this.success.set(this.email());
    } else {
      this.error.set(this.i18n.t('reset_error_unregistered'));
      this.fieldErr.set(true);
    }
  }

  resend(): void {
    this.success.set(null);
  }

  goToLogin(): void {
    this.router.navigate(['/auth/login']);
  }
}
