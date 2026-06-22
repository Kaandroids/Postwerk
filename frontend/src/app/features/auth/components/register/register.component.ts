import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { AuthService } from '../../services/auth.service';
import { WizardService } from '../../../getstarted/services/wizard.service';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { PasswordInputComponent } from '../../../../shared/components/password-input/password-input.component';
import { StrengthMeterComponent } from '../../../../shared/components/strength-meter/strength-meter.component';
import { CheckboxComponent } from '../../../../shared/components/checkbox/checkbox.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
/** Registration page with name, email, password, terms acceptance, and optional company/phone fields. */
@Component({
  selector: 'app-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, InputComponent, PasswordInputComponent, StrengthMeterComponent, CheckboxComponent, ButtonComponent, ErrorBannerComponent],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private wizardService = inject(WizardService);

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
    const result = await this.auth.register({
      fullName: this.name(),
      email: this.email(),
      password: this.password(),
      company: this.company() || undefined,
      phone: this.phone() || undefined,
      marketingOptIn: this.marketing(),
      termsAccepted: true,
    });
    this.loading.set(false);

    if (result.success) {
      // Check for wizard claim
      const from = this.route.snapshot.queryParamMap.get('from');
      if (from === 'wizard') {
        const wizardSessionId = sessionStorage.getItem('wizard_session_id');
        if (wizardSessionId) {
          this.wizardService.claimSession(wizardSessionId).subscribe({
            next: (res) => {
              sessionStorage.removeItem('wizard_session_id');
              this.router.navigate(['/dashboard/automations', res.automationId, 'edit']);
            },
            error: () => {
              sessionStorage.removeItem('wizard_session_id');
              this.router.navigate(['/dashboard']);
            },
          });
          return;
        }
      }
      this.router.navigate(['/dashboard']);
    } else {
      this.error.set(result.error || this.i18n.t('register_error_required'));
    }
  }
}
