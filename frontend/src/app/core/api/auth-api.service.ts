import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

/** Entrée d'un annuaire de connexion : identifiant et nom uniquement. */
export type DirectoryItem = {
  id: string;
  name: string;
};

export type SessionScope = 'CENTRE' | 'SOCIETE' | 'PLATEFORME';

export type LoginPayload = {
  /** Société choisie à l'étape 1 ; le serveur vérifie que le centre lui appartient. */
  societeId?: string;
  /** Absent pour une connexion sans centre (direction, propriétaire). */
  centerId?: string;
  username: string;
  password: string;
  /** Code de double authentification (TOTP ou code de secours), requis si le compte l'a activée. */
  otp?: string;
};

export type MfaEnrollment = {
  secret: string;
  otpauthUri: string;
};

export type LoginResponse = {
  username: string;
  fullName: string;
  userId: string;
  centerId: string | null;
  centerName: string | null;
  roles: string[];
  societeId?: string | null;
  societeName?: string | null;
  /** CENTRE, SOCIETE (direction) ou PLATEFORME (propriétaire). */
  scope?: SessionScope;
  /** Vrai tant que l'utilisateur n'a pas remplacé son mot de passe temporaire. */
  mustChangePassword?: boolean;
};

/** État de l'installation : `required` tant qu'aucun compte propriétaire n'existe. */
export type SetupStatus = {
  required: boolean;
  tokenRequired: boolean;
};

export type CreateOwnerPayload = {
  username: string;
  fullName?: string;
  email?: string;
  password: string;
  setupToken?: string;
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

  /** Double authentification de l'utilisateur connecté. */
  mfaStatus(): Observable<{ enabled: boolean }> {
    return this.http.get<{ enabled: boolean }>(`${this.baseUrl}/auth/mfa/status`, {withCredentials: true});
  }

  mfaEnroll(): Observable<MfaEnrollment> {
    return this.http.post<MfaEnrollment>(`${this.baseUrl}/auth/mfa/enroll`, {}, {withCredentials: true});
  }

  /** Confirme l'inscription avec un premier code ; renvoie les codes de secours (montrés une seule fois). */
  mfaConfirm(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>(`${this.baseUrl}/auth/mfa/confirm`, {code},
      {withCredentials: true});
  }

  mfaDisable(code: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/auth/mfa/disable`, {code}, {withCredentials: true});
  }

  /** Étape 1 de la connexion : sociétés actives (endpoint public, id et nom seulement). */
  getLoginSocietes(): Observable<DirectoryItem[]> {
    return this.http.get<DirectoryItem[]>(`${this.baseUrl}/auth/societes`);
  }

  /** Étape 2 de la connexion : centres actifs de la société choisie. */
  getLoginCentres(societeId: string): Observable<DirectoryItem[]> {
    return this.http.get<DirectoryItem[]>(`${this.baseUrl}/auth/societes/${societeId}/centres`);
  }

  /** Centres accessibles à l'utilisateur connecté (SUPERADMIN : tous ; sinon ceux de sa société). */
  getAccessibleCenters(): Observable<DirectoryItem[]> {
    return this.http.get<DirectoryItem[]>(`${this.baseUrl}/auth/centres`, {withCredentials: true});
  }

  getSetupStatus(): Observable<SetupStatus> {
    // Public : un refus ne doit jamais déclencher le rafraîchissement de session ni une redirection.
    return this.http.get<SetupStatus>(`${this.baseUrl}/auth/setup/status`, {
      headers: new HttpHeaders({'x-skip-auth-refresh': '1'})
    });
  }

  /** Installation initiale : crée le compte propriétaire (refusé dès qu'il en existe un). */
  createOwner(payload: CreateOwnerPayload): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/auth/setup/superadmin`, payload);
  }

  logout(): Observable<LogoutResponse> {
    return this.http.post<LogoutResponse>(`${this.baseUrl}/auth/logout`, {}, {withCredentials: true});
  }

  /** Remplace le mot de passe de l'utilisateur connecté (obligatoire tant que le mot de passe est temporaire). */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/auth/change-password`, {currentPassword, newPassword},
      {withCredentials: true});
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

