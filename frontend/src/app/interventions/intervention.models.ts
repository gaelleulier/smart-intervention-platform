export type InterventionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'VALIDATED';
export type InterventionAssignmentMode = 'AUTO' | 'MANUAL';

export interface TechnicianSummary {
  id: number;
  fullName: string;
  email: string;
}

export interface InterventionResponseDto {
  id: number;
  reference: string;
  title: string;
  description: string | null;
  status: InterventionStatus;
  assignmentMode: InterventionAssignmentMode;
  plannedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  validatedAt: string | null;
  createdAt: string;
  updatedAt: string;
  technician: TechnicianSummary | null;
}

export interface InterventionsPageResponseDto {
  content: InterventionResponseDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListInterventionsParams {
  page?: number;
  size?: number;
  query?: string;
  status?: InterventionStatus;
  assignmentMode?: InterventionAssignmentMode;
  technicianId?: number;
  plannedFrom?: string;
  plannedTo?: string;
}

export interface CreateInterventionPayload {
  reference: string;
  title: string;
  description?: string | null;
  plannedAt: string;
  assignmentMode: InterventionAssignmentMode;
  technicianId?: number | null;
}

export interface UpdateInterventionPayload {
  title: string;
  description?: string | null;
  plannedAt: string;
  assignmentMode: InterventionAssignmentMode;
  technicianId?: number | null;
}

export interface UpdateStatusPayload {
  status: InterventionStatus;
}
