import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = async (_, state): Promise<boolean | UrlTree> => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.ensureSessionInitialized();
  if (auth.isAuthenticated()) {
    return true;
  }
  const returnUrl = state?.url && state.url !== '/login' ? state.url : undefined;
  return router.createUrlTree(['/login'], { queryParams: returnUrl ? { returnUrl } : undefined });
};

export const loginRedirectGuard: CanActivateFn = async (): Promise<boolean | UrlTree> => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.ensureSessionInitialized();
  if (auth.isAuthenticated()) {
    return router.createUrlTree(['/users']);
  }
  return true;
};
