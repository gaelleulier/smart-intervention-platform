import { Routes } from '@angular/router';

import { UsersPageComponent } from './users/users-page.component';
import { InterventionsPageComponent } from './interventions/interventions-page.component';
import { LoginComponent } from './auth/login.component';
import { authGuard, loginRedirectGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'interventions' },
  { path: 'login', component: LoginComponent, canActivate: [loginRedirectGuard] },
  { path: 'interventions', component: InterventionsPageComponent, canActivate: [authGuard] },
  { path: 'users', component: UsersPageComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'interventions' }
];
