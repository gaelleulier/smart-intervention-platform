import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Buffer } from 'node:buffer';

import { AuthService } from './auth.service';

const storageFactory = () => {
  const store = new Map<string, string>();
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => store.clear()
  } as Storage;
};

const createToken = (payload: Record<string, unknown>): string => {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'utf-8').toString('base64');
  const body = Buffer.from(JSON.stringify(payload), 'utf-8').toString('base64');
  return `${header}.${body}.signature`;
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let storage: Storage;

  beforeEach(() => {
    storage = storageFactory();
    Object.defineProperty(window, 'localStorage', {
      value: storage,
      writable: true
    });

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });

    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('stores token and role on login', async () => {
    const token = createToken({ sub: 'admin@sip.local', role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });

    const loginPromise = service.login('admin@sip.local', 'Admin123!');
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush({ token });

    await loginPromise;

    expect(service.token()).toBe(token);
    expect(service.role()).toBe('ADMIN');
    expect(storage.getItem('sip.jwt')).toBe(token);
  });

  it('logs out after changing password', async () => {
    const token = createToken({ sub: 'admin@sip.local', role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 });

    const loginPromise = service.login('admin@sip.local', 'Admin123!');
    const loginRequest = http.expectOne('/api/auth/login');
    loginRequest.flush({ token });
    await loginPromise;
    expect(service.isAuthenticated()).toBeTrue();

    const changePromise = service.changePassword('oldPass', 'NewPass123!');
    const request = http.expectOne('/api/auth/change-password');
    expect(request.request.method).toBe('POST');
    request.flush({});

    await changePromise;

    expect(service.token()).toBeNull();
    expect(service.role()).toBeNull();
    expect(storage.getItem('sip.jwt')).toBeNull();
  });
});
