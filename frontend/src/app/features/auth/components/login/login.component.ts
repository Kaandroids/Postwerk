import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { AuthService } from '../../services/auth.service';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { PasswordInputComponent } from '../../../../shared/components/password-input/password-input.component';
import { CheckboxComponent } from '../../../../shared/components/checkbox/checkbox.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';

/** Login page with email/password form, remember-me option, and error handling. */
@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, InputComponent, PasswordInputComponent, CheckboxComponent, ButtonComponent, ErrorBannerComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private router = inject(Router);

  email = signal('');
  password = signal('');
  remember = signal(true);
  error = signal('');
  fieldErr = signal<Record<string, boolean>>({});
  loading = signal(false);

  // Shown when login is blocked because the email isn't verified yet.
  needsVerification = signal(false);
  resendDone = signal(false);

  async submit(): Promise<void> {
    this.error.set('');
    this.fieldErr.set({});
    this.needsVerification.set(false);
    this.resendDone.set(false);

    if (!this.email() || !this.password()) {
      this.error.set(this.i18n.t('login_error_required'));
      this.fieldErr.set({ email: !this.email(), password: !this.password() });
      return;
    }

    this.loading.set(true);
    const result = await this.auth.login(this.email(), this.password());
    this.loading.set(false);

    if (result.success) {
      this.router.navigate(['/dashboard']);
      return;
    }

    if (result.needsVerification) {
      this.needsVerification.set(true);
      this.error.set(this.i18n.t('login_error_unverified'));
      return;
    }

    this.error.set(result.error || this.i18n.t('login_error_password'));
    this.fieldErr.set({ email: true, password: true });
  }

  async resend(): Promise<void> {
    if (!this.email()) return;
    await this.auth.resendVerification(this.email(), this.i18n.lang());
    this.resendDone.set(true);
  }
}
