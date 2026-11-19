import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {Component, CUSTOM_ELEMENTS_SCHEMA, provideZonelessChangeDetection, signal} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {CenterDashboardComponent} from './center-dashboard.component';
import {ACCUEIL_TUILES, tuilesVisibles} from './accueil-tuiles';
import {AuthStore} from '../../core/state/auth.store';
import {AppShellStore} from '../../core/state/app-shell.store';
import {BackendApiService} from '../../core/api/backend-api.service';
import {InfirmierApiService} from '../../core/api/infirmier-api.service';
import {StockApiService} from '../../core/api/stock-api.service';
import {GmaoApiService} from '../../core/api/gmao-api.service';
import {ShellComponent} from '../../core/layout/shell.component';
import {NotificationBellComponent} from '../../core/layout/notification-bell.component';
import {DirectionNotificationBellComponent} from '../direction/direction-notification-bell.component';
import {WebSocketService} from '../../core/ws/websocket.service';
import {AuthApiService} from '../../core/api/auth-api.service';
import fr from '../../../../public/i18n/fr.json';
import en from '../../../../public/i18n/en.json';
import ar from '../../../../public/i18n/ar.json';
import kab from '../../../../public/i18n/kab.json';

@Component({selector: 'app-ecran-cible', template: ''})
class EcranCible {
}

