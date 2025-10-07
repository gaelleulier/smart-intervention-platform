import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import {
  CreateInterventionPayload,
  InterventionsPageResponseDto,
  ListInterventionsParams,
  TechnicianSummary,
  UpdateInterventionPayload,
  UpdateStatusPayload,
  InterventionResponseDto
} from './intervention.models';
import { UserResponseDto, UsersPageResponseDto } from '../users/user.models';

@Injectable({ providedIn: 'root' })
export class InterventionsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/interventions';

  list(params: ListInterventionsParams = {}): Observable<InterventionsPageResponseDto> {
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
    if (params.status) {
      httpParams = httpParams.set('status', params.status);
    }
    if (params.assignmentMode) {
      httpParams = httpParams.set('assignmentMode', params.assignmentMode);
    }
    if (params.technicianId != null) {
      httpParams = httpParams.set('technicianId', params.technicianId.toString());
    }
    if (params.plannedFrom) {
      httpParams = httpParams.set('plannedFrom', params.plannedFrom);
    }
    if (params.plannedTo) {
      httpParams = httpParams.set('plannedTo', params.plannedTo);
    }
    return this.http.get<InterventionsPageResponseDto>(this.baseUrl, { params: httpParams });
  }

  get(id: number): Observable<InterventionResponseDto> {
    return this.http.get<InterventionResponseDto>(`${this.baseUrl}/${id}`);
  }

  create(payload: CreateInterventionPayload): Observable<InterventionResponseDto> {
    return this.http.post<InterventionResponseDto>(this.baseUrl, payload);
  }

  update(id: number, payload: UpdateInterventionPayload): Observable<InterventionResponseDto> {
    return this.http.put<InterventionResponseDto>(`${this.baseUrl}/${id}`, payload);
  }

  updateStatus(id: number, payload: UpdateStatusPayload): Observable<InterventionResponseDto> {
    return this.http.post<InterventionResponseDto>(`${this.baseUrl}/${id}/status`, payload);
  }

  listTechnicians(): Observable<TechnicianSummary[]> {
    const params = new HttpParams().set('role', 'TECH').set('size', '100');
    return this.http.get<UsersPageResponseDto>(`/api/users`, { params }).pipe(
      map(response =>
        (response.content as UserResponseDto[]).map(user => ({
          id: user.id,
          fullName: user.fullName,
          email: user.email
        }))
      )
    );
  }
}
