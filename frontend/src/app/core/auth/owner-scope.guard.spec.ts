import {TestBed} from '@angular/core/testing';
import {Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {describe, expect, it} from 'vitest';
import {AuthStore} from '../state/auth.store';
import {isOwnerArea, ownerScopeGuard} from './owner-scope.guard';

function run(roles: string[], url: string): boolean | UrlTree {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      {provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r)}},
      {provide: Router, useValue: {parseUrl: (u: string) => ({redirect: u}) as unknown as UrlTree}},
    ],
  });
  return TestBed.runInInjectionContext(
    () => ownerScopeGuard({} as never, {url} as RouterStateSnapshot) as boolean | UrlTree);
}

describe('ownerScopeGuard', () => {
  it('reconnaît les zones du propriétaire', () => {
    expect(isOwnerArea('/admin/societes')).toBe(true);
    expect(isOwnerArea('/admin/societes/abc?x=1')).toBe(true);
    expect(isOwnerArea('/admin/licenses/new')).toBe(true);
    expect(isOwnerArea('/admin/users')).toBe(false);
    expect(isOwnerArea('/admin/societesx')).toBe(false);
  });

  it('laisse le propriétaire dans ses zones', () => {
    expect(run(['SUPERADMIN'], '/admin/societes')).toBe(true);
    expect(run(['SUPERADMIN'], '/admin/licenses')).toBe(true);
  });

  it('renvoie le propriétaire vers les sociétés depuis toute autre page', () => {
    for (const url of ['/dashboard', '/patients', '/seances', '/stock', '/admin/users', '/admin/roles']) {
      expect(run(['SUPERADMIN'], url), url).toEqual({redirect: '/admin/societes'});
    }
  });

  it('ne concerne pas les autres rôles', () => {
    expect(run(['ADMIN'], '/patients')).toBe(true);
    expect(run(['MEDECIN'], '/dashboard')).toBe(true);
  });
});