describe('CenterDashboardComponent — Accueil', () => {
  let roles: string[];
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let infirmiers: { alertes: ReturnType<typeof vi.fn> };
  let stock: { alertes: ReturnType<typeof vi.fn> };
  let gmao: { getStats: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    roles = ['ADMIN'];
    api = {
      getDashboardStats: vi.fn(() => of({patientCount: 42, pecExpiring: 3})),
      getSeanceMonthlyDashboard: vi.fn(() => of({totalSeances: 120, absenceCount: 4})),
      getFacturationDashboard: vi.fn(() => of({revenueTtc: 1234.5, createdInvoices: 18})),
      getReglementDashboard: vi.fn(() => of({nonReglees: 7, totalReste: 980})),
    };
    infirmiers = {alertes: vi.fn(() => of([{}, {}]))};
    stock = {alertes: vi.fn(() => of([{type: 'RUPTURE'}, {type: 'SEUIL'}, {type: 'PEREMPTION'}]))};
    gmao = {getStats: vi.fn(() => of({interventionsEnCours: 5, interventionsARelancer: 1}))};
    TestBed.configureTestingModule({
      imports: [CenterDashboardComponent, TranslateModule.forRoot()],
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
        {provide: AppShellStore, useValue: {currentCenterId: signal('c1')}},
        {provide: BackendApiService, useValue: api},
        {provide: InfirmierApiService, useValue: infirmiers},
        {provide: StockApiService, useValue: stock},
        {provide: GmaoApiService, useValue: gmao},
      ],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(CenterDashboardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return {fixture, root: fixture.nativeElement as HTMLElement};
  }

  const texte = (root: HTMLElement, testid: string) =>
    root.querySelector(`[data-testid="${testid}"]`)?.textContent?.trim();

  it('affiche une tuile par module accessible à l\'ADMIN, chacune menant au tableau de bord du module', async () => {
    const {root} = await render();

    const tuiles = Array.from(root.querySelectorAll('[data-testid^="accueil-tuile-"]'))
      .map((e) => e.getAttribute('data-testid'));
    expect(tuiles).toEqual(['accueil-tuile-patients', 'accueil-tuile-seances', 'accueil-tuile-infirmiers',
      'accueil-tuile-facturation', 'accueil-tuile-reglement', 'accueil-tuile-stock', 'accueil-tuile-gmao']);
    expect(root.querySelector('[data-testid="accueil-lien-gmao"]')?.getAttribute('href')).toBe('/gmao/tableau-de-bord');
    expect(root.querySelector('[data-testid="accueil-lien-patients"]')?.getAttribute('href'))
      .toBe('/patients/tableau-de-bord');
  });

  it('montre les chiffres clés lus sur les API des tableaux de bord', async () => {
    const {root} = await render();

    expect(texte(root, 'accueil-chiffre-patients-0')).toBe('42');
    expect(texte(root, 'accueil-chiffre-patients-1')).toBe('3');
    expect(texte(root, 'accueil-chiffre-seances-0')).toBe('120');
    expect(texte(root, 'accueil-chiffre-infirmiers-0')).toBe('2');
    expect(texte(root, 'accueil-chiffre-stock-0')).toBe('2');
    expect(texte(root, 'accueil-chiffre-stock-1')).toBe('1');
    expect(texte(root, 'accueil-chiffre-gmao-1')).toBe('1');
    expect(texte(root, 'accueil-chiffre-facturation-0')).toBe('1,234.50');
    expect(texte(root, 'accueil-chiffre-reglement-0')).toBe('7');
    expect(api['getDashboardStats']).toHaveBeenCalledWith('c1', 30);
    expect(infirmiers.alertes).toHaveBeenCalledWith('c1', 7);
  });

  it('masque la tuile Facturation à la secrétaire et n\'appelle jamais son API', async () => {
    roles = ['SECRETAIRE'];
    const {root} = await render();

    expect(root.querySelector('[data-testid="accueil-tuile-facturation"]')).toBeNull();
    expect(root.querySelector('[data-testid="accueil-tuile-reglement"]')).not.toBeNull();
    expect(api['getFacturationDashboard']).not.toHaveBeenCalled();
  });

  it('affiche un tiret pour les chiffres d\'une tuile en échec, sans gêner les autres', async () => {
    gmao.getStats.mockReturnValue(throwError(() => new Error('500')));
    const {root} = await render();

    expect(texte(root, 'accueil-chiffre-gmao-0')).toBe('—');
    expect(texte(root, 'accueil-chiffre-gmao-1')).toBe('—');
    expect(texte(root, 'accueil-chiffre-patients-0')).toBe('42');
  });

  it('n\'affiche plus les indicateurs PEC / attestations détaillés ni de filtre de mois', async () => {
    const {root} = await render();

    expect(root.querySelector('input[type="month"]')).toBeNull();
    expect(root.querySelector('input[type="number"]')).toBeNull();
    expect(root.querySelector('.pec-cree')).toBeNull();
  });

  it('chaque libellé de tuile est traduit dans toutes les langues', () => {
    const langues: Record<string, Record<string, Record<string, string>>> = {fr, en, ar, kab} as never;
    const cles = ACCUEIL_TUILES.flatMap((t) => [t.label, ...t.chiffres.map((c) => c.label)])
      .concat(['ACCUEIL.DESC', 'ACCUEIL.VOIR_TABLEAU_DE_BORD', 'ACCUEIL.INDISPONIBLE', 'ACCUEIL.AUCUN_MODULE']);
    for (const [langue, contenu] of Object.entries(langues)) {
      for (const cle of cles) {
        const [section, nom] = cle.split('.');
        expect(contenu[section]?.[nom], `${cle} (${langue})`).toBeTruthy();
      }
    }
  });
});

describe('Accueil — mêmes règles que le menu des modules', () => {
  let roles: string[];

  beforeEach(async () => {
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
            fullName: signal(''), username: signal(''), centerName: signal(''), societeName: signal(''),
            societeId: signal('s1'), clearSession: () => undefined,
          },
        },
        {provide: WebSocketService, useValue: {connect: () => undefined, disconnect: () => undefined}},
        {provide: AuthApiService, useValue: {logout: () => of(undefined)}},
      ],
    });
    TestBed.overrideComponent(ShellComponent, {
      remove: {imports: [NotificationBellComponent, DirectionNotificationBellComponent]},
      add: {schemas: [CUSTOM_ELEMENTS_SCHEMA]},
    });
    await TestBed.compileComponents();
  });

  it.each([[['ADMIN']], [['SECRETAIRE']], [['INFIRMIER', 'SECRETAIRE']], [['MEDECIN', 'ADMIN']]])(
    'profil %j : une tuile pour chaque tableau de bord de la barre de module, et seulement ceux-là', (profil) => {
      roles = profil;
      const shell = TestBed.createComponent(ShellComponent).componentInstance;
      TestBed.inject(Router);

      const tableauxDuMenu = shell.modules.flatMap((m) => m.items)
        .filter((i) => i.kind === 'tableau-de-bord').map((i) => i.route);
      expect(tuilesVisibles((r) => roles.includes(r)).map((t) => t.route)).toEqual(tableauxDuMenu);
    });
});
