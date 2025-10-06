import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_, state): boolean | UrlTree => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  const returnUrl = state?.url && state.url !== '/login' ? state.url : undefined;
  return router.createUrlTree(['/login'], { queryParams: returnUrl ? { returnUrl } : undefined });
};

export const loginRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return router.createUrlTree(['/users']);
  }
  return true;
};
