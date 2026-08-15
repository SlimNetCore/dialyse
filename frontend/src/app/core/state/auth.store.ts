import {computed, inject} from '@angular/core';
import {patchState, signalStore, withComputed, withMethods, withState} from '@ngrx/signals';
import {firstValueFrom} from 'rxjs';
import {AuthApiService, LoginResponse} from '../api/auth-api.service';
import {withDevtools} from '@angular-architects/ngrx-toolkit';

export type AuthSession = {
  username: string;
  fullName?: string;
  centerId: string;
  centerName: string;
  roles?: string[];
};

type AuthState = {
  username: string | null;
  fullName: string | null;
  centerId: string | null;
  centerName: string | null;
  roles: string[];
  isAuthenticated: boolean;
  serverSyncPending: number;
};

const INITIAL_STATE: AuthState = {
  username: null,
  fullName: null,
  centerId: null,
  centerName: null,
  roles: [],
  isAuthenticated: false,
  serverSyncPending: 0
};

export const AuthStore = signalStore(
  {providedIn: 'root'},
  withState(INITIAL_STATE),
  withDevtools('AuthStore'),
  withComputed((store) => ({
    isServerSyncing: computed(() => store.serverSyncPending() > 0)
  })),
  withMethods((store) => {
    const authApi = inject(AuthApiService);
    let initInFlight: Promise<boolean> | null = null;
    let lastServerSyncAt = 0;
    const serverSyncGraceMs = 10_000;

    return {
      /**
       * Set authenticated user session
       */
      setSession(session: AuthSession): void {
        patchState(store, {
          username: session.username,
          fullName: session.fullName ?? session.username,
          centerId: session.centerId,
          centerName: session.centerName,
          roles: session.roles ?? [],
          isAuthenticated: true
        });
      },

      /**
       * Clear authenticated session
       */
      clearSession(): void {
        patchState(store, {
          username: null,
          fullName: null,
          centerId: null,
          centerName: null,
          roles: [],
          isAuthenticated: false,
          serverSyncPending: 0
        });
        lastServerSyncAt = 0;
      },

      /**
       * Initialize session from server /me endpoint
       * Includes deduplication logic to avoid duplicate calls during bootstrap
       */
      async initFromServer(options: { force?: boolean } = {}): Promise<boolean> {
        const shouldFetch = options.force === true || store.isAuthenticated();
        if (!shouldFetch) {
          return false;
        }

        // Avoid duplicate /me calls during app bootstrap (initializer + guard)
        if (!options.force && (Date.now() - lastServerSyncAt) < serverSyncGraceMs) {
          return true;
        }

        if (initInFlight) {
          return initInFlight;
        }

        initInFlight = (async () => {
          patchState(store, {serverSyncPending: store.serverSyncPending() + 1});
          try {
            const me = await firstValueFrom(authApi.me());
            const session = mapLoginResponseToSession(me);
            this.setSession(session);
            lastServerSyncAt = Date.now();
            return true;
          } catch (error: unknown) {
            const status = typeof error === 'object' && error !== null
              ? (error as { status?: number }).status
              : undefined;

            // Session expirée/invalide côté serveur.
            if (status === 401 || status === 403) {
              this.clearSession();
              return false;
            }

            // Coupure réseau / backend indisponible : conserver la session locale.
            return store.isAuthenticated();
          } finally {
            patchState(store, {serverSyncPending: Math.max(0, store.serverSyncPending() - 1)});
            initInFlight = null;
          }
        })();

        return initInFlight;
      },

      /**
       * Check if user has role (supports both 'ADMIN' and 'ROLE_ADMIN' styles)
       */
      hasRole(role: string): boolean {
        const roles = store.roles();
        if (roles.includes(role)) return true;
        // Accept both ADMIN and ROLE_ADMIN styles
        if (role.startsWith('ROLE_')) {
          return roles.includes(role.replace('ROLE_', ''));
        }
        return roles.includes(`ROLE_${role}`);
      }
    };
  })
);

/**
 * Map API LoginResponse to AuthSession
 */
function mapLoginResponseToSession(res: LoginResponse): AuthSession {
  return {
    username: res.username,
    fullName: res.fullName,
    centerId: res.centerId,
    centerName: res.centerName,
    roles: res.roles ?? []
  };
}





