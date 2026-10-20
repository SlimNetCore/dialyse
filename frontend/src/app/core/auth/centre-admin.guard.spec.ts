import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {describe, expect, it} from 'vitest';
import {AuthStore} from '../state/auth.store';
import {centreAdminGuard} from './admin.guard';

function run(roles: string[]): boolean | UrlTree {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      {provide: AuthStore, useValue: {hasRole: (r: string) => roles.includes(r), roles: () => roles}},
      {provide: Router, useValue: {parseUrl: (u: string) => ({redirect: u}) as unknown as UrlTree}},
    ],
  });
  return TestBed.runInInjectionContext(
    () => centreAdminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as boolean | UrlTree);
}

describe('centreAdminGuard (écran de facturation)', () => {
  it('laisse passer l\'administrateur du centre', () => {
    expect(run(['ADMIN'])).toBe(true);
  });

  it('renvoie le médecin seul vers son accueil', () => {
    expect(run(['MEDECIN'])).toEqual({redirect: '/medecin'});
  });

  it('refuse la secrétaire et le propriétaire (SUPERADMIN sans centre)', () => {
    expect(run(['SECRETAIRE'])).toEqual({redirect: '/dashboard'});
    expect(run(['SUPERADMIN'])).toEqual({redirect: '/admin/societes'});
  });
});
