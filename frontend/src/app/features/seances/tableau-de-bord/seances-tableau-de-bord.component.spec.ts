import {TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA, provideZonelessChangeDetection} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {BackendApiService} from '../../../core/api/backend-api.service';
import {seancesRoutes} from '../seances.routes';
import {SeancesStatsComponent} from './seances-stats.component';
import {SeancesTableauDeBordComponent} from './seances-tableau-de-bord.component';

describe('SeancesTableauDeBordComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SeancesTableauDeBordComponent, TranslateModule.forRoot()],
      providers: [provideZonelessChangeDetection(), {provide: BackendApiService, useValue: {}}],
    });
    TestBed.overrideComponent(SeancesTableauDeBordComponent, {
      remove: {imports: [SeancesStatsComponent]},
      add: {schemas: [NO_ERRORS_SCHEMA]},
    });
  });

  it('affiche le titre du tableau de bord et les statistiques mensuelles des séances', () => {
    const fixture = TestBed.createComponent(SeancesTableauDeBordComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent).toContain('SEANCES_TABLEAU_DE_BORD.TITLE');
    expect(root.querySelector('app-seances-stats')).not.toBeNull();
  });

  it('est servi à /seances/tableau-de-bord', async () => {
    const route = seancesRoutes.find((r) => r.path === 'tableau-de-bord');
    expect(route).toBeDefined();
    expect(await route!.loadComponent!()).toBe(SeancesTableauDeBordComponent);
  });
});
