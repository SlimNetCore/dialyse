import {CurrencyPipe, DecimalPipe} from '@angular/common';
import {Component, LOCALE_ID, provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {DirectionTableContext} from './direction-tables.util';
import {DirectionViewBase} from './direction-view.base';
import {DirectionStore} from './state/direction.store';

/** Page minimale : expose la mise en forme que la vue partagée fournit aux tableaux. */
@Component({standalone: true, template: ''})
class ProbeComponent extends DirectionViewBase {
  context(): DirectionTableContext {
    return this.tableContext();
  }
}

describe('DirectionViewBase — mise en forme des tableaux de la direction', () => {
  const overview = signal<{ seuilAnonymat: number } | null>({seuilAnonymat: 5});
  let probe: ProbeComponent;

  beforeEach(() => {
    overview.set({seuilAnonymat: 5});
    const store = new Proxy<Record<string, unknown>>({overview}, {
      get: (target, key: string) => (key in target ? target[key] : () => null),
    });
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideZonelessChangeDetection(), {provide: DirectionStore, useValue: store}],
    });
    probe = TestBed.createComponent(ProbeComponent).componentInstance;
  });

  it('écrit un montant en dinars sans décimales, comme le faisait le pipe currency des anciens tableaux', () => {
    const locale = TestBed.inject(LOCALE_ID);
    const pipe = new CurrencyPipe(locale);
    const ctx = probe.context();

    for (const value of [0, 950, 1_234_567.6, -42_000]) {
      expect(ctx.money(value)).toBe(pipe.transform(value, 'DZD', 'symbol-narrow', '1.0-0'));
    }
    expect(ctx.money(1_234_567.6)).toBe('DZD1,234,568');
    expect(ctx.money(null)).toBe('');
  });

  it('masque un effectif sous le seuil d\'anonymat publié par le serveur', () => {
    const ctx = probe.context();
    expect(ctx.threshold).toBe(5);
    expect(ctx.headcount(null)).toBe('< 5');
    expect(ctx.headcount(12)).toBe('12');

    overview.set({seuilAnonymat: 10});
    expect(probe.context().threshold).toBe(10);
    expect(probe.context().headcount(null)).toBe('< 10');

    // tableau de bord pas encore chargé : seuil par défaut
    overview.set(null);
    expect(probe.context().headcount(null)).toBe('< 5');
  });

  it('écrit nombres et dates à la locale de l\'application', () => {
    const locale = TestBed.inject(LOCALE_ID);
    const ctx = probe.context();

    expect(ctx.decimal(12.345, '1.0-1')).toBe(new DecimalPipe(locale).transform(12.345, '1.0-1'));
    expect(ctx.decimal(12.345, '1.0-1')).toBe('12.3');
    expect(ctx.dateTime('2026-09-01T08:05:00')).toBe('01/09/2026 08:05');
  });

  it('traduit les libellés et se renouvelle au changement de langue', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {DIRECTION: {COL: {RANG: 'Rank'}}});
    const before = probe.context();
    expect(before.t('DIRECTION.COL.RANG')).toBe('DIRECTION.COL.RANG');

    translate.use('en');
    const after = probe.context();
    expect(after).not.toBe(before);
    expect(after.t('DIRECTION.COL.RANG')).toBe('Rank');
  });
});
