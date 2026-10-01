import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';
import { Login } from './features/auth/login';
import { Register } from './features/auth/register';
import { Dashboard } from './features/dashboard/dashboard';
import { EditorPage } from './features/editor/editor-page';
import { AnalyzePage } from './features/jobs/analyze-page';
import { ProfilePage } from './features/profile/profile-page';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', component: Login, canActivate: [guestGuard], title: 'Sign in · JobPilot' },
  { path: 'register', component: Register, canActivate: [guestGuard], title: 'Create account · JobPilot' },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', component: Dashboard, title: 'Dashboard · JobPilot' },
      { path: 'profile', component: ProfilePage, title: 'Profile · JobPilot' },
      { path: 'jobs/analyze', component: AnalyzePage, title: 'Analyze a job · JobPilot' },
      { path: 'applications/:id', component: EditorPage, title: 'Editor · JobPilot' },
    ],
  },
  { path: '**', redirectTo: '' },
];
