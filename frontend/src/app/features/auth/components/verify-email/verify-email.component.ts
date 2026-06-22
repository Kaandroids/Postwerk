import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { AuthService } from '../../services/auth.service';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';

type VerifyState = 'verifying' | 'success' | 'error';

/** Landing page hit from the verification email link: confirms the token then logs the user in. */
@Component({
  selector: 'app-verify-email',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, ButtonComponent, InputComponent, IconComponent],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss',
})
export class VerifyEmailComponent implements OnInit {
  protected i18n = inject(I18nService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  state = signal<VerifyState>('verifying');
  // Email for the "resend" path when verification failed (link expired/invalid).
  resendEmail = signal('');
  resendDone = signal(false);

  async ngOnInit(): Promise<void> {
    const token = this.route.snapshot.queryParamMap.get('token');
    this.resendEmail.set(this.route.snapshot.queryParamMap.get('email') ?? '');
    if (!token) {
      this.state.set('error');
      return;
    }
    const result = await this.auth.verifyEmail(token);
    if (result.success) {
      this.state.set('success');
      // Brief pause so the user sees the confirmation, then land in the app (already logged in).
      setTimeout(() => this.router.navigate(['/dashboard']), 1200);
    } else {
      this.state.set('error');
    }
  }

  async resend(): Promise<void> {
    if (!this.resendEmail() || !this.auth.isValidEmail(this.resendEmail())) return;
    await this.auth.resendVerification(this.resendEmail(), this.i18n.lang());
    this.resendDone.set(true);
  }
}
