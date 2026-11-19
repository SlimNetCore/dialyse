import {Component, computed, provideZonelessChangeDetection, TemplateRef, viewChild} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {beforeEach, describe, expect, it} from 'vitest';
import {DirectionTableComponent} from './direction-table.component';
import {
  dataBarCell,
  DirectionColumn,
  headcountSortValue,
  NIVEAU_CAPACITE_TONES,
  RANK_TONES,
  RATE_CELL,
} from './direction-tables.util';

type Row = { nom: string; ca: number | null; taux: number | null; niveau: string; actif: boolean; total: boolean };

const columns: DirectionColumn<Row>[] = [
  {id: 'nom', header: 'Centre', valueAccessor: (r) => r.nom, sortable: true, cell: {type: 'avatar'}},
  {
    id: 'ca', header: 'CA', valueAccessor: (r) => r.ca, sortable: true, numeric: true, cell: dataBarCell(1000),
    text: (r) => (r.ca === null ? '< 5' : `${r.ca} DA`), sortValueAccessor: (r) => headcountSortValue(r.ca, 5),
  },
  {
    id: 'taux', header: 'Taux', valueAccessor: (r) => r.taux, numeric: true, cell: RATE_CELL,
    text: (r) => (r.taux === null ? '—' : `${r.taux} %`), emphasis: (r) => ((r.taux ?? 0) >= 80 ? 'good' : 'bad'),
  },
  {id: 'niveau', header: 'Niveau', valueAccessor: (r) => r.niveau, cell: {type: 'badge', tone: NIVEAU_CAPACITE_TONES}},
];
const alpha: Row = {nom: 'Centre Alpha', ca: 1000, taux: 62.5, niveau: 'PROCHE', actif: true, total: false};
const beta: Row = {nom: 'Centre Beta', ca: null, taux: null, niveau: 'MARGE', actif: false, total: false};
const gamma: Row = {nom: 'Centre Gamma', ca: 3, taux: 130, niveau: 'ATTEINTE', actif: true, total: false};
const total: Row = {nom: 'Total', ca: 1000, taux: 85, niveau: 'MARGE', actif: true, total: true};

const dataRows = (host: HTMLElement): HTMLElement[] => [...host.querySelectorAll<HTMLElement>('tr.data-row')];
const rowOf = (host: HTMLElement, text: string): HTMLElement =>
  dataRows(host).find((tr) => tr.textContent?.includes(text))!;
const cells = (tr: HTMLElement): HTMLElement[] => [...tr.querySelectorAll<HTMLElement>('td')];

function configure(): void {
  TestBed.configureTestingModule({
    imports: [DirectionTableComponent, TranslateModule.forRoot()],
    providers: [provideZonelessChangeDetection()],
  });
}

