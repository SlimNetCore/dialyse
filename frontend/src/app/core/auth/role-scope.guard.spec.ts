import {TestBed} from '@angular/core/testing';
import {Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {describe, expect, it} from 'vitest';
import {AuthStore} from '../state/auth.store';
import {
  directionGuard, homeRouteFor, isDirectionArea, isDoctorArea, isDoctorOnly, isNurseArea, isNurseOnly, isOwnerArea,
  roleScopeGuard,
} from './role-scope.guard';

function setup(roles: string[]): void {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      {
        provide: AuthStore,
        useValue: {hasRole: (r: string) => roles.includes(r), roles: () => roles},
      },
      {provide: Router, useValue: {parseUrl: (u: string) => ({redirect: u}) as unknown as UrlTree}},
    ],
  });
}

function run(roles: string[], url: string): boolean | UrlTree {
  setup(roles);
  return TestBed.runInInjectionContext(
    () => roleScopeGuard({} as never, {url} as RouterStateSnapshot) as boolean | UrlTree);
}

describe('roleScopeGuard', () => {
  it('reconnaît les zones du propriétaire et de la direction', () => {
    expect(isOwnerArea('/admin/societes')).toBe(true);
    expect(isOwnerArea('/admin/societes/abc?x=1')).toBe(true);
    expect(isOwnerArea('/admin/licenses/new')).toBe(true);
    expect(isOwnerArea('/admin/users')).toBe(false);
    expect(isOwnerArea('/admin/societesx')).toBe(false);
    expect(isDirectionArea('/direction')).toBe(true);
    expect(isDirectionArea('/direction/x?a=1')).toBe(true);
    expect(isDirectionArea('/directionx')).toBe(false);
  });

  it('laisse le propriétaire dans ses zones', () => {
    expect(run(['SUPERADMIN'], '/admin/societes')).toBe(true);
    expect(run(['SUPERADMIN'], '/admin/licenses')).toBe(true);
  });

  it('renvoie le propriétaire vers les sociétés depuis toute autre page', () => {
    for (const url of ['/dashboard', '/patients', '/seances', '/stock', '/admin/users', '/direction']) {
      expect(run(['SUPERADMIN'], url), url).toEqual({redirect: '/admin/societes'});
    }
  });

  it('cantonne la direction à son tableau de bord', () => {
    expect(run(['DIRECTION'], '/direction')).toBe(true);
    for (const url of ['/dashboard', '/patients', '/admin/societes', '/admin/users', '/stock']) {
      expect(run(['DIRECTION'], url), url).toEqual({redirect: '/direction'});
    }
  });

  it('ne concerne pas les autres rôles', () => {
    expect(run(['ADMIN'], '/patients')).toBe(true);
    expect(run(['MEDECIN', 'SECRETAIRE'], '/dashboard')).toBe(true);
  });

  it('cantonne l\'infirmier seul à son planning et aux séances', () => {
    expect(run(['INFIRMIER'], '/infirmiers/moi')).toBe(true);
    expect(run(['INFIRMIER'], '/seances')).toBe(true);
    expect(run(['INFIRMIER'], '/seances?date=2026-10-05')).toBe(true);
    for (const url of ['/dashboard', '/patients', '/stock', '/admin/users', '/infirmiers', '/infirmiers/referentiel',
      '/infirmiers/absences', '/seances/planning', '/gmao', '/facturation']) {
      expect(run(['INFIRMIER'], url), url).toEqual({redirect: '/infirmiers/moi'});
    }
  });

  it('laisse tout l\'accès à un infirmier qui cumule un autre rôle de centre', () => {
    for (const autre of ['ADMIN', 'MEDECIN', 'SECRETAIRE']) {
      expect(run(['INFIRMIER', autre], '/patients'), autre).toBe(true);
      expect(isNurseOnly(['INFIRMIER', autre]), autre).toBe(false);
    }
    expect(isNurseOnly(['ROLE_INFIRMIER'])).toBe(true);
    expect(isNurseOnly(['ADMIN'])).toBe(false);
    expect(isNurseArea('/infirmiers/moi/absences')).toBe(true);
    expect(isNurseArea('/infirmiersx')).toBe(false);
  });

  it('cantonne le médecin seul à ses écrans de consultation', () => {
    for (const url of ['/medecin', '/patients', '/patients/abc', '/patients/abc/cahier', '/patients/abc/stats',
      '/patients/abc/dossier-medical', '/patients/abc/dossier-medical/prescriptions', '/patients?page=2']) {
      expect(run(['MEDECIN'], url), url).toBe(true);
    }
    for (const url of ['/dashboard', '/patients/new', '/patients/pec-admin', '/patients/pec-list',
      '/patients/attestations-list', '/seances', '/stock', '/admin/users', '/infirmiers', '/infirmiers/moi',
      '/patients/abc/autre', '/gmao', '/facturation']) {
      expect(run(['MEDECIN'], url), url).toEqual({redirect: '/medecin'});
    }
  });

  it('ouvre le suivi des absences de patients à l\'infirmier seul et au médecin seul', () => {
    expect(run(['INFIRMIER'], '/absences-patients')).toBe(true);
    expect(run(['MEDECIN'], '/absences-patients')).toBe(true);
    expect(isNurseArea('/absences-patients')).toBe(true);
    expect(isDoctorArea('/absences-patients')).toBe(true);
    expect(run(['DIRECTION'], '/absences-patients')).toEqual({redirect: '/direction'});
  });

  it('laisse tout l\'accès à un médecin qui cumule un autre rôle', () => {
    for (const autre of ['ADMIN', 'SECRETAIRE', 'INFIRMIER']) {
      expect(run(['MEDECIN', autre], '/patients/new'), autre).toBe(true);
      expect(isDoctorOnly(['MEDECIN', autre]), autre).toBe(false);
    }
    expect(isDoctorOnly(['ROLE_MEDECIN'])).toBe(true);
    expect(isDoctorArea('/medecinx')).toBe(false);
    expect(homeRouteFor(['MEDECIN'])).toBe('/medecin');
    expect(homeRouteFor(['MEDECIN', 'ADMIN'])).toBe('/dashboard');
  });

  it("choisit l'accueil selon le profil", () => {
    expect(homeRouteFor(['SUPERADMIN'])).toBe('/admin/societes');
    expect(homeRouteFor(['ROLE_DIRECTION'])).toBe('/direction');
    expect(homeRouteFor(['ADMIN'])).toBe('/dashboard');
    expect(homeRouteFor(['INFIRMIER'])).toBe('/infirmiers/moi');
    expect(homeRouteFor(['INFIRMIER', 'MEDECIN'])).toBe('/dashboard');
  });

  it("n'ouvre /direction qu'au rôle direction", () => {
    setup(['DIRECTION']);
    expect(TestBed.runInInjectionContext(() => directionGuard({} as never, {} as never))).toBe(true);
    setup(['ADMIN']);
    expect(TestBed.runInInjectionContext(() => directionGuard({} as never, {} as never))).toEqual({redirect: '/dashboard'});
  });
});
