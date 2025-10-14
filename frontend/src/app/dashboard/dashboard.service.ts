import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  DashboardSummaryResponse,
  InterventionMapMarker,
  StatusTrendPoint,
  TechnicianLoadResponse,
  AiInsightResponse,
  ForecastResponse,
  SmartAssignmentRequestPayload,
  SmartAssignmentResponsePayload
} from './dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/dashboard';

  getSummary(date?: string): Observable<DashboardSummaryResponse> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<DashboardSummaryResponse>(`${this.baseUrl}/summary`, { params });
  }

  getStatusTrends(from?: string, to?: string): Observable<StatusTrendPoint[]> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<StatusTrendPoint[]>(`${this.baseUrl}/status-trends`, { params });
  }

  getTechnicianLoad(): Observable<TechnicianLoadResponse[]> {
    return this.http.get<TechnicianLoadResponse[]>(`${this.baseUrl}/technician-load`);
  }

  getMapMarkers(status?: string[], limit?: number): Observable<InterventionMapMarker[]> {
    let params = new HttpParams();
    if (status && status.length > 0) {
      status.forEach(value => {
        params = params.append('status', value);
      });
    }
    if (limit != null) {
      params = params.set('limit', String(limit));
    }
    return this.http.get<InterventionMapMarker[]>(`${this.baseUrl}/map`, { params });
  }

  getAiInsights(date?: string): Observable<AiInsightResponse> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<AiInsightResponse>(`${this.baseUrl}/ai/insights`, { params });
  }

  getForecast(date?: string): Observable<ForecastResponse> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<ForecastResponse>(`${this.baseUrl}/ai/forecast`, { params });
  }

  recommendTechnician(payload: SmartAssignmentRequestPayload): Observable<SmartAssignmentResponsePayload> {
    const body = {
      ...payload,
      latitude: payload.latitude ?? null,
      longitude: payload.longitude ?? null,
      interventionId: payload.interventionId ?? null
    };
    return this.http.post<SmartAssignmentResponsePayload>(`/api/interventions/recommendation`, body);
  }
}
