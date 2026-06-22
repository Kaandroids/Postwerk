import { Routes } from '@angular/router';
import { AuthLayoutComponent } from './components/auth-layout/auth-layout.component';

export default [
  {
    path: '',
    component: AuthLayoutComponent,
    children: [
      { path: '', redirectTo: 'login', pathMatch: 'full' as const },
      { path: 'login', loadComponent: () => import('./components/login/login.component').then(m => m.LoginComponent) },
      { path: 'register', loadComponent: () => import('./components/register/register.component').then(m => m.RegisterComponent) },
      { path: 'reset-password', loadComponent: () => import('./components/reset-password/reset-password.component').then(m => m.ResetPasswordComponent) },
      { path: 'verify-email', loadComponent: () => import('./components/verify-email/verify-email.component').then(m => m.VerifyEmailComponent) },
    ],
  },
] satisfies Routes;
