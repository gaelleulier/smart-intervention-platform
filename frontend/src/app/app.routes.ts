import { Routes } from '@angular/router';

import { UsersPageComponent } from './users/users-page.component';
import { InterventionsPageComponent } from './interventions/interventions-page.component';
import { LoginComponent } from './auth/login.component';
import { authGuard, loginRedirectGuard } from './auth/auth.guard';
import { DashboardPageComponent } from './dashboard/dashboard-page.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'login', component: LoginComponent, canActivate: [loginRedirectGuard] },
  { path: 'dashboard', component: DashboardPageComponent, canActivate: [authGuard] },
  { path: 'interventions', component: InterventionsPageComponent, canActivate: [authGuard] },
  { path: 'users', component: UsersPageComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'dashboard' }
];
