import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';
import {AuthApiService} from '../api/auth-api.service';
import {AuthStore} from './auth.store';

const session = {username: 'sara', centerId: 'c1', centerName: 'ANNABA', roles: ['INFIRMIER']};

describe('AuthStore — mot de passe temporaire', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), {provide: AuthApiService, useValue: {}}],
    });
  });

  it('ne demande pas de changement par défaut', () => {
    const store = TestBed.inject(AuthStore);

    store.setSession(session);

    expect(store.mustChangePassword()).toBe(false);
  });

  it('retient l\'obligation reçue à la connexion puis la lève après le changement', () => {
    const store = TestBed.inject(AuthStore);

    store.setSession({...session, mustChangePassword: true});
    expect(store.mustChangePassword()).toBe(true);

    store.markPasswordChanged();
    expect(store.mustChangePassword()).toBe(false);
  });

  it('enregistre l\'obligation signalée par le serveur et l\'oublie à la déconnexion', () => {
    const store = TestBed.inject(AuthStore);
    store.setSession(session);

    store.requirePasswordChange();
    expect(store.mustChangePassword()).toBe(true);

    store.clearSession();
    expect(store.mustChangePassword()).toBe(false);
  });
});
