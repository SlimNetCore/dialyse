import {provideZonelessChangeDetection, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {NgTableComponent} from '@sbourahla/ng-table';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {BackendApiService, DetailDeFacture, FactureListee, PageDeFactures} from '../../../core/api/backend-api.service';
import {OrganisationApiService} from '../../../core/api/organisation-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {dateCourte, FacturesListComponent, montant, statutDeSeance, tonDuReste, TONS_ETAT_FACTURE} from './factures-list.component';

const facture = (id: string, etat: FactureListee['etat']): FactureListee => ({
  factureId: id, numeroFacture: `FAC-${id}`, dateFacturation: '2026-11-05', periodeDebut: '2026-10-01',
  periodeFin: '2026-10-31', patientId: 'p1', numeroAssurance: '1', patientNom: 'BENALI', patientPrenom: 'Karim',
  caisseId: null, caisse: 'CNAS', nombreDeSeances: 13, tauxTva: 9, montantHt: 104000, montantTva: 9360,
  montantTtc: 113360, montantRegle: 50000, reste: 63360, tropPercu: 0, etat,
});
const PAGE: PageDeFactures = {
  items: [facture('f1', 'PARTIELLEMENT_REGLEE'), facture('f2', 'REGLEE')], total: 2, page: 0, size: 20,
  du: '2026-11-01', au: '2026-11-30',
  totaux: {nombreDeFactures: 2, montantHt: 208000, montantTva: 18720, montantTtc: 226720, montantRegle: 100000,
    reste: 126720},
};
const detail = (id: string): DetailDeFacture => ({
  factureId: id, numeroFacture: `FAC-${id}`, tauxTva: 9,
  lignes: [{forfaitId: 'fo1', forfaitLibelle: 'Hémodialyse standard', prixUnitaireHt: 8000, nombreDeSeances: 13,
    montantHt: 104000, montantTva: 9360, montantTtc: 113360}],
  seances: [
    {seanceId: 's1', dateSeance: '2026-10-02', statut: 'FACTUREE', forfaitId: 'fo1',
      forfaitLibelle: 'Hémodialyse standard', prixUnitaireHt: 8000},
    {seanceId: 's2', dateSeance: '2026-10-04', statut: 'FACTUREE', forfaitId: null, forfaitLibelle: null,
      prixUnitaireHt: null},
  ],
  totaux: {montantHt: 104000, montantTva: 9360, montantTtc: 113360, montantRegle: 50000, reste: 63360},
});

describe('FacturesListComponent', () => {
  let api: { listFactures: ReturnType<typeof vi.fn>; getFactureDetail: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<FacturesListComponent>;

  beforeEach(async () => {
    api = {
      listFactures: vi.fn(() => of(PAGE)),
      getFactureDetail: vi.fn((_c: string, id: string) => of(detail(id))),
    };
    TestBed.configureTestingModule({
      imports: [FacturesListComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        {provide: BackendApiService, useValue: api},
        {provide: AppShellStore, useValue: {currentCenterId: signal('c1')}},
        {provide: OrganisationApiService, useValue: {listerCaisses: () => of([{id: 'k1', nom: 'CNAS'}])}},
      ],
    });
    fixture = TestBed.createComponent(FacturesListComponent);
    await fixture.whenStable();
  });

  const host = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const table = (): NgTableComponent<FactureListee> =>
    fixture.debugElement.query(By.directive(NgTableComponent)).componentInstance;
  const cliquer = async (row: FactureListee): Promise<void> => {
    table().rowClick.emit(row);
    await fixture.whenStable();
  };

  it('charge la période courante du centre et affiche les totaux de toutes les factures filtrées', () => {
    expect(api.listFactures).toHaveBeenCalledWith('c1', expect.objectContaining({page: 0, size: 20}));
    const totaux = host().querySelector('[data-testid="factures-totaux"]')?.textContent ?? '';
    expect(totaux).toContain(montant(226720));
    expect(totaux).toContain(montant(126720));
  });

  it('montre l\'état de règlement en badge teinté et les montants au format dinar', () => {
    const texte = host().textContent ?? '';
    expect(texte).toContain('FACTURATION.FACTURES.ETAT.PARTIELLEMENT_REGLEE');
    expect(texte).toContain('FACTURATION.FACTURES.ETAT.REGLEE');
    expect(texte).toContain(montant(113360));
    const colonneEtat = table().columns().find((c) => c.id === 'etat');
    expect(colonneEtat?.cell).toMatchObject({type: 'badge', tone: TONS_ETAT_FACTURE});
    expect(TONS_ETAT_FACTURE).toEqual({NON_REGLEE: 'danger', PARTIELLEMENT_REGLEE: 'warning', REGLEE: 'success'});
  });

  it('un clic déroule la facture : séances avec leur forfait (« — » non attribuable), lignes et montants figés',
    async () => {
      await cliquer(PAGE.items[0]);

      expect(api.getFactureDetail).toHaveBeenCalledWith('c1', 'f1');
      const detailDom = host().querySelector('[data-testid="facture-detail"]');
      expect(detailDom).not.toBeNull();
      const seances = detailDom!.querySelectorAll('[data-testid="facture-detail-seances"] tbody tr');
      expect(seances).toHaveLength(2);
      expect(seances[0].textContent).toContain('Hémodialyse standard');
      expect(seances[0].textContent).toContain(montant(8000));
      expect(seances[1].textContent).toContain('FACTURATION.FACTURES.DETAIL.NON_ATTRIBUABLE');
      expect(seances[1].textContent).toContain('—');
      const aide = detailDom!.querySelector('[data-testid="facture-detail-non-attribuable"]');
      expect(aide?.textContent).toContain('FACTURATION.FACTURES.DETAIL.NON_ATTRIBUABLE_AIDE');
      expect(seances[1].querySelector('[aria-describedby]')?.getAttribute('aria-describedby')).toBe(aide?.id);
      expect(detailDom!.querySelector('[data-testid="facture-detail-lignes"]')?.textContent).toContain(montant(104000));
      expect(detailDom!.querySelector('[data-testid="facture-detail-totaux"]')?.textContent).toContain(montant(63360));
    });

  it('le détail occupe toute la largeur de la table et résume la facture en pastilles teintées et libellées',
    async () => {
      await cliquer(PAGE.items[0]);
      const detailDom = host().querySelector('[data-testid="facture-detail"]') as HTMLElement;
      const cellule = detailDom.closest('td')!;
      expect(cellule.classList).toContain('detail-row');
      expect(Number(cellule.getAttribute('colspan'))).toBeGreaterThanOrEqual(table().columns().length);
      expect(getComputedStyle(detailDom).maxWidth).toBe('none');
      detailDom.querySelectorAll('table').forEach((t) => expect(getComputedStyle(t).maxWidth).not.toMatch(/\d/));

      const etat = detailDom.querySelector('[data-testid="facture-detail-etat"]')!;
      expect(etat.getAttribute('data-tone')).toBe('warning');
      expect(etat.textContent).toContain('FACTURATION.FACTURES.ETAT.PARTIELLEMENT_REGLEE');
      const pastilles = detailDom.querySelectorAll('[data-testid="facture-detail-totaux"] .pastille');
      expect(pastilles).toHaveLength(5);
      pastilles.forEach((p) => expect(p.querySelector('dt')?.textContent?.trim()).toBeTruthy());
      expect(pastilles[1].textContent).toContain('(9 %)');
      const reste = detailDom.querySelector('[data-testid="facture-detail-reste"]')!;
      expect(reste.getAttribute('data-tone')).toBe('warning');
      expect(reste.textContent).toContain('FACTURATION.FACTURES.COL.RESTE');
      expect(detailDom.querySelector('[data-testid="facture-detail-statut"]')?.getAttribute('data-tone'))
        .toBe('success');
      expect(detailDom.querySelectorAll('[data-testid="facture-detail-lignes"] li.forfait')).toHaveLength(1);
    });

  it('les totaux de la période sont des pastilles ; la teinte du reste suit le règlement', () => {
    const totaux = host().querySelector('[data-testid="factures-totaux"]')!;
    expect(totaux.querySelectorAll('.pastille')).toHaveLength(6);
    expect(totaux.querySelector('[data-testid="factures-totaux-reste"]')?.getAttribute('data-tone')).toBe('warning');
    expect(tonDuReste(0, 100)).toBe('success');
    expect(tonDuReste(-5, 100)).toBe('success');
    expect(tonDuReste(100, 0)).toBe('danger');
    expect(tonDuReste(100, 50)).toBe('warning');
  });

  it('replier puis redéplier sert le détail du cache ; une seule facture dépliée à la fois', async () => {
    await cliquer(PAGE.items[0]);
    await cliquer(PAGE.items[0]);
    expect(table().expandedRowKeys()).toEqual([]);
    await cliquer(PAGE.items[0]);
    expect(api.getFactureDetail).toHaveBeenCalledTimes(1);

    await cliquer(PAGE.items[1]);
    expect(table().expandedRowKeys()).toEqual(['f2']);
    expect(host().querySelectorAll('[data-testid="facture-detail"]')).toHaveLength(1);
  });

  it('le bouton natif de dépli passe par le même état contrôlé', async () => {
    table().detailToggle.emit({row: PAGE.items[1], expanded: true, expandedKeys: ['f2']});
    await fixture.whenStable();
    expect(table().expandedRowKeys()).toEqual(['f2']);
    table().detailToggle.emit({row: PAGE.items[1], expanded: false, expandedKeys: []});
    await fixture.whenStable();
    expect(table().expandedRowKeys()).toEqual([]);
  });

  it('un raccourci « mois précédent » relance la page 0 sur ce mois', async () => {
    (host().querySelector('[data-testid="factures-mois-precedent"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    const derniere = api.listFactures.mock.calls.at(-1)![1];
    expect(derniere.page).toBe(0);
    expect(derniere.du < PAGE.du || derniere.du.endsWith('-01')).toBe(true);
  });

  it('pilote les filtres de la table : deux états choisis reviennent à un seul', async () => {
    (fixture.componentInstance as unknown as { onRemoteQueryChange(q: unknown): void }).onRemoteQueryChange(
      {sort: {columnId: '', direction: ''}, filters: {etat: 'REGLEE,NON_REGLEE'}, page: {index: 0, size: 20}});
    await fixture.whenStable();
    expect(table().filters()).toEqual({etat: 'NON_REGLEE'});
    expect(api.listFactures).toHaveBeenLastCalledWith('c1', expect.objectContaining({etat: 'NON_REGLEE'}));
  });

  it('formate dates, montants et statuts', () => {
    expect(dateCourte('2026-10-02')).toBe('02/10/2026');
    expect(dateCourte(null)).toBe('—');
    expect(montant(null)).toBe('—');
    expect(montant(1500)).toContain('1,500.00');
    expect(montant(1500)).toMatch(/DZD|DA|د/);
    expect(statutDeSeance('FACTUREE')).toBe('SEANCES.SEANCE_STATUTS.FACTUREE');
    expect(statutDeSeance('ANNULEE')).toBe('ANNULEE');
  });
});
