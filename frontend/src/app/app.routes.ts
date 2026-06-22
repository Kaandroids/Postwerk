import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'landing', pathMatch: 'full' },
  { path: 'landing', loadChildren: () => import('./features/landing/landing.routes') },
  { path: 'auth', loadChildren: () => import('./features/auth/auth.routes'), canActivate: [guestGuard] },
  { path: 'legal', loadChildren: () => import('./features/legal/legal.routes') },
  { path: 'docs', loadChildren: () => import('./features/docs/docs.routes') },
  { path: 'getstarted', loadChildren: () => import('./features/getstarted/getstarted.routes') },
  { path: 'dashboard', loadChildren: () => import('./features/dashboard/dashboard.routes'), canActivate: [authGuard] },
  { path: '**', redirectTo: 'landing' },
];
