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
import {PmpExplanation, PmpExplanationStep, StockApiService} from '../../core/api/stock-api.service';

const step = (i: number): PmpExplanationStep => ({
  piece: `BR-${i}`, datePiece: '2026-11-02', type: i % 2 ? 'SORTIE' : 'ENTREE', quantite: 10, prixUnitaire: 2500,
  quantiteAvant: 0, valeurAvant: 0, pmpAvant: 0, quantiteApres: 10, valeurApres: 25000, pmpApres: 2500.12345,
  formule: '(0 + 25000) / 10',
});

const explanation: PmpExplanation = {
  articleId: 'a1', code: 'EPO', libelle: 'Époétine', methode: 'PMP pondéré',
  etapes: Array.from({length: 23}, (_, i) => step(i)), quantiteFinale: 10, valeurFinale: 25000, pmpFinal: 2500,
};

describe('PmpExplainDialogComponent (étapes en ng-table)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PmpExplainDialogComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: StockApiService, useValue: {pmpExplain: vi.fn().mockReturnValue(of(explanation))}},
        {provide: MAT_DIALOG_DATA, useValue: {articleId: 'a1', centerId: 'c1', libelle: 'Époétine'}},
        {provide: MatDialogRef, useValue: {close: vi.fn()}},
      ],
    }).compileComponents();
  });

  it('montre les étapes du calcul paginées localement, type en pastille et montants formatés', async () => {
    const fixture = TestBed.createComponent(PmpExplainDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const ngTable: NgTableComponent<PmpExplanationStep> =
      fixture.debugElement.query(By.directive(NgTableComponent)).componentInstance;
    expect(ngTable.dataMode()).toBe('local');
    expect(ngTable.paginator()).toBe(true);
    expect(ngTable.columns().map((c) => c.id)).toEqual(['piece', 'datePiece', 'type', 'qte', 'pu', 'pmp', 'formule']);
    expect(ngTable.columns().find((c) => c.id === 'type')!.cell)
      .toEqual(expect.objectContaining({type: 'badge', tone: {ENTREE: 'success', SORTIE: 'warning', AJUSTEMENT: 'info'}}));

    const harness = await TestbedHarnessEnvironment.loader(fixture).getHarness(NgTableHarness);
    expect(await harness.getRowCount()).toBe(20);
    const first = (await harness.getCellTexts())[0];
    expect(first).toEqual(expect.arrayContaining(['BR-0', '02/11/2026', '2,500', '2,500.1235']));
  });
});
