import { Routes } from '@angular/router';

export default [
  {
    path: '',
    loadComponent: () => import('./components/docs-shell/docs-shell.component').then(m => m.DocsShellComponent),
    children: [
      { path: '', loadComponent: () => import('./components/docs-home/docs-home.component').then(m => m.DocsHomeComponent) },
      { path: ':section/:topic', loadComponent: () => import('./components/docs-article/docs-article.component').then(m => m.DocsArticleComponent) },
    ],
  },
] satisfies Routes;
