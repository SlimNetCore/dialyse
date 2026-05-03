import {computed, Injectable, signal} from '@angular/core';
import {firstValueFrom} from 'rxjs';
import {AuthApiService, LoginResponse} from '../api/auth-api.service';

type AuthSession = {
  username: string;
  fullName?: string;
  centerId: string;
  centerName: string;
  roles?: string[];
};

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private initInFlight: Promise<boolean> | null = null;
  private lastServerSyncAt = 0;
  private readonly serverSyncGraceMs = 10_000;
  private readonly serverSyncPending = signal(0);

  readonly username = signal<string | null>(null);
  readonly fullName = signal<string | null>(null);
  readonly centerId = signal<string | null>(null);
  readonly centerName = signal<string | null>(null);
  readonly roles = signal<string[]>([]);
  readonly isAuthenticated = signal(false);
  readonly isServerSyncing = computed(() => this.serverSyncPending() > 0);

  constructor(private readonly authApi: AuthApiService) {
  }

  setSession(session: AuthSession): void {
    this.username.set(session.username);
    this.fullName.set(session.fullName ?? session.username);
    this.centerId.set(session.centerId);
    this.centerName.set(session.centerName);
    this.roles.set(session.roles ?? []);
    this.isAuthenticated.set(true);
  }

  clearSession(): void {
    this.username.set(null);
    this.fullName.set(null);
    this.centerId.set(null);
    this.centerName.set(null);
    this.roles.set([]);
    this.isAuthenticated.set(false);
    this.lastServerSyncAt = 0;
  }

  async initFromServer(options: { force?: boolean } = {}): Promise<boolean> {
    const shouldFetch = options.force === true || this.isAuthenticated();
    if (!shouldFetch) {
      return false;
    }

    // Avoid duplicate /me calls during app bootstrap (initializer + guard).
    if (!options.force && (Date.now() - this.lastServerSyncAt) < this.serverSyncGraceMs) {
      return true;
    }

    if (this.initInFlight) {
      return this.initInFlight;
    }

    this.initInFlight = (async () => {
      this.serverSyncPending.update((v) => v + 1);
      try {
        const me = await firstValueFrom(this.authApi.me());
        this.setSession(this.mapLoginResponse(me));
        this.lastServerSyncAt = Date.now();
        return true;
      } catch {
        this.clearSession();
        return false;
      } finally {
        this.serverSyncPending.update((v) => Math.max(0, v - 1));
        this.initInFlight = null;
      }
    })();

    return this.initInFlight;
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
