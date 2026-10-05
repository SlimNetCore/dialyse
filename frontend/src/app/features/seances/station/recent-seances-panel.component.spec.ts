import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {RecentSeancesPanelComponent} from './recent-seances-panel.component';
import {SeanceRecent} from '../../../core/api/backend-api.service';

describe('RecentSeancesPanelComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RecentSeancesPanelComponent, TranslateModule.forRoot()],
      providers: [provideZonelessChangeDetection()],
    });
  });

  function render(seances: SeanceRecent[]) {
    const fixture = TestBed.createComponent(RecentSeancesPanelComponent);
    fixture.componentRef.setInput('seances', seances);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche chaque séance passée avec ses poids, la perte, la tension et la durée', () => {
    const root = render([
      {
        seanceId: 'r1',
        dateSeance: '2026-10-02',
        status: 'VALIDEE',
        poidsAvantKg: 72,
        poidsApresKg: 69.5,
        taAvant: '12/8',
        dureeMinutes: 240
      },
    ]);

    const text = root.querySelector('.recent-item')!.textContent!;
    expect(text).toContain('2026-10-02');
    expect(text).toContain('72 → 69.5');
    expect(text).toContain('2.5');
    expect(text).toContain('12/8');
    expect(text).toContain('240');
  });

  it('place en tête la séance en cours avec les constantes saisies, sans doublon', () => {
    const fixture = TestBed.createComponent(RecentSeancesPanelComponent);
    fixture.componentRef.setInput('seances', [{seanceId: 'r1', dateSeance: '2026-10-02', status: 'VALIDEE'}]);
    fixture.componentRef.setInput('current', {
      seanceId: 's0', dateSeance: '2026-10-05', status: 'CREE', taAvant: '13/8', taApres: '12/7',
      dureeMinutes: 240, ultrafiltrationMl: 2500,
    });
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    const items = root.querySelectorAll('.recent-item');
    expect(items).toHaveLength(2);
    expect(items[0].classList.contains('live')).toBe(true);
    expect(items[0].textContent).toContain('13/8');
    expect(items[0].textContent).toContain('12/7');
    expect(items[0].textContent).toContain('2500');
    expect(items[1].classList.contains('live')).toBe(false);
  });

  it('remplace une valeur absente par un tiret', () => {
    const root = render([{seanceId: 'r1', dateSeance: '2026-10-02', status: 'CREE'}]);
    expect(root.querySelector('.recent-grid')!.textContent!.match(/–/g)!.length).toBeGreaterThanOrEqual(4);
  });

  it('invite à patienter quand il n\'y a aucune séance précédente', () => {
    const root = render([]);
    expect(root.querySelector('.empty')?.textContent).toContain('SEANCES.STATION.RECENT_EMPTY');
    expect(root.querySelector('.recent-item')).toBeNull();
  });
});
