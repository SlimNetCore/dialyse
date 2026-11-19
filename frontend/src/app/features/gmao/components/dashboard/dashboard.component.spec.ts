import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {of} from 'rxjs';
import {GmaoDashboardComponent} from './dashboard.component';
import {GmaoApiService, GmaoStats} from '../../../../core/api/gmao-api.service';

const STATS: GmaoStats = {
  totalEquipements: 12,
  equipementsEnService: 8,
  equipementsEnMaintenance: 2,
  equipementsHorsService: 1,
  totalInterventions: 30,
  interventionsEnCours: 3,
  interventionsTerminees: 25,
  plansMaintenanceActifs: 5,
  plansMaintenanceEnRetard: 1,
  interventionsARelancer: 2,
};

describe('GmaoDashboardComponent — indicateurs cliquables', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GmaoDashboardComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {provide: GmaoApiService, useValue: {getStats: () => of(STATS)}},
      ],
    });
  });

  async function rendu(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(GmaoDashboardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function lien(root: HTMLElement, testid: string): string {
    return root.querySelector<HTMLAnchorElement>(`[data-testid="${testid}"]`)!.getAttribute('href')!;
  }

  it('chaque statut mène à la liste filtrée sur ce statut', async () => {
    const root = await rendu();

    expect(lien(root, 'kpi-equipements-hors-service')).toBe('/gmao/equipements?statut=HORS_SERVICE');
    expect(lien(root, 'kpi-equipements-en-service')).toBe('/gmao/equipements?statut=EN_SERVICE');
    expect(lien(root, 'kpi-interventions-en-cours')).toBe('/gmao/interventions?statut=EN_COURS');
    expect(lien(root, 'kpi-interventions-terminees')).toBe('/gmao/interventions?statut=TERMINEE');
  });

  it('les totaux et les interventions à relancer mènent à la liste sans filtre de statut', async () => {
    const root = await rendu();

    expect(lien(root, 'kpi-equipements-total')).toBe('/gmao/equipements?statut=');
    expect(lien(root, 'kpi-interventions-total')).toBe('/gmao/interventions?statut=');
    expect(lien(root, 'kpi-relance')).toBe('/gmao/interventions?statut=');
  });

  it('la création d\'un équipement reste dans la liste des équipements, pas dans le tableau de bord', async () => {
    const root = await rendu();

    expect(root.querySelector('a[href="/gmao/equipements/new"]')).toBeNull();
  });
});
