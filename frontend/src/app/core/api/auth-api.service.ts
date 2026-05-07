import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export type LoginPayload = {
  centerId: string;
  username: string;
  password: string;
};

export type LoginResponse = {
  username: string;
  fullName: string;
  userId: string;
  centerId: string;
  centerName: string;
  roles: string[];
};

export type LogoutResponse = {
  loggedOut: boolean;
};

export type RefreshResponse = {
  refreshed: boolean;
};

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  login(payload: LoginPayload): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/auth/login`, payload, {withCredentials: true});
  }

  logout(): Observable<LogoutResponse> {
    return this.http.post<LogoutResponse>(`${this.baseUrl}/auth/logout`, {}, {withCredentials: true});
  }

  me(): Observable<LoginResponse> {
    return this.http.get<LoginResponse>(`${this.baseUrl}/auth/me`, {withCredentials: true});
  }

  refresh(): Observable<RefreshResponse> {
    return this.http.post<RefreshResponse>(
      `${this.baseUrl}/auth/refresh`,
      {},
      {
        withCredentials: true,
        headers: new HttpHeaders({'x-skip-auth-refresh': '1'})
      }
    );
  }
}

