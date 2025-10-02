export type UserRole = 'ADMIN' | 'DISPATCHER' | 'TECH';

export interface UserResponseDto {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
  createdAt: string;
}

export interface UsersPageResponseDto {
  content: UserResponseDto[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ListUsersParams {
  page?: number;
  size?: number;
  query?: string;
  role?: UserRole;
}

export interface CreateUserPayload {
  email: string;
  fullName: string;
  role?: UserRole;
}

export interface UpdateUserPayload {
  email: string;
  fullName: string;
  role: UserRole;
}
