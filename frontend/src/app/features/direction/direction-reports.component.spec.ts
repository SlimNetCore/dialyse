import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {describe, expect, it, vi} from 'vitest';
import {DirectionReportsComponent} from './direction-reports.component';
import {lastCompleteMonths} from './direction.util';
import {DirectionStore} from './state/direction.store';

describe('DirectionReportsComponent — rapports mensuels', () => {
  async function render() {
    const mois = lastCompleteMonths(new Date(), 12);
    const report = vi.fn().mockResolvedValue(null);
    const base: Record<string, unknown> = {
      snapshots: signal([{mois: mois[0], generatedAt: '2026-09-01T08:00:00'}]), reportBusy: signal(null),
      reportError: signal(false), compareA: signal(null), compareB: signal(null), compareError: signal(false),
      overview: signal(null), report,
    };
    const store = new Proxy(base, {get: (target, key: string) => (key in target ? target[key] : () => null)});
    TestBed.configureTestingModule({
      imports: [DirectionReportsComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [provideZonelessChangeDetection(), {provide: DirectionStore, useValue: store}],
    });
    const fixture = TestBed.createComponent(DirectionReportsComponent);
    await fixture.whenStable();
    return {root: fixture.nativeElement as HTMLElement, mois, report};
  }

  it('propose le rapport PDF de chacun des douze derniers mois, dans la colonne d\'actions du tableau', async () => {
    const {root, mois, report} = await render();
    const table = root.querySelector('[data-testid="direction-reports-table"]')!;
    const rows = [...table.querySelectorAll<HTMLElement>('tr.data-row')];

    expect(rows).toHaveLength(12);
    // le bouton vient du gabarit de la page, conservé par le tableau pour la colonne d'actions
    const buttons = rows.map((tr) => tr.querySelector<HTMLButtonElement>('td:last-child button'));
    expect(buttons.map((b) => b?.getAttribute('data-testid'))).toEqual(mois.map((m) => `report-${m}`));
    expect(buttons[0]?.textContent).toContain('DIRECTION.REPORTS.DOWNLOAD');
    // état du mois : pastille figé / non figé
    expect(rows[0].querySelector('.ngt-badge')?.getAttribute('data-tone')).toBe('success');
    expect(rows[1].querySelector('.ngt-badge')?.getAttribute('data-tone')).toBe('neutral');

    buttons[0]!.click();
    expect(report).toHaveBeenCalledWith(mois[0]);
  });
});
