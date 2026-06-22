import { Routes } from '@angular/router';

export default [
  {
    path: ':kind',
    loadComponent: () => import('./components/legal-page/legal-page.component').then(m => m.LegalPageComponent),
  },
] satisfies Routes;
