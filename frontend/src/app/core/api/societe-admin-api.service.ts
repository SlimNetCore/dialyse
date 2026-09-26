import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export type AdminAccount = {
  userId: string;
  username: string;
  fullName: string | null;
  email: string | null;
  active: boolean;
  centerId: string;
  centerName: string;
};

export type CreateAdminAccount = {
  centerId: string;
  username: string;
  fullName?: string;
  email?: string;
  password: string;
};

/** Administrateurs des centres d'une société : créés et gérés par le propriétaire de l'application. */
@Injectable({providedIn: 'root'})
export class SocieteAdminApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  list(societeId: string): Observable<AdminAccount[]> {
    return this.http.get<AdminAccount[]>(this.url(societeId), {withCredentials: true});
  }

  create(societeId: string, payload: CreateAdminAccount): Observable<AdminAccount> {
    return this.http.post<AdminAccount>(this.url(societeId), payload, {withCredentials: true});
  }

  setActive(societeId: string, userId: string, active: boolean): Observable<void> {
    return this.http.post<void>(`${this.url(societeId)}/${userId}/${active ? 'activer' : 'desactiver'}`, {},
      {withCredentials: true});
  }

  resetPassword(societeId: string, userId: string, password: string): Observable<void> {
    return this.http.put<void>(`${this.url(societeId)}/${userId}/password`, {password}, {withCredentials: true});
  }

  private url(societeId: string): string {
    return `${this.baseUrl}/societes/${societeId}/admin-accounts`;
  }
}
