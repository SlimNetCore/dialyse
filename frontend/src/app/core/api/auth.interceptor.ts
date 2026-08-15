import {HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {catchError, finalize, Observable, shareReplay, switchMap, throwError} from 'rxjs';
import {AuthApiService} from './auth-api.service';
import {AuthStore} from '../state/auth.store';

let refreshInFlight$: Observable<any> | null = null;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/v1/')) {
    return next(req);
  }

  const authApi = inject(AuthApiService);
  const authStore = inject(AuthStore);
  const isRefreshCall = req.url.includes('/api/v1/auth/refresh');
  const isLoginOrLogout = req.url.includes('/api/v1/auth/login') || req.url.includes('/api/v1/auth/logout');
  const skipRefresh = req.headers.has('x-skip-auth-refresh');

  const cloned = req.clone({
    withCredentials: true
  });

  return next(cloned).pipe(
    catchError((err) => {
      if (isRefreshCall || isLoginOrLogout || skipRefresh || err?.status !== 401) {
        return throwError(() => err);
      }

      if (!refreshInFlight$) {
        refreshInFlight$ = authApi.refresh().pipe(
          finalize(() => refreshInFlight$ = null),
          shareReplay(1)
        );
      }

      return refreshInFlight$.pipe(
        switchMap(() => next(cloned)),
        catchError((refreshErr) => {
          if (refreshErr?.status === 0 || refreshErr?.status >= 500) {
            return throwError(() => refreshErr);
          }

          if (refreshErr?.status === 403) {
            authStore.clearSession();
            window.location.assign('/login');
            return throwError(() => refreshErr);
          }

          authStore.clearSession();
          window.location.assign('/login');
          return throwError(() => refreshErr);
        })
      );
    })
  );
};

