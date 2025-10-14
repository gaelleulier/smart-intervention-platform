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

export interface AiInsightResponse {
  date: string;
  headline: string;
  trendDirection: 'UP' | 'DOWN' | 'FLAT' | string;
  trendPercentage: number;
  validationRate: number;
  validationAssessment: string;
  slaAssessment: string;
  highlights: string[];
}

export interface ForecastPointResponse {
  date: string;
  predictedCount: number;
}

export interface ForecastResponse {
  generatedAt: string;
  method: string;
  smoothingFactor: number;
  lastObservedCount: number;
  baselineAverage: number;
  points: ForecastPointResponse[];
}

export interface SmartAssignmentRequestPayload {
  title: string;
  description?: string;
  latitude?: number | null;
  longitude?: number | null;
  interventionId?: number | null;
}

export interface SmartAssignmentCandidate {
  technicianId: number;
  fullName: string;
  email: string;
  overallScore: number;
  workloadScore: number;
  distanceScore: number;
  skillScore: number;
  distanceKm: number | null;
  openAssignments: number;
  matchingHistory: number;
}

export interface SmartAssignmentResponsePayload {
  recommended: SmartAssignmentCandidate;
  alternatives: SmartAssignmentCandidate[];
  rationale: string;
  generatedAt: string;
}
