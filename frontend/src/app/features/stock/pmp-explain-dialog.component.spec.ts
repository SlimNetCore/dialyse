import {provideZonelessChangeDetection} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {TestbedHarnessEnvironment} from '@angular/cdk/testing/testbed';
import {TranslateModule} from '@ngx-translate/core';
import {NgTableComponent} from '@sbourahla/ng-table';
import {NgTableHarness} from '@sbourahla/ng-table/testing';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {PmpExplainDialogComponent} from './pmp-explain-dialog.component';
import {PmpExplanationStep} from '../../core/api/stock-api.service';
import {PageDExplicationDuPmp, StockPagesApiService} from '../../core/api/stock-pages-api.service';

const step = (i: number): PmpExplanationStep => ({
  piece: `BR-${i}`, datePiece: '2026-11-02', type: i % 2 ? 'SORTIE' : 'ENTREE', quantite: 10, prixUnitaire: 2500,
  quantiteAvant: 0, valeurAvant: 0, pmpAvant: 0, quantiteApres: 10, valeurApres: 25000, pmpApres: 2500.12345,
  formule: '(0 + 25000) / 10',
});

const etapes = Array.from({length: 23}, (_, i) => step(i));
const pageDExplication = (page: number, size: number): PageDExplicationDuPmp => ({
  articleId: 'a1', code: 'EPO', libelle: 'Époétine', methode: 'PMP pondéré',
  items: etapes.slice(page * size, page * size + size), total: etapes.length, page, size,
  quantiteFinale: 10, valeurFinale: 25000, pmpFinal: 2500,
});

describe('PmpExplainDialogComponent (étapes en ng-table)', () => {
  let explicationDuPmp: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    explicationDuPmp = vi.fn((_c: string, _a: string, page: number, size: number) => of(pageDExplication(page, size)));
    await TestBed.configureTestingModule({
      imports: [PmpExplainDialogComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: StockPagesApiService, useValue: {explicationDuPmp}},
        {provide: MAT_DIALOG_DATA, useValue: {articleId: 'a1', centerId: 'c1', libelle: 'Époétine'}},
        {provide: MatDialogRef, useValue: {close: vi.fn()}},
      ],
    }).compileComponents();
  });

  it('montre la première page des étapes servie par le serveur, sans tri, avec le résumé', async () => {
    const fixture = TestBed.createComponent(PmpExplainDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(explicationDuPmp).toHaveBeenCalledWith('c1', 'a1', 0, 20);
    const ngTable: NgTableComponent<PmpExplanationStep> =
      fixture.debugElement.query(By.directive(NgTableComponent)).componentInstance;
    expect(ngTable.dataMode()).toBe('remote');
    expect(ngTable.totalCount()).toBe(23);
    expect(ngTable.paginator()).toBe(true);
    expect(ngTable.columns().map((c) => c.id)).toEqual(['piece', 'datePiece', 'type', 'qte', 'pu', 'pmp', 'formule']);
    expect(ngTable.columns().every((c) => c.sortable === false && !c.filter)).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('PMP pondéré');
    expect(ngTable.columns().find((c) => c.id === 'type')!.cell)
      .toEqual(expect.objectContaining({type: 'badge', tone: {ENTREE: 'success', SORTIE: 'warning', AJUSTEMENT: 'info'}}));

    const harness = await TestbedHarnessEnvironment.loader(fixture).getHarness(NgTableHarness);
    expect(await harness.getRowCount()).toBe(20);
    const first = (await harness.getCellTexts())[0];
    expect(first).toEqual(expect.arrayContaining(['BR-0', '02/11/2026', '2,500', '2,500.1235']));
  });

  it('demande la page suivante au serveur', async () => {
    const fixture = TestBed.createComponent(PmpExplainDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    (fixture.componentInstance as any).onRemoteQueryChange(
      {sort: {columnId: '', direction: ''}, filters: {}, page: {index: 1, size: 20}});
    expect(explicationDuPmp).toHaveBeenLastCalledWith('c1', 'a1', 1, 20);
    expect((fixture.componentInstance as any).liste.items().length).toBe(3);
  });
});
