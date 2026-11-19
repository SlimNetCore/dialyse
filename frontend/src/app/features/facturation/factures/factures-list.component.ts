import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  LOCALE_ID,
  signal,
  TemplateRef,
  untracked,
  viewChild,
} from '@angular/core';
import {formatCurrency, getCurrencySymbol} from '@angular/common';
import {toSignal} from '@angular/core/rxjs-interop';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {
  NgTableColumn,
  NgTableComponent,
  NgTableDetailToggleEvent,
  NgTableRemoteQuery,
  NgTableTone,
} from '@sbourahla/ng-table';
import {catchError, map, merge, of} from 'rxjs';
import {DetailDeFacture, FactureEtat, FactureListee} from '../../../core/api/backend-api.service';
import {OrganisationApiService} from '../../../core/api/organisation-api.service';
import {AppShellStore} from '../../../core/state/app-shell.store';
import {NgTableTranslatedLabels} from '../../direction/ng-table-labels';
import {FacturesListStore, moisDe, PeriodeDeFactures, periodeValide, TAILLES_DE_PAGE} from './factures-list.store';

/** Teinte de l'état de règlement (RG-FAC-046). */
export const TONS_ETAT_FACTURE: Record<FactureEtat, NgTableTone> = {
  NON_REGLEE: 'danger',
  PARTIELLEMENT_REGLEE: 'warning',
  REGLEE: 'success',
};
const ETATS: FactureEtat[] = ['NON_REGLEE', 'PARTIELLEMENT_REGLEE', 'REGLEE'];

/**
 * Montant en dinars (symbole DZD) à la locale de l'application, deux décimales, comme les écrans de la direction ;
 * `—` si absent.
 */
export function montant(valeur: number | null | undefined, locale = 'en-US'): string {
  if (valeur == null) return '—';
  return formatCurrency(valeur, locale, getCurrencySymbol('DZD', 'narrow', locale), 'DZD', '1.2-2');
}

/** `yyyy-MM-dd` ⇒ `dd/MM/yyyy`. */
export function dateCourte(iso: string | null | undefined): string {
  const [annee, mois, jour] = (iso ?? '').split('-');
  return annee && mois && jour ? `${jour}/${mois}/${annee}` : '—';
}

const STATUTS_TRADUITS: ReadonlySet<string> = new Set(['VALIDEE', 'SIGNEE', 'FACTUREE']);

/** Clé de traduction du statut d'une séance (statut brut s'il n'est pas traduit). */
export function statutDeSeance(statut: string): string {
  return STATUTS_TRADUITS.has(statut) ? `SEANCES.SEANCE_STATUTS.${statut}` : statut;
}

/**
 * Factures émises du centre sur une période (RG-FAC-046) : table paginée et filtrée côté serveur ; un clic sur une
 * facture la déroule pour montrer ses séances avec leur forfait, ses lignes figées et ses montants (RG-FAC-047/048).
 */
@Component({
  selector: 'app-factures-list',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, TranslateModule, NgTableComponent],
  providers: [FacturesListStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './factures-list.component.html',
  styleUrl: './factures-list.component.css',
})
export class FacturesListComponent {
  protected readonly store = inject(FacturesListStore);
  private readonly appShell = inject(AppShellStore);
  private readonly organisation = inject(OrganisationApiService);
  private readonly translate = inject(TranslateService);
  protected readonly ngTableLabels = inject(NgTableTranslatedLabels).labels;
  private readonly locale = inject(LOCALE_ID);

  protected readonly taillesDePage = TAILLES_DE_PAGE;
  protected readonly montant = (valeur: number | null | undefined): string => montant(valeur, this.locale);
  protected readonly dateCourte = dateCourte;
  protected readonly statutDeSeance = statutDeSeance;
  protected readonly cleDeFacture = (row: FactureListee): unknown => row.factureId;

  /** Bornes saisies dans la barre de période (appliquées quand elles forment une période valide). */
  protected readonly du = signal(this.store.periode().du);
  protected readonly au = signal(this.store.periode().au);
  protected readonly periodeInvalide = computed(() => !periodeValide({du: this.du(), au: this.au()}));

  protected readonly detailTemplate = viewChild.required<TemplateRef<{ $implicit: FactureListee; row: FactureListee }>>(
    'detailFacture');

