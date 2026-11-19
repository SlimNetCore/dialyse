import {TestBed} from '@angular/core/testing';
import {Component, CUSTOM_ELEMENTS_SCHEMA, provideZonelessChangeDetection, signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {of} from 'rxjs';
import {activeModuleItemRoute, breadcrumbLabels, ShellComponent, toModuleBarItems} from './shell.component';
import {NotificationBellComponent} from './notification-bell.component';
import {DirectionNotificationBellComponent} from '../../features/direction/direction-notification-bell.component';
import {AuthStore} from '../state/auth.store';
import {WebSocketService} from '../ws/websocket.service';
import {AuthApiService} from '../api/auth-api.service';
import fr from '../../../../public/i18n/fr.json';
import en from '../../../../public/i18n/en.json';
import ar from '../../../../public/i18n/ar.json';
import kab from '../../../../public/i18n/kab.json';

const LANGUES: Record<string, { NAV: Record<string, string> }> = {fr, en, ar, kab};

/** Écran d'arrivée factice : la navigation est réelle, seul le contenu est vide. */
@Component({selector: 'app-ecran-cible', template: ''})
class EcranCible {
}

describe('ShellComponent — barre de module', () => {
  let roles: string[];

  beforeEach(async () => {
    roles = ['ADMIN'];
    TestBed.configureTestingModule({
      imports: [ShellComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{path: '**', component: EcranCible}]),
        {
          provide: AuthStore,
          useValue: {
            hasRole: (r: string) => roles.includes(r),
            roles: () => roles,
            fullName: signal('Admin Test'),
            username: signal('admin'),
            centerName: signal('Centre'),
            societeName: signal('Société'),
            societeId: signal('s1'),
            clearSession: () => undefined,
          },
        },
        {provide: WebSocketService, useValue: {connect: () => undefined, disconnect: () => undefined}},
        {provide: AuthApiService, useValue: {logout: () => of(undefined)}},
      ],
    });
    // Les cloches de notification ont leurs propres tests : on les retire pour isoler la navigation.
    TestBed.overrideComponent(ShellComponent, {
      remove: {imports: [NotificationBellComponent, DirectionNotificationBellComponent]},
      add: {schemas: [CUSTOM_ELEMENTS_SCHEMA]},
    });
    await TestBed.compileComponents();
  });

  async function renderAt(url: string) {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl(url);
    fixture.detectChanges();
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const actives = Array.from(root.querySelectorAll<HTMLElement>('.module-item-btn.active-module-item'));
    return {fixture, root, actives, component: fixture.componentInstance};
  }

  it.each([
    ['/patients', '/patients'],
    ['/patients/123', '/patients'],
    ['/patients/pec-list', '/patients/pec-list'],
    ['/patients/tableau-de-bord', '/patients/tableau-de-bord'],
    ['/patients/pec-admin', '/patients/pec-admin'],
    ['/gmao/equipements', '/gmao/equipements'],
    ['/gmao/equipements/new', '/gmao/equipements'],
    ['/gmao/tableau-de-bord', '/gmao/tableau-de-bord'],
    ['/seances/absences-patients', '/seances/absences-patients'],
    ['/seances/tableau-de-bord', '/seances/tableau-de-bord'],
    ['/seances', '/seances'],
    ['/infirmiers/referentiel', '/infirmiers/referentiel'],
  ])('sur %s, une seule entrée est active : %s', async (url, attendu) => {
    const {actives, component} = await renderAt(url);

    expect(actives).toHaveLength(1);
    expect(component.activeItemRoute()).toBe(attendu);
    expect(actives[0].getAttribute('aria-current')).toBe('page');
  });

  it('module Patients : le tableau de bord en tête, puis les écrans de travail (validation PEC pour l\'ADMIN)',
    async () => {
      const {component} = await renderAt('/patients');

      expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/patients/tableau-de-bord', '/patients',
        '/patients/new', '/patients/pec-list', '/patients/pec-admin', '/patients/attestations-list',
        '/patients/mouvements']);
      expect(component.activeModuleItems()[0]).toMatchObject({label: 'NAV.TABLEAU_DE_BORD', icon: 'insights'});
    });

  it('module Patients : la secrétaire ne voit pas la validation PEC', async () => {
    roles = ['SECRETAIRE'];
    const {component} = await renderAt('/patients');

    expect(component.activeModuleItems().map((i) => i.route)).not.toContain('/patients/pec-admin');
  });

  it('module Patients : le médecin seul voit le tableau de bord et la liste', async () => {
    roles = ['MEDECIN'];
    const {component} = await renderAt('/patients');

    expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/patients/tableau-de-bord', '/patients']);
  });

  it('module Séances : le tableau de bord en tête, puis les écrans de travail', async () => {
    const {component} = await renderAt('/seances');

    expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/seances/tableau-de-bord', '/seances',
      '/seances/historique', '/seances/planning', '/seances/salles', '/seances/optimisation',
      '/seances/absences-patients']);
    expect(component.activeModuleItems()[0]).toMatchObject({label: 'NAV.TABLEAU_DE_BORD', icon: 'insights'});
  });

  it('module Séances : l\'infirmier seul garde le tableau de bord, le poste, l\'historique et les absences', async () => {
    roles = ['INFIRMIER'];
    const {component} = await renderAt('/seances');

    expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/seances/tableau-de-bord', '/seances',
      '/seances/historique', '/seances/absences-patients']);
  });

  it('le référentiel des infirmiers, déclaré aussi dans la barre Administration, s\'active dans son module d\'origine',
    async () => {
      const {component} = await renderAt('/infirmiers/referentiel');

      expect(component.activeModuleItems().map((i) => i.route)).toContain('/infirmiers/charge');
      expect(component.activeModuleItems().map((i) => i.route)).not.toContain('/admin/users');
    });

  it('sépare discrètement les groupes : le paramétrage de l\'administration suit ses écrans de travail', async () => {
    const {root, component} = await renderAt('/admin/users');

    const kinds = component.activeModuleItems().map((i) => i.kind);
    expect(kinds.indexOf('parametrage')).toBeGreaterThan(kinds.lastIndexOf('travail'));
    expect(root.querySelectorAll('[data-testid="module-bar-separator"]')).toHaveLength(1);
    // le journal d'audit n'est plus déclaré en double
    expect(component.activeModuleItems().filter((i) => i.route === '/admin/audit')).toHaveLength(1);
  });

  it('le propriétaire garde le journal d\'audit parmi ses seules entrées', async () => {
    roles = ['SUPERADMIN'];
    const {component} = await renderAt('/admin/societes');

    expect(component.activeModuleItems().map((i) => i.route))
      .toEqual(['/admin/societes', '/admin/licenses', '/admin/performance', '/admin/audit']);
  });

  it('toutes les entrées de la barre de module sont des clés NAV.* traduites dans chaque langue', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    const labels = fixture.componentInstance.modules.flatMap((m) => m.items.map((i) => i.label));
    const cles = new Set([...labels, ...fixture.componentInstance.navItems.map((m) => m.label),
      'NAV.TABLEAU_DE_BORD', 'NAV.ACCUEIL', 'NAV.MODULES', 'NAV.FICHE', 'NAV.MODELES_DOCUMENT',
      ...breadcrumbLabels('/stock/articles/bons-commande/bons-reception/bons-sortie/inventaires/fournisseurs'),
      ...breadcrumbLabels('/admin/societes/licenses/audit/users/roles/parametrage/facturation/tva/referentiels/reprise'),
    ]);
    for (const [langue, {NAV: nav}] of Object.entries(LANGUES)) {
      for (const cle of cles) {
        expect(cle, `${cle} (${langue})`).toMatch(/^NAV\./);
        expect(nav[cle.slice(4)], `${cle} (${langue})`).toBeTruthy();
      }
    }
  });
});

