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
    ['/stock', '/stock'],
    ['/stock/tableau-de-bord', '/stock/tableau-de-bord'],
    ['/stock/articles', '/stock/articles'],
    ['/stock/inventaires/inv-1', '/stock/inventaires'],
    ['/infirmiers/referentiel', '/infirmiers/referentiel'],
    ['/infirmiers/tableau-de-bord', '/infirmiers/tableau-de-bord'],
    ['/infirmiers', '/infirmiers'],
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
        '/patients/pec-list', '/patients/pec-admin', '/patients/attestations-list',
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

  it('module Stock : le tableau de bord en tête, puis les écrans de travail, puis articles et fournisseurs',
    async () => {
      const {component} = await renderAt('/stock');

      expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/stock/tableau-de-bord', '/stock',
        '/stock/bons-commande', '/stock/bons-reception', '/stock/bons-sortie', '/stock/sorties-pour-seances',
        '/stock/inventaires', '/stock/articles', '/stock/fournisseurs']);
      expect(component.activeModuleItems()[0]).toMatchObject({label: 'NAV.TABLEAU_DE_BORD', icon: 'insights'});
      expect(component.activeModuleItems().slice(-2).map((i) => i.kind)).toEqual(['parametrage', 'parametrage']);
    });

  it('la secrétaire seule ne voit ni le Stock ni la GMAO dans le menu des modules', async () => {
    roles = ['SECRETAIRE'];
    const {component} = await renderAt('/patients');

    const modules = component.modules.map((m) => m.key);
    expect(modules).not.toContain('stock');
    expect(modules).not.toContain('gmao');
    expect(modules).toContain('patients');
    expect(modules).toContain('comptabilite');
  });

  it('un cumul SECRETAIRE + ADMIN garde le Stock et la GMAO', async () => {
    roles = ['SECRETAIRE', 'ADMIN'];
    const {component} = await renderAt('/patients');

    expect(component.modules.map((m) => m.key)).toEqual(expect.arrayContaining(['stock', 'gmao']));
  });

  it('module Facturation : le tableau de bord en tête, puis le calcul et la validation, puis la liste des factures',
    async () => {
      const {component} = await renderAt('/facturation');

      expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/facturation/tableau-de-bord',
        '/facturation', '/facturation/factures']);
      expect(component.activeModuleItems()[0]).toMatchObject({label: 'NAV.TABLEAU_DE_BORD', icon: 'insights'});
      expect(component.activeModuleItems()[1]).toMatchObject({label: 'NAV.FACTURATION_CALCUL'});
      expect(component.activeModuleItems()[2]).toMatchObject({
        label: 'NAV.FACTURATION_FACTURES', icon: 'receipt_long'});
      expect(component.activeItemRoute()).toBe('/facturation');
    });

  it.each([
    [['MEDECIN']],
    [['INFIRMIER']],
    [['SECRETAIRE']],
  ])('la liste des factures n\'est proposée qu\'à l\'ADMIN (absente pour %j, que le serveur refuse)', async (profil) => {
    roles = profil;
    const fixture = TestBed.createComponent(ShellComponent);

    const routes = fixture.componentInstance.modules.flatMap((m) => m.items.map((i) => i.route));
    expect(routes).not.toContain('/facturation/factures');
  });

  it('la liste des factures est l\'entrée active sur /facturation/factures, avec son fil d\'Ariane', async () => {
    const {component} = await renderAt('/facturation/factures');

    expect(component.activeItemRoute()).toBe('/facturation/factures');
    expect(breadcrumbLabels('/facturation/factures')).toEqual(['NAV.FACTURATION', 'NAV.FACTURATION_FACTURES']);
  });

  it('module Règlement : le tableau de bord en tête, puis la saisie des règlements', async () => {
    const {component} = await renderAt('/reglement');

    expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/reglement/tableau-de-bord', '/reglement']);
    expect(component.activeModuleItems()[0]).toMatchObject({label: 'NAV.TABLEAU_DE_BORD', icon: 'insights'});
    expect(component.activeModuleItems()[1]).toMatchObject({label: 'NAV.REGLEMENT_SAISIE'});
    expect(component.activeItemRoute()).toBe('/reglement');
  });

  it('module Comptabilité : pas de tableau de bord, le journal, la saisie d\'une pièce, puis le paramétrage',
    async () => {
      const {component} = await renderAt('/comptabilite');

      expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/comptabilite', '/comptabilite/pieces',
        '/comptabilite/parametrage']);
      expect(component.activeModuleItems().map((i) => i.label)).not.toContain('NAV.TABLEAU_DE_BORD');
      expect(component.activeModuleItems().map((i) => i.kind)).toEqual(['travail', 'travail', 'parametrage']);
      expect(component.activeItemRoute()).toBe('/comptabilite');
    });

  it('module Comptabilité : la secrétaire saisit des pièces mais ne voit pas le paramétrage', async () => {
    roles = ['SECRETAIRE'];
    const {component} = await renderAt('/comptabilite/pieces');

    expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/comptabilite', '/comptabilite/pieces']);
    expect(component.activeItemRoute()).toBe('/comptabilite/pieces');
  });

  it('module Infirmiers : le tableau de bord en tête, puis les écrans de travail, puis le référentiel', async () => {
    const {component} = await renderAt('/infirmiers');

    expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/infirmiers/tableau-de-bord', '/infirmiers',
      '/infirmiers/absences', '/infirmiers/charge', '/infirmiers/referentiel']);
    expect(component.activeModuleItems()[0]).toMatchObject({label: 'NAV.TABLEAU_DE_BORD', icon: 'insights'});
    expect(component.activeItemRoute()).toBe('/infirmiers');
  });

  it('module Infirmiers : l\'infirmier seul ne voit que son planning, pas le tableau de bord', async () => {
    roles = ['INFIRMIER'];
    const {component} = await renderAt('/infirmiers/moi');

    expect(component.activeModuleItems().map((i) => i.route)).toEqual(['/infirmiers/moi']);
  });

  it('le référentiel des infirmiers, déclaré aussi dans la barre Administration, s\'active dans son module d\'origine',
    async () => {
      const {component} = await renderAt('/infirmiers/referentiel');

      expect(component.activeModuleItems().map((i) => i.route)).toContain('/infirmiers/charge');
      expect(component.activeModuleItems().map((i) => i.route)).not.toContain('/admin/users');
    });

  it('administration du centre : Comptes | Paramétrage | Supervision, séparés discrètement', async () => {
    const {root, component} = await renderAt('/admin/users');

    expect(component.activeModuleItems().map((i) => [i.route, i.kind])).toEqual([
      ['/admin/users', 'travail'],
      ['/admin/roles', 'travail'],
      ['/admin/parametrage/calendrier-clinique', 'parametrage'],
      ['/admin/parametrage/facturation', 'parametrage'],
      ['/admin/parametrage/tva', 'parametrage'],
      ['/admin/parametrage/referentiels', 'parametrage'],
      ['/admin/parametrage/groupes-articles', 'parametrage'],
      ['/admin/parametrage/planning-centre', 'parametrage'],
      ['/admin/parametrage/modeles-document', 'parametrage'],
      ['/infirmiers/referentiel', 'parametrage'],
      ['/admin/audit', 'supervision'],
      ['/admin/parametrage/reprise', 'supervision'],
    ]);
    expect(root.querySelectorAll('[data-testid="module-bar-separator"]')).toHaveLength(2);
    // le journal d'audit n'est plus déclaré en double
    expect(component.activeModuleItems().filter((i) => i.route === '/admin/audit')).toHaveLength(1);
  });

  it('les modèles de documents s\'activent dans la barre Administration, plus dans l\'Accueil', async () => {
    const {component} = await renderAt('/admin/parametrage/modeles-document');

    expect(component.activeItemRoute()).toBe('/admin/parametrage/modeles-document');
    expect(component.activeModuleItems().map((i) => i.route)).toContain('/admin/users');
  });

  it('l\'Accueil (/dashboard) n\'a plus que sa propre entrée, libellée Accueil', async () => {
    const {component} = await renderAt('/dashboard');

    expect(component.activeModuleItems().map((i) => [i.route, i.label])).toEqual([['/dashboard', 'NAV.ACCUEIL']]);
    expect(component.navItems.find((m) => m.route === '/dashboard')?.label).toBe('NAV.ACCUEIL');
    expect(breadcrumbLabels('/dashboard')).toEqual(['NAV.ACCUEIL']);
  });

  it('le propriétaire garde le journal d\'audit parmi ses seules entrées, sans groupes', async () => {
    roles = ['SUPERADMIN'];
    const {root, component} = await renderAt('/admin/societes');

    expect(component.activeModuleItems().map((i) => i.route))
      .toEqual(['/admin/societes', '/admin/licenses', '/admin/performance', '/admin/audit']);
    expect(root.querySelectorAll('[data-testid="module-bar-separator"]')).toHaveLength(0);
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
  it('ordonne tableau de bord | travail | paramétrage | supervision et marque le début de chaque groupe', () => {
    const items = toModuleBarItems([
      {route: '/s', label: 'S', icon: 's', kind: 'supervision'},
      {route: '/a', label: 'A', icon: 'a'},
      {route: '/p', label: 'P', icon: 'p', kind: 'parametrage'},
      {route: '/b', label: 'B', icon: 'b'},
      {route: '/t', label: 'T', icon: 't', kind: 'tableau-de-bord'},
    ]);

    expect(items.map((i) => i.route)).toEqual(['/t', '/a', '/b', '/p', '/s']);
    expect(items.map((i) => i.separatorBefore)).toEqual([false, true, false, true, true]);
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

  it('nomme les pages du tableau de bord de la direction, paramètres d\'URL ignorés', () => {
    expect(breadcrumbLabels('/direction?from=2026-01-01&to=2026-01-31')).toEqual(['NAV.DIRECTION']);
    expect(breadcrumbLabels('/direction/analyses?centre=c1')).toEqual(['NAV.DIRECTION', 'NAV.DIRECTION_ANALYSES']);
    expect(breadcrumbLabels('/direction/rapports')).toEqual(['NAV.DIRECTION', 'NAV.DIRECTION_RAPPORTS']);
  });

  it('distingue le paramétrage de la facturation du module Facturation', () => {
    expect(breadcrumbLabels('/facturation')).toEqual(['NAV.FACTURATION']);
    expect(breadcrumbLabels('/admin/parametrage/facturation'))
      .toEqual(['NAV.ADMIN', 'NAV.PARAMETRAGE', 'NAV.ADMIN_PARAMETRAGE_FACTURATION']);
  });
});
