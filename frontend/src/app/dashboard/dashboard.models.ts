export interface DashboardSummaryResponse {
  totalInterventions: number;
  scheduledCount: number;
  inProgressCount: number;
  completedCount: number;
  validatedCount: number;
  averageCompletionSeconds: number | null;
  validationRatio: number | null;
  lastRefreshedAt: string | null;
}

export interface StatusTrendPoint {
  date: string;
  status: string;
  count: number;
}

export interface TechnicianLoadResponse {
  technicianId: number;
  technicianName: string;
  technicianEmail: string;
  openCount: number;
  completedToday: number;
  averageCompletionSeconds: number | null;
  lastRefreshedAt: string | null;
}

export interface InterventionMapMarker {
  interventionId: number;
  latitude: number;
  longitude: number;
  status: string;
  technicianId: number | null;
  plannedAt: string | null;
  updatedAt: string;
}
