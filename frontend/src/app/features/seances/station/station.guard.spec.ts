import {TestBed} from '@angular/core/testing';
import {Route, UrlSegment} from '@angular/router';
import {describe, expect, it} from 'vitest';
import {seanceStationMatch} from './station.guard';
import {AuthStore} from '../../../core/state/auth.store';

function matchFor(roles: string[]): boolean {
  TestBed.configureTestingModule({
    providers: [{provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r)}}],
  });
  return TestBed.runInInjectionContext(() => seanceStationMatch({} as Route, [] as UrlSegment[], {} as never)) as boolean;
}

describe('seanceStationMatch', () => {
  it('ouvre le poste infirmier pour l\'infirmier, la secrétaire et l\'administrateur', () => {
    expect(matchFor(['INFIRMIER'])).toBe(true);
    TestBed.resetTestingModule();
    expect(matchFor(['SECRETAIRE'])).toBe(true);
    TestBed.resetTestingModule();
    expect(matchFor(['ADMIN'])).toBe(true);
  });

  it('ouvre aussi le poste pour les autres rôles de gestion (direction, super-admin)', () => {
    expect(matchFor(['DIRECTION'])).toBe(true);
    TestBed.resetTestingModule();
    expect(matchFor(['SUPERADMIN'])).toBe(true);
  });

  it('garde l\'écran de consultation pour le médecin seul', () => {
    expect(matchFor(['MEDECIN'])).toBe(false);
  });

  it('un médecin qui est aussi administrateur travaille au poste', () => {
    expect(matchFor(['MEDECIN', 'ADMIN'])).toBe(true);
  });
});
