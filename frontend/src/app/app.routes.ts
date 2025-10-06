import { Routes } from '@angular/router';

import { UsersPageComponent } from './users/users-page.component';
import { LoginComponent } from './auth/login.component';
import { authGuard, loginRedirectGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  { path: 'login', component: LoginComponent, canActivate: [loginRedirectGuard] },
  { path: 'users', component: UsersPageComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'users' }
];