describe('activeModuleItemRoute', () => {
  const items = [{route: '/patients'}, {route: '/patients/new'}, {route: '/patients/pec-list'}];

  it('retient le plus long préfixe par segment, sans confondre un préfixe de caractères', () => {
    expect(activeModuleItemRoute('/patients/pec-list?page=2', items)).toBe('/patients/pec-list');
    expect(activeModuleItemRoute('/patients/123/edit', items)).toBe('/patients');
    expect(activeModuleItemRoute('/patients-archives', items)).toBeNull();
  });
});

describe('toModuleBarItems', () => {
  it('ordonne tableau de bord | travail | paramétrage et marque le début de chaque groupe', () => {
    const items = toModuleBarItems([
      {route: '/a', label: 'A', icon: 'a'},
      {route: '/p', label: 'P', icon: 'p', kind: 'parametrage'},
      {route: '/b', label: 'B', icon: 'b'},
      {route: '/t', label: 'T', icon: 't', kind: 'tableau-de-bord'},
    ]);

    expect(items.map((i) => i.route)).toEqual(['/t', '/a', '/b', '/p']);
    expect(items.map((i) => i.separatorBefore)).toEqual([false, true, false, true]);
  });
});

describe('breadcrumbLabels', () => {
  it('traduit les segments GMAO et stock par la règle commune', () => {
    expect(breadcrumbLabels('/gmao/equipements')).toEqual(['NAV.GMAO', 'NAV.GMAO_EQUIPEMENTS']);
    expect(breadcrumbLabels('/gmao/tableau-de-bord')).toEqual(['NAV.GMAO', 'NAV.TABLEAU_DE_BORD']);
    expect(breadcrumbLabels('/stock/tableau-de-bord')).toEqual(['NAV.STOCK', 'NAV.TABLEAU_DE_BORD']);
    expect(breadcrumbLabels('/stock/bons-commande')).toEqual(['NAV.STOCK', 'NAV.STOCK_BONS_COMMANDE']);
  });

  it('affiche « Fiche » à la place d\'un identifiant technique', () => {
    expect(breadcrumbLabels('/patients/123')).toEqual(['NAV.PATIENTS', 'NAV.FICHE']);
    expect(breadcrumbLabels('/gmao/equipements/3f2a1b4c-0d9e-4f00-8a1b-2c3d4e5f6a7b'))
      .toEqual(['NAV.GMAO', 'NAV.GMAO_EQUIPEMENTS', 'NAV.FICHE']);
  });

  it('distingue le paramétrage de la facturation du module Facturation', () => {
    expect(breadcrumbLabels('/facturation')).toEqual(['NAV.FACTURATION']);
    expect(breadcrumbLabels('/admin/parametrage/facturation'))
      .toEqual(['NAV.ADMIN', 'NAV.PARAMETRAGE', 'NAV.ADMIN_PARAMETRAGE_FACTURATION']);
  });
});
