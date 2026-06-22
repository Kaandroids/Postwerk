import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { AuthService } from '../../services/auth.service';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { PasswordInputComponent } from '../../../../shared/components/password-input/password-input.component';
import { StrengthMeterComponent } from '../../../../shared/components/strength-meter/strength-meter.component';
import { CheckboxComponent } from '../../../../shared/components/checkbox/checkbox.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
/** Registration page with name, email, password, terms acceptance, and optional company/phone fields. */
@Component({
  selector: 'app-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, InputComponent, PasswordInputComponent, StrengthMeterComponent, CheckboxComponent, ButtonComponent, ErrorBannerComponent, IconComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  name = signal('');
  email = signal('');
  company = signal('');
  phone = signal('');
  password = signal('');
  confirm = signal('');
  terms = signal(false);
  marketing = signal(false);
  error = signal('');
  fieldErr = signal<Record<string, boolean>>({});
  loading = signal(false);

  // After a successful registration we show a "check your email" state instead of logging in.
  registeredEmail = signal<string | null>(null);
  resendDone = signal(false);

  async submit(): Promise<void> {
    this.error.set('');
    const errs: Record<string, boolean> = {};

    if (!this.name()) errs['name'] = true;
    if (!this.email()) errs['email'] = true;
    else if (!this.auth.isValidEmail(this.email())) errs['email'] = true;
    if (this.password().length < 8) errs['password'] = true;
    if (this.password() !== this.confirm()) errs['confirm'] = true;
    if (!this.terms()) errs['terms'] = true;

    if (Object.keys(errs).length) {
      this.fieldErr.set(errs);
      if (!this.terms() && !errs['email'] && !errs['password'] && !errs['confirm'] && !errs['name']) {
        this.error.set(this.i18n.t('register_error_terms'));
      } else if (errs['email']) {
        this.error.set(this.i18n.t('register_error_email'));
      } else if (errs['password']) {
        this.error.set(this.i18n.t('register_error_password'));
      } else if (errs['confirm']) {
        this.error.set(this.i18n.t('register_error_passwordMatch'));
      } else {
        this.error.set(this.i18n.t('register_error_required'));
      }
      return;
    }

    this.fieldErr.set({});
    this.loading.set(true);

    // When arriving from the /getstarted wizard, hand the session id to the backend so it can claim
    // the built automation into the new account server-side (survives the verification gap).
    let wizardSessionId: string | undefined;
    if (this.route.snapshot.queryParamMap.get('from') === 'wizard') {
      wizardSessionId = sessionStorage.getItem('wizard_session_id') ?? undefined;
    }

    const result = await this.auth.register({
      fullName: this.name(),
      email: this.email(),
      password: this.password(),
      company: this.company() || undefined,
      phone: this.phone() || undefined,
      marketingOptIn: this.marketing(),
      termsAccepted: true,
      lang: this.i18n.lang(),
      wizardSessionId,
    });
    this.loading.set(false);

    if (result.success) {
      // The wizard session was claimed during registration — drop the local handle.
      sessionStorage.removeItem('wizard_session_id');
      this.registeredEmail.set(result.email ?? this.email());
    } else {
      this.error.set(result.error || this.i18n.t('register_error_required'));
    }
  }

  async resend(): Promise<void> {
    const email = this.registeredEmail();
    if (!email) return;
    await this.auth.resendVerification(email, this.i18n.lang());
    this.resendDone.set(true);
  }
}
