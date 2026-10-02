import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {describe, expect, it, vi} from 'vitest';
import {AuthStore} from '../state/auth.store';
import {BackendInitService} from '../startup/backend-init.service';
import {authGuard, PASSWORD_CHANGE_URL, passwordChangeGuard} from './auth.guard';

interface Cas {
  authentifie: boolean;
  motDePasseTemporaire: boolean;
  /** Résultat de la restauration de session depuis le serveur quand rien n'est en mémoire. */
  sessionServeur?: boolean;
}

function lancer(garde: typeof authGuard, cas: Cas): Promise<boolean | UrlTree> {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      {
        provide: AuthStore,
        useValue: {
          isAuthenticated: () => cas.authentifie,
          mustChangePassword: () => cas.motDePasseTemporaire,
          initFromServer: vi.fn().mockResolvedValue(cas.sessionServeur ?? false),
        },
      },
      {provide: BackendInitService, useValue: {state: signal('ready')}},
      {provide: Router, useValue: {parseUrl: (u: string) => ({redirect: u}) as unknown as UrlTree}},
    ],
  });
  return TestBed.runInInjectionContext(
    () => garde({} as never, {url: '/dashboard'} as RouterStateSnapshot) as Promise<boolean | UrlTree>);
}

describe('authGuard', () => {
  it('laisse passer une session ouverte dont le mot de passe n\'est pas temporaire', async () => {
    expect(await lancer(authGuard, {authentifie: true, motDePasseTemporaire: false})).toBe(true);
  });

  it('renvoie vers la page de changement tant que le mot de passe est temporaire', async () => {
    expect(await lancer(authGuard, {authentifie: true, motDePasseTemporaire: true}))
      .toEqual({redirect: PASSWORD_CHANGE_URL});
  });

  it('renvoie vers la connexion sans session, sauf si le serveur en restaure une', async () => {
    expect(await lancer(authGuard, {authentifie: false, motDePasseTemporaire: false}))
      .toEqual({redirect: '/login'});
    expect(await lancer(authGuard, {authentifie: false, motDePasseTemporaire: false, sessionServeur: true}))
      .toBe(true);
  });
});

describe('passwordChangeGuard', () => {
  it('n\'ouvre la page de changement que tant que le mot de passe est temporaire', async () => {
    expect(await lancer(passwordChangeGuard, {authentifie: true, motDePasseTemporaire: true})).toBe(true);
    expect(await lancer(passwordChangeGuard, {authentifie: true, motDePasseTemporaire: false}))
      .toEqual({redirect: '/'});
  });

  it('exige une session ouverte', async () => {
    expect(await lancer(passwordChangeGuard, {authentifie: false, motDePasseTemporaire: false}))
      .toEqual({redirect: '/login'});
  });
});
