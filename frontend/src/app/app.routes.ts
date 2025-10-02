import { Routes } from '@angular/router';

import { UsersPageComponent } from './users/users-page.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  { path: 'users', component: UsersPageComponent },
  { path: '**', redirectTo: 'users' }
];