  private readonly langue = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(map(() => Date.now())),
    {initialValue: 0});
  private readonly caisses = signal<{ value: string; label: string }[]>([]);

  protected readonly libelleVide = computed(() => {
    this.langue();
    return this.translate.instant('FACTURATION.FACTURES.EMPTY');
  });

  protected readonly colonnes = computed<NgTableColumn<FactureListee>[]>(() => {
    this.langue();
    const t = (cle: string): string => this.translate.instant(`FACTURATION.FACTURES.COL.${cle}`);
    const etat = (valeur: unknown): string => this.translate.instant(`FACTURATION.FACTURES.ETAT.${String(valeur)}`);
    return [
      {
        id: 'numeroFacture', header: t('NUMERO'), valueAccessor: (r) => r.numeroFacture, sortable: true,
        filter: {type: 'text', label: t('NUMERO')},
      },
      {id: 'dateFacturation', header: t('DATE'), valueAccessor: (r) => dateCourte(r.dateFacturation), sortable: true},
      {
        id: 'periode', header: t('PERIODE'),
        valueAccessor: (r) => `${dateCourte(r.periodeDebut)} – ${dateCourte(r.periodeFin)}`,
      },
      {
        id: 'patient', header: t('PATIENT'), valueAccessor: (r) => `${r.patientNom} ${r.patientPrenom}`.trim(),
        sortable: true, filter: {type: 'text', label: t('PATIENT')},
      },
      {
        id: 'caisse', header: t('CAISSE'), valueAccessor: (r) => r.caisse, sortable: true,
        filter: {type: 'enum', options: this.caisses(), label: t('CAISSE')},
      },
      {id: 'nombreDeSeances', header: t('SEANCES'), valueAccessor: (r) => r.nombreDeSeances, sortable: true},
      {id: 'montantHt', header: t('HT'), valueAccessor: (r) => this.montant(r.montantHt)},
      {
        id: 'montantTtc', header: t('TTC'), valueAccessor: (r) => this.montant(r.montantTtc), sortable: true,
        filter: {type: 'numberRange', label: t('TTC')},
      },
      {id: 'montantRegle', header: t('REGLE'), valueAccessor: (r) => this.montant(r.montantRegle), sortable: true},
      {id: 'reste', header: t('RESTE'), valueAccessor: (r) => this.montant(r.reste), sortable: true},
      {
        id: 'etat', header: t('ETAT'), valueAccessor: (r) => r.etat, sortable: true,
        cell: {type: 'badge', tone: TONS_ETAT_FACTURE, label: etat},
        filter: {type: 'enum', label: t('ETAT'), options: ETATS.map((e) => ({value: e, label: etat(e)}))},
      },
    ];
  });

  constructor() {
    effect(() => {
      const centre = this.appShell.currentCenterId();
      untracked(() => {
        this.store.changerDeCentre(centre);
        this.chargerCaisses(centre);
      });
    });
  }

  /** Détail chargé de la facture, sinon `null` (chargement ou erreur). */
  protected detailDe(factureId: string): DetailDeFacture | null {
    const etat = this.store.details().get(factureId);
    return etat?.etat === 'pret' ? etat.detail : null;
  }

  /** Au moins une séance du détail sans forfait attribuable (RG-FAC-048) : la note explicative est affichée. */
  protected aDesSeancesNonAttribuables(detail: DetailDeFacture): boolean {
    return detail.seances.some((s) => !s.forfaitLibelle);
  }

  /** Clé du message d'échec de lecture du détail, sinon `null`. */
  protected erreurDuDetail(factureId: string): string | null {
    const etat = this.store.details().get(factureId);
    return etat?.etat === 'erreur' ? etat.message : null;
  }

  protected onRemoteQueryChange(requete: NgTableRemoteQuery): void {
    this.store.appliquer(requete);
  }

  protected onRowClick(row: FactureListee): void {
    this.store.basculer(row.factureId);
  }

  protected onDetailToggle(event: NgTableDetailToggleEvent<FactureListee>): void {
    if (event.expanded && event.row) this.store.deplier(event.row.factureId);
    else this.store.replier();
  }

  protected changerDu(valeur: string): void {
    this.du.set(valeur);
    this.appliquerPeriode();
  }

  protected changerAu(valeur: string): void {
    this.au.set(valeur);
    this.appliquerPeriode();
  }

  /** Raccourci : mois courant (0) ou précédent (-1). */
  protected choisirMois(decalage: number): void {
    const periode = moisDe(new Date(), decalage);
    this.du.set(periode.du);
    this.au.set(periode.au);
    this.appliquerPeriode();
  }

  private appliquerPeriode(): void {
    const periode: PeriodeDeFactures = {du: this.du(), au: this.au()};
    if (periodeValide(periode)) this.store.changerDePeriode(periode);
  }

  private chargerCaisses(centre: string | null): void {
    if (!centre) {
      this.caisses.set([]);
      return;
    }
    this.organisation.listerCaisses(centre).pipe(catchError(() => of([]))).subscribe((caisses) =>
      this.caisses.set(caisses.map((c) => ({value: c.id, label: c.nom || c.libelle || c.code || c.id}))));
  }
}
