import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  CreateUserPayload,
  ListUsersParams,
  UpdateUserPayload,
  UserResponseDto,
  UsersPageResponseDto
} from './user.models';

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/users';

  list(params: ListUsersParams = {}): Observable<UsersPageResponseDto> {
    let httpParams = new HttpParams();
    if (params.page != null) {
      httpParams = httpParams.set('page', params.page.toString());
    }
    if (params.size != null) {
      httpParams = httpParams.set('size', params.size.toString());
    }
    if (params.query) {
      httpParams = httpParams.set('query', params.query);
    }
    if (params.role) {
      httpParams = httpParams.set('role', params.role);
    }
    return this.http.get<UsersPageResponseDto>(this.baseUrl, { params: httpParams });
  }

  get(id: number): Observable<UserResponseDto> {
    return this.http.get<UserResponseDto>(`${this.baseUrl}/${id}`);
  }

  create(payload: CreateUserPayload): Observable<UserResponseDto> {
    return this.http.post<UserResponseDto>(this.baseUrl, payload);
  }

  update(id: number, payload: UpdateUserPayload): Observable<UserResponseDto> {
    return this.http.put<UserResponseDto>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