describe('DirectionTableComponent — tableau ng-table de la direction', () => {
  let fixture: ComponentFixture<DirectionTableComponent<Row>>;
  let host: HTMLElement;

  async function render(rows: Row[], totals: Row[] = [total], cols: DirectionColumn<Row>[] = columns): Promise<void> {
    fixture = TestBed.createComponent(DirectionTableComponent<Row>);
    fixture.componentRef.setInput('columns', cols);
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('totals', totals);
    fixture.componentRef.setInput('label', 'Activité par centre');
    fixture.componentRef.setInput('inactive', (r: Row) => !r.actif);
    await fixture.whenStable();
    host = fixture.nativeElement;
  }

  beforeEach(async () => {
    configure();
    await render([alpha, beta]);
  });

  it('dessine les cellules ng-table déclarées par les colonnes, avec le texte mis en forme', () => {
    const [nom, ca, taux, niveau] = cells(rowOf(host, 'Centre Alpha'));

    expect(host.querySelector('table')?.getAttribute('aria-label')).toBe('Activité par centre');
    expect(nom.querySelector('.ngt-avatar')?.textContent?.trim()).toBe('CA');
    expect(ca.querySelector('.ngt-data-bar__value')?.textContent).toBe('1000 DA');
    expect(ca.querySelector<HTMLElement>('.ngt-data-bar__fill')?.style.width).toBe('100%');
    // taux : barre 0-100 de ng-table, valeur exacte écrite à côté, couleur du niveau
    expect(taux.querySelector('[role=progressbar]')).not.toBeNull();
    expect(taux.querySelector('.ngt-progress__value')).toBeNull();
    expect(taux.querySelector('.dt-rate')?.textContent).toBe('62.5 %');
    expect(taux.querySelector('.dt-cell')?.classList).toContain('dt-bad');
    expect(niveau.querySelector('.ngt-badge')?.getAttribute('data-tone')).toBe('warning');
  });

  it('écrit une valeur masquée en texte simple, sans barre, et estompe un centre inactif', () => {
    const row = rowOf(host, 'Centre Beta');
    const [, ca, taux] = cells(row);

    expect(ca.textContent?.trim()).toBe('< 5');
    expect(ca.querySelector('ng-table-cell-format')).toBeNull();
    expect(taux.textContent?.trim()).toBe('—');
    expect(taux.querySelector('[role=progressbar]')).toBeNull();
    expect(row.classList).toContain('direction-inactive');
  });

  it('épingle la ligne de total en bas, en texte simple hors échelle des barres, le taux gardant sa barre', () => {
    const rows = dataRows(host);
    const last = rows[rows.length - 1];
    const [nom, ca, taux] = cells(last);

    expect(rows).toHaveLength(3);
    expect(last.getAttribute('data-ngt-pin')).toBe('bottom');
    expect(last.classList).toContain('direction-total');
    expect(nom.textContent?.trim()).toBe('Total');
    expect(nom.querySelector('.ngt-avatar')).toBeNull();
    expect(ca.textContent?.trim()).toBe('1000 DA');
    expect(ca.querySelector('.ngt-data-bar__fill')).toBeNull();
    expect(taux.querySelector('[role=progressbar]')).not.toBeNull();
    expect(taux.querySelector('.dt-rate')?.textContent).toBe('85 %');
  });

  it('n\'affiche ni menu des colonnes ni bouton de réinitialisation des filtres', () => {
    expect(host.querySelector('.list-actions button')).toBeNull();
  });

  it('taux supérieur à 100 % : la barre bornée est masquée aux lecteurs d\'écran, qui lisent la valeur exacte', async () => {
    await render([alpha, gamma]);
    const [, , taux] = cells(rowOf(host, 'Centre Gamma'));
    const bar = taux.querySelector('[role=progressbar]')!;
    const rate = taux.querySelector('.dt-rate')!;

    // ng-table borne aria-valuenow à 100 : la barre n'est que décorative
    expect(bar.closest('[aria-hidden="true"]')?.tagName.toLowerCase()).toBe('ng-table-cell-format');
    expect(rate.textContent).toBe('130 %');
    expect(rate.closest('[aria-hidden]')).toBeNull();
    // les autres cellules prêtes à l'emploi restent lues
    const [nom, ca] = cells(rowOf(host, 'Centre Alpha'));
    expect(nom.querySelector('ng-table-cell-format')?.hasAttribute('aria-hidden')).toBe(false);
    expect(ca.querySelector('ng-table-cell-format')?.hasAttribute('aria-hidden')).toBe(false);
  });

  it('un seul centre affiché : pas de barre de données (elle serait toujours pleine), sauf échelle commune à plusieurs colonnes', async () => {
    await render([alpha]);
    let [, ca, taux] = cells(rowOf(host, 'Centre Alpha'));

    expect(ca.textContent?.trim()).toBe('1000 DA');
    expect(ca.querySelector('ng-table-cell-format')).toBeNull();
    expect(ca.querySelector('.dt-cell')?.classList).toContain('dt-num');
    // le taux, à bornes fixes 0-100, garde sa barre
    expect(taux.querySelector('[role=progressbar]')).not.toBeNull();

    await render([alpha], [total], columns.map((c) => (c.id === 'ca' ? {...c, sharedScale: true} : c)));
    [, ca, taux] = cells(rowOf(host, 'Centre Alpha'));
    expect(ca.querySelector('.ngt-data-bar__value')?.textContent).toBe('1000 DA');
  });

  it('trie un effectif masqué juste sous le seuil : premier en ordre croissant, dernier en ordre décroissant', async () => {
    await render([alpha, beta, gamma], []);
    const order = (): string[] => dataRows(host).map((tr) => cells(tr)[1].textContent!.trim());
    const sortButton = (): HTMLElement => host.querySelector<HTMLElement>('[aria-label^="CA, "]')!;

    sortButton().click();
    await fixture.whenStable();
    expect(order()).toEqual(['3 DA', '< 5', '1000 DA']);

    sortButton().click();
    await fixture.whenStable();
    expect(order()).toEqual(['1000 DA', '< 5', '3 DA']);
  });

  it('rang en pastille : le premier en vert, au-delà du podium en neutre', async () => {
    const rang: DirectionColumn<Row> = {
      id: 'rang', header: 'Rang', valueAccessor: (r) => (r === alpha ? 1 : 4), cell: {type: 'badge', tone: RANK_TONES},
    };
    await render([alpha, gamma], [], [rang, columns[0]]);
    const badge = (nom: string) => cells(rowOf(host, nom))[0].querySelector('.ngt-badge')!;

    expect(badge('Centre Alpha').textContent?.trim()).toBe('1');
    expect(badge('Centre Alpha').getAttribute('data-tone')).toBe('success');
    expect(badge('Centre Gamma').textContent?.trim()).toBe('4');
    expect(badge('Centre Gamma').getAttribute('data-tone')).toBe('neutral');
  });

  it('écrit les textes propres à ng-table dans la langue de l\'application, et suit un changement de langue', async () => {
    const translate = TestBed.inject(TranslateService);
    const sortLabel = () => host.querySelector('[aria-label^="CA, "]')?.getAttribute('aria-label');
    // sans langue choisie : le français livré par ng-table
    expect(sortLabel()).toBe('CA, Trier');

    // kabyle : pas de jeu livré par ng-table, les clés NG_TABLE.* du fichier de traduction l'emportent
    translate.setTranslation('kab', {NG_TABLE: {SORT: 'Smizzwer', NO_DATA: 'Ulac isefka'}});
    translate.use('kab');
    await fixture.whenStable();
    expect(sortLabel()).toBe('CA, Smizzwer');

    // anglais sans clé NG_TABLE : le jeu anglais livré par ng-table
    translate.setTranslation('en', {});
    translate.use('en');
    fixture.componentRef.setInput('rows', []);
    fixture.componentRef.setInput('totals', []);
    await fixture.whenStable();
    expect(sortLabel()).toBe('CA, Sort');
    expect(host.textContent).toContain('No data');

    translate.use('kab');
    await fixture.whenStable();
    expect(host.textContent).toContain('Ulac isefka');
  });
});

