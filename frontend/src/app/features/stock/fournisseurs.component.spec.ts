import {provideZonelessChangeDetection, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TestbedHarnessEnvironment} from '@angular/cdk/testing/testbed';
import {TranslateModule} from '@ngx-translate/core';
import {NgTableComponent} from '@sbourahla/ng-table';
import {NgTableHarness} from '@sbourahla/ng-table/testing';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {FournisseursComponent} from './fournisseurs.component';
import {Fournisseur, StockApiService} from '../../core/api/stock-api.service';
import {AuthStore} from '../../core/state/auth.store';

const fournisseurs: Fournisseur[] = Array.from({length: 25}, (_, i) => ({
  id: `f${i}`, centerId: 'c1', code: `F${String(i).padStart(2, '0')}`, raisonSociale: `Fournisseur ${i}`,
  contact: 'Mme Test', telephone: '0550', actif: true,
}));

describe('FournisseursComponent (liste en ng-table)', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    api = {
      listFournisseursPage: vi.fn((_c: string, p: Record<string, string>) => {
        const page = Number(p['page']);
        const size = Number(p['size']);
        return of({items: fournisseurs.slice(page * size, page * size + size), total: fournisseurs.length, page, size});
      }),
      createFournisseur: vi.fn().mockReturnValue(of(fournisseurs[0])),
    };
    await TestBed.configureTestingModule({
      imports: [FournisseursComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideZonelessChangeDetection(),
        {provide: StockApiService, useValue: api},
        {provide: AuthStore, useValue: {centerId: signal('c1')}},
        {provide: MatSnackBar, useValue: {open: vi.fn()}},
      ],
    }).compileComponents();
  });

  async function render() {
    const fixture = TestBed.createComponent(FournisseursComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('pagine les fournisseurs du centre côté serveur (GET /referentiel/fournisseurs/page), 20 par page', async () => {
    const fixture = await render();

    expect(api['listFournisseursPage']).toHaveBeenCalledWith('c1', expect.objectContaining({page: '0', size: '20'}));
    const ngTable: NgTableComponent<Fournisseur> =
      fixture.debugElement.query(By.directive(NgTableComponent)).componentInstance;
    expect(ngTable.dataMode()).toBe('remote');
    expect(ngTable.paginator()).toBe(true);
    expect(ngTable.pageSizeOptions()).toEqual([10, 20, 50, 100]);
    expect(ngTable.columns().map((c) => c.id)).toEqual(['code', 'raisonSociale', 'contact', 'telephone']);
    expect(ngTable.columns().find((c) => c.id === 'telephone')!.sortable).toBe(false);

    const harness = await TestbedHarnessEnvironment.loader(fixture).getHarness(NgTableHarness);
    expect(await harness.getRowCount()).toBe(20);
    const paginator = (await harness.getPaginator())!;
    await paginator.goToNextPage();
    expect(api['listFournisseursPage']).toHaveBeenLastCalledWith('c1', expect.objectContaining({page: '1', size: '20'}));
    expect(await harness.getRowCount()).toBe(5);
  });

  it('envoie tri, filtres de colonne et recherche (q) au serveur et repart de la page 0', async () => {
    const fixture = await render();
    const component = fixture.componentInstance as any;
    component.onRemoteQueryChange({
      sort: {columnId: 'raisonSociale', direction: 'desc'}, filters: {contact: 'mme', telephone: '05'},
      page: {index: 1, size: 20}, search: 'pharma',
    });
    expect(api['listFournisseursPage']).toHaveBeenLastCalledWith('c1', {
      page: '0', size: '20', sort: 'raisonSociale,desc', contact: 'mme', q: 'pharma',
    });
  });

  it('crée un fournisseur puis recharge la page courante', async () => {
    const fixture = await render();
    const appels = api['listFournisseursPage'].mock.calls.length;
    const input = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>('input')[1];
    input.value = 'Pharma Plus';
    input.dispatchEvent(new Event('input', {bubbles: true}));
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

    expect(api['createFournisseur']).toHaveBeenCalledWith(expect.objectContaining({centerId: 'c1', raisonSociale: 'Pharma Plus'}));
    expect(api['listFournisseursPage']).toHaveBeenCalledTimes(appels + 1);
  });
});
