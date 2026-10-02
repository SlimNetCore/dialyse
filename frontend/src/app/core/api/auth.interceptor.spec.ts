import {HttpErrorResponse, HttpHandlerFn, HttpRequest} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {firstValueFrom, throwError} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';
import {PASSWORD_CHANGE_URL} from '../auth/auth.guard';
import {AuthStore} from '../state/auth.store';
import {AuthApiService} from './auth-api.service';
import {authInterceptor} from './auth.interceptor';

describe('authInterceptor — mot de passe temporaire', () => {
  function installer() {
    const auth = {requirePasswordChange: vi.fn(), clearSession: vi.fn()};
    const router = {navigateByUrl: vi.fn().mockResolvedValue(true)};
    const authApi = {refresh: vi.fn()};
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: AuthStore, useValue: auth},
        {provide: Router, useValue: router},
        {provide: AuthApiService, useValue: authApi},
      ],
    });
    return {auth, router, authApi};
  }

  function appeler(erreur: HttpErrorResponse): Promise<unknown> {
    const next: HttpHandlerFn = () => throwError(() => erreur);
    return TestBed.runInInjectionContext(() =>
      firstValueFrom(authInterceptor(new HttpRequest('GET', '/api/v1/patients'), next)));
  }

  it('renvoie vers la page de changement quand le serveur exige le remplacement du mot de passe', async () => {
    const {auth, router, authApi} = installer();
    const erreur = new HttpErrorResponse({status: 403, error: {code: 'PASSWORD_CHANGE_REQUIRED'}});

    await expect(appeler(erreur)).rejects.toBe(erreur);

    expect(auth.requirePasswordChange).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith(PASSWORD_CHANGE_URL);
    expect(authApi.refresh).not.toHaveBeenCalled();
  });

  it('ne touche pas aux autres refus 403', async () => {
    const {auth, router} = installer();
    const erreur = new HttpErrorResponse({status: 403, error: {code: 'ACCESS_DENIED'}});

    await expect(appeler(erreur)).rejects.toBe(erreur);

    expect(auth.requirePasswordChange).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
