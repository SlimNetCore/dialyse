import {Injectable, signal} from '@angular/core';
import {firstValueFrom} from 'rxjs';
import {AuthApiService, LoginResponse} from '../api/auth-api.service';

const STORAGE_KEY = 'hemodialyse.auth.session';

type AuthSession = {
  username: string;
  fullName?: string;
  centerId: string;
  centerName: string;
  roles?: string[];
};

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  readonly username = signal<string | null>(null);
  readonly fullName = signal<string | null>(null);
  readonly centerId = signal<string | null>(null);
  readonly centerName = signal<string | null>(null);
  readonly roles = signal<string[]>([]);
  readonly isAuthenticated = signal(false);

  constructor(private readonly authApi: AuthApiService) {
    this.restoreFromStorage();
  }

  setSession(session: AuthSession): void {
    this.username.set(session.username);
    this.fullName.set(session.fullName ?? session.username);
    this.centerId.set(session.centerId);
    this.centerName.set(session.centerName);
    this.roles.set(session.roles ?? []);
    this.isAuthenticated.set(true);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  clearSession(): void {
    this.username.set(null);
    this.fullName.set(null);
    this.centerId.set(null);
    this.centerName.set(null);
    this.roles.set([]);
    this.isAuthenticated.set(false);
    localStorage.removeItem(STORAGE_KEY);
  }

  async initFromServer(options: { force?: boolean } = {}): Promise<boolean> {
    const shouldFetch = options.force === true || this.isAuthenticated();
    if (!shouldFetch) {
      return false;
    }

    try {
      const me = await firstValueFrom(this.authApi.me());
      this.setSession(this.mapLoginResponse(me));
      return true;
    } catch {
      this.clearSession();
      return false;
    }
  }

  hasRole(role: string): boolean {
    const roles = this.roles();
    if (roles.includes(role)) return true;
    // Accept both ADMIN and ROLE_ADMIN styles
    if (role.startsWith('ROLE_')) {
      return roles.includes(role.replace('ROLE_', ''));
    }
    return roles.includes(`ROLE_${role}`);
  }

  private restoreFromStorage(): void {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;

    try {
      const parsed = JSON.parse(raw) as AuthSession;
      this.setSession(parsed);
    } catch {
      this.clearSession();
    }
  }

  private mapLoginResponse(res: LoginResponse): AuthSession {
    return {
      username: res.username,
      fullName: res.fullName,
      centerId: res.centerId,
      centerName: res.centerName,
      roles: res.roles ?? []
    };
  }
}