/** Page qui apporte son propre gabarit pour une colonne (boutons d'action), comme les rapports mensuels. */
@Component({
  standalone: true,
  imports: [DirectionTableComponent],
  template: `
    <app-direction-table [columns]="columns()" [rows]="rows"/>
    <ng-template #actions let-row>
      <button class="own-action" type="button">PDF {{ row.nom }}</button>
    </ng-template>
  `,
})
class OwnTemplateHostComponent {
  readonly rows = [alpha, gamma];
  private readonly actions = viewChild<TemplateRef<unknown>>('actions');
  readonly columns = computed<DirectionColumn<Row>[]>(() => [
    columns[0],
    {
      id: 'actions', header: 'Actions', valueAccessor: () => '', sortable: false,
      cellTemplate: this.actions() as DirectionColumn<Row>['cellTemplate'],
    },
  ]);
}

describe('DirectionTableComponent — colonne à gabarit propre', () => {
  it('garde le gabarit apporté par la colonne et n\'applique le gabarit commun qu\'aux autres', async () => {
    configure();
    const fixture = TestBed.createComponent(OwnTemplateHostComponent);
    await fixture.whenStable();
    const host: HTMLElement = fixture.nativeElement;
    const [nom, actions] = cells(rowOf(host, 'Centre Alpha'));

    expect(actions.querySelector('button.own-action')?.textContent?.trim()).toBe('PDF Centre Alpha');
    expect(actions.querySelector('.dt-cell')).toBeNull();
    expect(nom.querySelector('.dt-cell .ngt-avatar')).not.toBeNull();
    expect(host.querySelectorAll('button.own-action')).toHaveLength(2);
  });
});
