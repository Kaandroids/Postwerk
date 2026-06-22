import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { AuthService } from '../../services/auth.service';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { PasswordInputComponent } from '../../../../shared/components/password-input/password-input.component';
import { StrengthMeterComponent } from '../../../../shared/components/strength-meter/strength-meter.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';

/**
 * Dual-mode page:
 * - no {@code token} query param → request a reset link (sends email).
 * - {@code token} present (from the reset email) → set a new password.
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, InputComponent, PasswordInputComponent, StrengthMeterComponent, ButtonComponent, ErrorBannerComponent, IconComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent implements OnInit {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  // Reset token from the email link; null => request mode.
  token = signal<string | null>(null);

  // --- request mode ---
  email = signal('');
  error = signal('');
  fieldErr = signal(false);
  loading = signal(false);
  success = signal<string | null>(null);

  // --- set-new-password mode ---
  password = signal('');
  confirm = signal('');
  pwFieldErr = signal<Record<string, boolean>>({});
  resetDone = signal(false);

  ngOnInit(): void {
    this.token.set(this.route.snapshot.queryParamMap.get('token'));
  }

  async submit(): Promise<void> {
    this.error.set('');
    this.fieldErr.set(false);

    if (!this.email() || !this.auth.isValidEmail(this.email())) {
      this.error.set(this.i18n.t('register_error_email'));
      this.fieldErr.set(true);
      return;
    }

    this.loading.set(true);
    const result = await this.auth.resetPassword(this.email(), this.i18n.lang());
    this.loading.set(false);

    if (result.success) {
      this.success.set(this.email());
    } else {
      this.error.set(result.error || this.i18n.t('reset_error_unregistered'));
      this.fieldErr.set(true);
    }
  }

  async submitNewPassword(): Promise<void> {
    this.error.set('');
    const errs: Record<string, boolean> = {};
    if (this.password().length < 8) errs['password'] = true;
    if (this.password() !== this.confirm()) errs['confirm'] = true;

    if (Object.keys(errs).length) {
      this.pwFieldErr.set(errs);
      this.error.set(this.i18n.t(errs['password'] ? 'register_error_password' : 'register_error_passwordMatch'));
      return;
    }
    this.pwFieldErr.set({});

    this.loading.set(true);
    const result = await this.auth.confirmPasswordReset(this.token()!, this.password());
    this.loading.set(false);

    if (result.success) {
      this.resetDone.set(true);
    } else {
      this.error.set(result.error || this.i18n.t('reset_confirm_error'));
    }
  }

  resend(): void {
    this.success.set(null);
  }

  goToLogin(): void {
    this.router.navigate(['/auth/login']);
  }
}
