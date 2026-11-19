import {provideZonelessChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';
import {DirectionTableComponent} from './direction-table.component';
import {dataBarCell, DirectionColumn, NIVEAU_CAPACITE_TONES, RATE_CELL} from './direction-tables.util';

type Row = { nom: string; ca: number | null; taux: number | null; niveau: string; actif: boolean; total: boolean };

const columns: DirectionColumn<Row>[] = [
  {id: 'nom', header: 'Centre', valueAccessor: (r) => r.nom, sortable: true, cell: {type: 'avatar'}},
  {
    id: 'ca', header: 'CA', valueAccessor: (r) => r.ca, sortable: true, numeric: true, cell: dataBarCell(1000),
    text: (r) => (r.ca === null ? '< 5' : `${r.ca} DA`),
  },
  {
    id: 'taux', header: 'Taux', valueAccessor: (r) => r.taux, numeric: true, cell: RATE_CELL,
    text: (r) => (r.taux === null ? '—' : `${r.taux} %`), emphasis: (r) => ((r.taux ?? 0) >= 80 ? 'good' : 'bad'),
  },
  {id: 'niveau', header: 'Niveau', valueAccessor: (r) => r.niveau, cell: {type: 'badge', tone: NIVEAU_CAPACITE_TONES}},
];
const alpha: Row = {nom: 'Centre Alpha', ca: 1000, taux: 62.5, niveau: 'PROCHE', actif: true, total: false};
const beta: Row = {nom: 'Centre Beta', ca: null, taux: null, niveau: 'MARGE', actif: false, total: false};
const total: Row = {nom: 'Total', ca: 1000, taux: 85, niveau: 'MARGE', actif: true, total: true};

describe('DirectionTableComponent — tableau ng-table de la direction', () => {
  let fixture: ComponentFixture<DirectionTableComponent<Row>>;
  let host: HTMLElement;

  const rowOf = (text: string): HTMLElement =>
    [...host.querySelectorAll<HTMLElement>('tr.data-row')].find((tr) => tr.textContent?.includes(text))!;
  const cells = (tr: HTMLElement): HTMLElement[] => [...tr.querySelectorAll<HTMLElement>('td')];

  beforeEach(async () => {
    TestBed.configureTestingModule({imports: [DirectionTableComponent], providers: [provideZonelessChangeDetection()]});
    fixture = TestBed.createComponent(DirectionTableComponent<Row>);
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('rows', [alpha, beta]);
    fixture.componentRef.setInput('totals', [total]);
    fixture.componentRef.setInput('label', 'Activité par centre');
    fixture.componentRef.setInput('inactive', (r: Row) => !r.actif);
    await fixture.whenStable();
    host = fixture.nativeElement;
  });

  it('dessine les cellules ng-table déclarées par les colonnes, avec le texte mis en forme', () => {
    const [nom, ca, taux, niveau] = cells(rowOf('Centre Alpha'));

    expect(host.querySelector('table')?.getAttribute('aria-label')).toBe('Activité par centre');
    expect(nom.querySelector('.ngt-avatar')?.textContent?.trim()).toBe('CA');
    expect(ca.querySelector('.ngt-data-bar__value')?.textContent).toBe('1000 DA');
    expect(ca.querySelector<HTMLElement>('.ngt-data-bar__fill')?.style.width).toBe('100%');
    // taux : barre 0-100 de ng-table, valeur exacte écrite à côté, couleur du niveau
    expect(taux.querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')).toBe('63');
    expect(taux.querySelector('.ngt-progress__value')).toBeNull();
    expect(taux.querySelector('.dt-rate')?.textContent).toBe('62.5 %');
    expect(taux.querySelector('.dt-cell')?.classList).toContain('dt-bad');
    expect(niveau.querySelector('.ngt-badge')?.getAttribute('data-tone')).toBe('warning');
  });

  it('écrit une valeur masquée en texte simple, sans barre, et estompe un centre inactif', () => {
    const row = rowOf('Centre Beta');
    const [, ca, taux] = cells(row);

    expect(ca.textContent?.trim()).toBe('< 5');
    expect(ca.querySelector('ng-table-cell-format')).toBeNull();
    expect(taux.textContent?.trim()).toBe('—');
    expect(taux.querySelector('[role=progressbar]')).toBeNull();
    expect(row.classList).toContain('direction-inactive');
  });

  it('épingle la ligne de total en bas, en texte simple hors échelle des barres, le taux gardant sa barre', () => {
    const rows = [...host.querySelectorAll<HTMLElement>('tr.data-row')];
    const last = rows[rows.length - 1];
    const [nom, ca, taux] = cells(last);

    expect(rows).toHaveLength(3);
    expect(last.getAttribute('data-ngt-pin')).toBe('bottom');
    expect(last.classList).toContain('direction-total');
    expect(nom.textContent?.trim()).toBe('Total');
    expect(nom.querySelector('.ngt-avatar')).toBeNull();
    expect(ca.textContent?.trim()).toBe('1000 DA');
    expect(ca.querySelector('.ngt-data-bar__fill')).toBeNull();
    expect(taux.querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')).toBe('85');
  });

  it('n\'affiche ni menu des colonnes ni bouton de réinitialisation des filtres', () => {
    expect(host.querySelector('.list-actions button')).toBeNull();
  });
});
