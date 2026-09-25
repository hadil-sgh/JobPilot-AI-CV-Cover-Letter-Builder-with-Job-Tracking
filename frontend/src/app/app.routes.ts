import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';
import { Login } from './features/auth/login';
import { Register } from './features/auth/register';
import { Dashboard } from './features/dashboard/dashboard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard], title: 'Sign in · JobPilot' },
  { path: 'register', component: Register, canActivate: [guestGuard], title: 'Create account · JobPilot' },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [{ path: '', component: Dashboard, title: 'Dashboard · JobPilot' }],
  },
  { path: '**', redirectTo: '' },
];
