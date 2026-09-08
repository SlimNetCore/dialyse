import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export interface AppUser {
  id: string;
  username: string;
  email: string;
  full_name: string;
  active: boolean;
  created_at: string;
  roles: Array<{ id: string; code: string; name: string }>;
  centers: Array<{ id: string; name: string }>;
}

export interface AppRole {
  id: string;
  code: string;
  name: string;
  description: string;
}

export interface PagedResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface ListQuery {
  page: number;
  size: number;
  search?: string;
  filters?: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /* ─── Users ─── */
  listUsers(centerId?: string): Observable<AppUser[]> {
    let params = new HttpParams();
    if (centerId) params = params.set('centerId', centerId);
    return this.http.get<AppUser[]>(`${this.base}/users`, { params });
  }

  listUsersPaged(query: ListQuery, centerId?: string): Observable<PagedResponse<AppUser>> {
    const filters = query.filters ?? {};
    const active = filters['active'];
    const body = {
      centerId: centerId || null,
      page: query.page,
      size: query.size,
      search: query.search?.trim() || null,
      username: filters['username']?.trim() || null,
      fullName: filters['fullName']?.trim() || null,
      email: filters['email']?.trim() || null,
      roles: filters['roles']?.trim() || null,
      centers: filters['centers']?.trim() || null,
      active: active === 'true' ? true : active === 'false' ? false : null,
    };
    return this.http.post<PagedResponse<AppUser>>(`${this.base}/users/search`, body);
  }

  getUser(id: string): Observable<AppUser> {
    return this.http.get<AppUser>(`${this.base}/users/${id}`);
  }

  createUser(payload: { username: string; password: string; email: string; fullName: string; active: boolean; roleIds: string[]; centerIds: string[] }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.base}/users`, payload);
  }

  updateUser(id: string, payload: { email: string; fullName: string; active: boolean; password?: string; roleIds: string[]; centerIds: string[] }): Observable<{ id: string }> {
    return this.http.put<{ id: string }>(`${this.base}/users/${id}`, payload);
  }

  deleteUser(id: string): Observable<{ deleted: boolean }> {
    return this.http.delete<{ deleted: boolean }>(`${this.base}/users/${id}`);
  }

  /* ─── Roles ─── */
  listRoles(): Observable<AppRole[]> {
    return this.http.get<AppRole[]>(`${this.base}/roles`);
  }

  listRolesPaged(query: ListQuery): Observable<PagedResponse<AppRole>> {
    const filters = query.filters ?? {};
    const body = {
      page: query.page,
      size: query.size,
      search: query.search?.trim() || null,
      code: filters['code']?.trim() || null,
      name: filters['name']?.trim() || null,
      description: filters['description']?.trim() || null,
    };
    return this.http.post<PagedResponse<AppRole>>(`${this.base}/roles/search`, body);
  }

  getRole(id: string): Observable<AppRole> {
    return this.http.get<AppRole>(`${this.base}/roles/${id}`);
  }

  createRole(payload: { code: string; name: string; description: string }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.base}/roles`, payload);
  }

  updateRole(id: string, payload: { code: string; name: string; description: string }): Observable<{ id: string }> {
    return this.http.put<{ id: string }>(`${this.base}/roles/${id}`, payload);
  }

  deleteRole(id: string): Observable<{ deleted: boolean }> {
    return this.http.delete<{ deleted: boolean }>(`${this.base}/roles/${id}`);
  }
}

