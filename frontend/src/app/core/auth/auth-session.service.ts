import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'hemodialyse.auth.session';

type AuthSession = {
  token: string;
  username: string;
  centerId: string;
  centerName: string;
  roles?: string[];
};

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  readonly token = signal<string | null>(null);
  readonly username = signal<string | null>(null);
  readonly centerId = signal<string | null>(null);
  readonly centerName = signal<string | null>(null);
  readonly roles = signal<string[]>([]);
  readonly isAuthenticated = signal(false);

  constructor() {
    this.restore();
  }

  setSession(session: AuthSession): void {
    this.token.set(session.token);
    this.username.set(session.username);
    this.centerId.set(session.centerId);
    this.centerName.set(session.centerName);
    this.roles.set(session.roles ?? []);
    this.isAuthenticated.set(true);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  clearSession(): void {
    this.token.set(null);
    this.username.set(null);
    this.centerId.set(null);
    this.centerName.set(null);
    this.roles.set([]);
    this.isAuthenticated.set(false);
    localStorage.removeItem(STORAGE_KEY);
  }

  hasRole(role: string): boolean {
    return this.roles().includes(role);
  }

  private restore(): void {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;

    try {
      const parsed = JSON.parse(raw) as AuthSession;
      this.setSession(parsed);
    } catch {
      this.clearSession();
    }
  }
}
