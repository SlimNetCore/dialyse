import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

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

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api/v1';

  login(payload: LoginPayload): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/auth/login`, payload, {withCredentials: true});
  }

  logout(): Observable<LogoutResponse> {
    return this.http.post<LogoutResponse>(`${this.baseUrl}/auth/logout`, {}, {withCredentials: true});
  }

  me(): Observable<LoginResponse> {
    return this.http.get<LoginResponse>(`${this.baseUrl}/auth/me`, {withCredentials: true});
  }
}

