import { Routes } from '@angular/router';

import { UsersPageComponent } from './users/users-page.component';
import { InterventionsPageComponent } from './interventions/interventions-page.component';
import { LoginComponent } from './auth/login.component';
import { authGuard, loginRedirectGuard } from './auth/auth.guard';
import { DashboardPageComponent } from './dashboard/dashboard-page.component';
import { AppShellComponent } from './layout/app-shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [loginRedirectGuard] },
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: DashboardPageComponent },
      { path: 'interventions', component: InterventionsPageComponent },
      { path: 'users', component: UsersPageComponent },
      { path: 'settings', redirectTo: 'users' }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
