import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AppUser {
  ID: string; USERNAME: string; EMAIL: string; FULL_NAME: string; ACTIVE: boolean; CREATED_AT: string;
  roles: Array<{ ID: string; CODE: string; NAME: string }>;
  centers: Array<{ ID: string; NAME: string }>;
}

export interface AppRole {
  ID: string; CODE: string; NAME: string; DESCRIPTION: string;
}

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = 'http://localhost:8080/api/v1';

  /* ─── Users ─── */
  listUsers(centerId?: string): Observable<AppUser[]> {
    let params = new HttpParams();
    if (centerId) params = params.set('centerId', centerId);
    return this.http.get<AppUser[]>(`${this.base}/users`, { params });
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

  /* ─── Centers (for assignment) ─── */
  listCenters(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/auth/centers`);
  }
}

