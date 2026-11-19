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
    expect(isOwnerArea('/admin/audit')).toBe(true);
    expect(isOwnerArea('/admin/performance')).toBe(true);
    expect(isOwnerArea('/admin/users')).toBe(false);
    expect(isOwnerArea('/admin/societesx')).toBe(false);
    expect(isDirectionArea('/direction')).toBe(true);
    expect(isDirectionArea('/direction/x?a=1')).toBe(true);
    expect(isDirectionArea('/directionx')).toBe(false);
  });

  it('laisse le propriétaire dans ses zones', () => {
    expect(run(['SUPERADMIN'], '/admin/societes')).toBe(true);
    expect(run(['SUPERADMIN'], '/admin/licenses')).toBe(true);
    expect(run(['SUPERADMIN'], '/admin/audit')).toBe(true);
    expect(run(['SUPERADMIN'], '/admin/performance')).toBe(true);
  });

  it('renvoie le propriétaire vers les sociétés depuis toute autre page', () => {
    for (const url of ['/dashboard', '/patients', '/seances', '/stock', '/admin/users', '/direction']) {
      expect(run(['SUPERADMIN'], url), url).toEqual({redirect: '/admin/societes'});
    }
  });

  it('cantonne la direction à son tableau de bord', () => {
    expect(run(['DIRECTION'], '/direction')).toBe(true);
    // les trois pages du tableau de bord (vue d'ensemble, analyses, rapports), avec la période et le centre en URL
    expect(run(['DIRECTION'], '/direction/analyses')).toBe(true);
    expect(run(['DIRECTION'], '/direction/rapports?from=2026-01-01&to=2026-01-31&centre=c1')).toBe(true);
    for (const url of ['/dashboard', '/patients', '/admin/societes', '/admin/users', '/stock', '/stock/tableau-de-bord']) {
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
    for (const url of ['/dashboard', '/patients', '/stock', '/stock/tableau-de-bord', '/admin/users', '/infirmiers',
      '/infirmiers/referentiel', '/infirmiers/absences', '/seances/planning', '/gmao', '/facturation',
      '/facturation/tableau-de-bord', '/reglement', '/reglement/tableau-de-bord', '/infirmiers/tableau-de-bord',
      '/infirmiers/charge']) {
      expect(run(['INFIRMIER'], url), url).toEqual({redirect: '/infirmiers/moi'});
    }
  });

  it('ouvre le tableau de bord Infirmiers à la gestion du centre, ni à l\'infirmier seul, ni au médecin seul', () => {
    expect(isNurseArea('/infirmiers/tableau-de-bord')).toBe(false);
    for (const roles of [['ADMIN'], ['SECRETAIRE'], ['INFIRMIER', 'ADMIN'], ['MEDECIN', 'SECRETAIRE']]) {
      expect(run(roles, '/infirmiers/tableau-de-bord'), roles.join('+')).toBe(true);
    }
    expect(run(['MEDECIN'], '/infirmiers/tableau-de-bord')).toEqual({redirect: '/medecin'});
    expect(run(['DIRECTION'], '/infirmiers/tableau-de-bord')).toEqual({redirect: '/direction'});
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
      '/patients/attestations-list', '/seances', '/stock', '/stock/tableau-de-bord', '/admin/users', '/infirmiers', '/infirmiers/moi',
      '/patients/abc/autre', '/gmao', '/facturation', '/facturation/tableau-de-bord', '/reglement',
      '/reglement/tableau-de-bord']) {
      expect(run(['MEDECIN'], url), url).toEqual({redirect: '/medecin'});
    }
  });

  it('ouvre au médecin seul le tableau de bord Patients, sans en faire un identifiant de patient', () => {
    expect(run(['MEDECIN'], '/patients/tableau-de-bord')).toBe(true);
    expect(run(['MEDECIN'], '/patients/tableau-de-bord?mois=2026-10')).toBe(true);
    for (const url of ['/patients/tableau-de-bord/cahier', '/patients/tableau-de-bord/stats',
      '/patients/tableau-de-bord/dossier-medical']) {
      expect(run(['MEDECIN'], url), url).toEqual({redirect: '/medecin'});
    }
    // l'infirmier seul n'a toujours pas accès au module Patients
    expect(run(['INFIRMIER'], '/patients/tableau-de-bord')).toEqual({redirect: '/infirmiers/moi'});
    expect(run(['ADMIN'], '/patients/tableau-de-bord')).toBe(true);
  });

  it('laisse les mouvements de patients tels qu\'aujourd\'hui pour le médecin seul (droits inchangés)', () => {
    expect(isDoctorArea('/patients/mouvements')).toBe(true);
  });

  it('ouvre l\'historique des séances à l\'infirmier seul mais pas au médecin seul', () => {
    expect(run(['INFIRMIER'], '/seances/historique')).toBe(true);
    expect(isNurseArea('/seances/historique')).toBe(true);
    expect(isNurseArea('/seances/planning')).toBe(false);
    expect(isDoctorArea('/seances/historique')).toBe(false);
  });

  it('ouvre le tableau de bord des séances à l\'infirmier seul (statistiques qu\'il voyait dans l\'historique), pas au médecin seul',
    () => {
      expect(run(['INFIRMIER'], '/seances/tableau-de-bord')).toBe(true);
      expect(run(['INFIRMIER'], '/seances/tableau-de-bord?mois=2026-10')).toBe(true);
      expect(isNurseArea('/seances/tableau-de-bord/autre')).toBe(false);
      expect(run(['MEDECIN'], '/seances/tableau-de-bord')).toEqual({redirect: '/medecin'});
      expect(run(['DIRECTION'], '/seances/tableau-de-bord')).toEqual({redirect: '/direction'});
      expect(run(['ADMIN'], '/seances/tableau-de-bord')).toBe(true);
      expect(run(['SECRETAIRE'], '/seances/tableau-de-bord')).toBe(true);
    });

  it('donne au médecin seul le planning à son accueil (/medecin), pas à l\'adresse des séances', () => {
    expect(run(['MEDECIN'], '/medecin')).toBe(true);
    expect(run(['MEDECIN'], '/seances/planning')).toEqual({redirect: '/medecin'});
    expect(isDoctorArea('/seances/planning')).toBe(false);
    expect(run(['INFIRMIER'], '/seances/planning')).toEqual({redirect: '/infirmiers/moi'});
    expect(isDoctorArea('/seances/optimisation')).toBe(false);
    expect(isDoctorArea('/seances/salles')).toBe(false);
  });

  it('ouvre le suivi des absences de patients à l\'infirmier seul et au médecin seul', () => {
    expect(run(['INFIRMIER'], '/seances/absences-patients')).toBe(true);
    expect(run(['MEDECIN'], '/seances/absences-patients')).toBe(true);
    expect(isNurseArea('/seances/absences-patients')).toBe(true);
    expect(isDoctorArea('/seances/absences-patients')).toBe(true);
    expect(run(['DIRECTION'], '/seances/absences-patients')).toEqual({redirect: '/direction'});
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
