import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection, signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {InfirmierApiService} from '../../../core/api/infirmier-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {infirmierRoutes} from '../infirmier.routes';
import {InfirmiersTableauDeBordComponent} from './infirmiers-tableau-de-bord.component';

describe('InfirmiersTableauDeBordComponent', () => {
  let api: Record<'alertes' | 'charge' | 'listAbsences' | 'list', ReturnType<typeof vi.fn>>;
  const centre = signal<string | null>('c-1');

  beforeEach(async () => {
    centre.set('c-1');
    api = {
      alertes: vi.fn(() => of([{
        date: '2026-11-24', jour: 'MARDI', salleId: 's1', creneauId: 'cr1', patients: 8, requis: 2, manque: 1,
        absents: ['Amina B'],
      }])),
      charge: vi.fn(() => of({items: [], total: 0, page: 0, size: 100, moyenne: 0})),
      listAbsences: vi.fn(() => of({items: [], total: 0, page: 0, size: 100})),
      list: vi.fn(() => of({items: [], total: 0, page: 0, size: 100})),
    };
    await TestBed.configureTestingModule({
      imports: [InfirmiersTableauDeBordComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {provide: InfirmierApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: centre}},
      ],
    }).compileComponents();
  });

  async function rendre() {
    const fixture = TestBed.createComponent(InfirmiersTableauDeBordComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('est servi en lecture seule sur /infirmiers/tableau-de-bord, sans changer l écran d arrivée du module', () => {
    expect(infirmierRoutes.map((r) => r.path)).toContain('tableau-de-bord');
    expect(infirmierRoutes[0].path).toBe('');
  });

  it('chaque créneau à risque mène à sa semaine dans la grille de présence (date seule en paramètre)', async () => {
    const root = await rendre();

    const liens = root.querySelectorAll<HTMLAnchorElement>('[data-testid="itdb-alerte"]');
    expect(liens).toHaveLength(1);
    expect(liens[0].getAttribute('href')).toBe('/infirmiers?date=2026-11-24');
    expect(root.querySelector('[data-testid="itdb-nb-alertes"]')?.textContent?.trim()).toBe('1');
  });

  it('affiche les états vides des charges et des absences', async () => {
    const root = await rendre();

    expect(root.querySelector('[data-testid="itdb-no-absence"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="itdb-plus-charge"]')).toBeNull();
  });

  it('recharge quand le centre actif change', async () => {
    await rendre();
    expect(api.alertes).toHaveBeenCalledTimes(1);

    centre.set('c-2');
    TestBed.tick();

    expect(api.alertes).toHaveBeenCalledTimes(2);
    expect(api.alertes).toHaveBeenLastCalledWith('c-2', 7);
  });
});
