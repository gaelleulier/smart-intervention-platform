import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [provideZonelessChangeDetection()]
    });

    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('stores session info on login', async () => {
    const loginPromise = service.login('admin@sip.local', 'Admin123!');
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush({ token: 'jwt-token', email: 'admin@sip.local', role: 'ADMIN' });

    await loginPromise;

    expect(service.token()).toBe('jwt-token');
    expect(service.role()).toBe('ADMIN');
    expect(service.email()).toBe('admin@sip.local');
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('marks session as initialized after failed restore', async () => {
    const ensurePromise = service.ensureSessionInitialized();
    const request = http.expectOne('/api/auth/session');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
    await ensurePromise;
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('clears the session after changing password', async () => {
    const loginPromise = service.login('admin@sip.local', 'Admin123!');
    const loginRequest = http.expectOne('/api/auth/login');
    loginRequest.flush({ token: 'jwt-token', email: 'admin@sip.local', role: 'ADMIN' });
    await loginPromise;
    expect(service.isAuthenticated()).toBeTrue();

    const changePromise = service.changePassword('oldPass', 'NewPass123!');
    const changeRequest = http.expectOne('/api/auth/change-password');
    expect(changeRequest.request.method).toBe('POST');
    changeRequest.flush({});

    await Promise.resolve();
    const logoutRequest = http.expectOne('/api/auth/logout');
    expect(logoutRequest.request.method).toBe('POST');
    logoutRequest.flush({});

    await changePromise;

    expect(service.token()).toBeNull();
    expect(service.role()).toBeNull();
    expect(service.email()).toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
  });
});
