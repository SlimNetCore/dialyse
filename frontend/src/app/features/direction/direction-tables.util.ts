import {
  NgTableAvatarFormat,
  NgTableCellFormat,
  NgTableColorScaleFormat,
  NgTableColumn,
  NgTableDataBarFormat,
  NgTableProgressFormat,
  NgTableTone,
} from '@sbourahla/ng-table';
import {
  AgeRow,
  AnemieRow,
  CentreGmao,
  CentreIndicators,
  CentreStats,
  ClinicalIndicators,
  MoisAbsences,
  MotifAbsenceStat,
  SexeRow,
  Snapshot,
  StockIndicators,
} from '../../core/api/direction-api.service';
import {AbsencesRow, motifLabelKey, motifShare} from './direction-absences.util';
import {CaisseMetric, PivotCentre, PivotLine} from './direction-breakdown.util';
import {CapaciteRow} from './direction-capacite.util';
import {GmaoTopRow} from './direction-gmao.util';
import {StockGroupeRow} from './direction-stock-groupes.util';
import {collectionLevel, Delta, delta, formatPct, kdigoLevel} from './direction.util';

/** Couleur du texte d'une cellule : bon / à surveiller / mauvais (mêmes seuils qu'avant le passage à ng-table). */
export type CellEmphasis = 'good' | 'warn' | 'bad';

/**
 * Colonne d'un tableau de la direction : une colonne ng-table (`cell` = cellule prête à l'emploi de ng-table) et la
 * façon d'écrire sa valeur. ng-table affiche `String(valueAccessor(row))` ; `text` donne le texte réellement lu à
 * l'écran (montant en dinars, effectif masqué « < 5 », « — »…), la valeur brute restant celle du tri et des barres.
 */
export type DirectionColumn<T> = NgTableColumn<T> & {
  text?: (row: T) => string;
  emphasis?: (row: T) => CellEmphasis | null;
  /** Colonne de nombres : alignée à droite. */
  numeric?: boolean;
  strong?: boolean;
  /** Garde l'avatar sur une ligne de total (par défaut une ligne de total s'écrit en texte simple). */
  totalFormat?: boolean;
};

/** Mise en forme des valeurs, fournie par la page (langue, locale, seuil d'anonymat). */
export interface DirectionTableContext {
  t(key: string, params?: Record<string, unknown>): string;

  /** Effectif, « < seuil » quand il est masqué. */
  headcount(value: number | null): string;

  /** Montant en dinars sans décimales ; chaîne vide pour `null`. */
  money(value: number | null): string;

  /** Nombre au format de la locale (`digits` : format Angular, ex. `1.0-1`). */
  decimal(value: number, digits: string): string;

  dateTime(value: string): string;
}

export const AVATAR_CELL: NgTableAvatarFormat<unknown> = {type: 'avatar'};

/**
 * Taux en % : barre de progression à bornes fixes 0-100, pour que deux périodes ou deux centres se comparent. Le
 * pourcentage est écrit à côté par le tableau (valeur exacte, « 62.5 % »), pas arrondi par ng-table.
 */
export const RATE_CELL: NgTableProgressFormat = {type: 'progress', max: 100, showValue: false};

/** Variation d'un mois à l'autre, en % : dégradé à deux teintes centré sur zéro, saturé à ± 100 %. */
export const DELTA_CELL: NgTableColorScaleFormat = {type: 'colorScale', diverging: true, min: -100, max: 100};

/** Teinte du niveau d'occupation de la capacité théorique. */
export const NIVEAU_CAPACITE_TONES: Record<string, NgTableTone> = {
  MARGE: 'success',
  PROCHE: 'warning',
  ATTEINTE: 'danger',
  SANS_CAPACITE: 'neutral',
};

/** Teinte du rang d'un centre par chiffre d'affaires : le premier, puis les deux suivants. */
export const RANK_TONES: Record<string, NgTableTone> = {'1': 'success', '2': 'info', '3': 'info'};

/** Teinte de l'état d'un rapport mensuel. */
export const REPORT_STATUS_TONES: Record<string, NgTableTone> = {FROZEN: 'success', NOT_FROZEN: 'neutral'};

/** Barre de données de zéro à `max` (le maximum de la colonne, hors ligne de total). */
export function dataBarCell(max: number, tone?: NgTableTone): NgTableDataBarFormat {
  return tone ? {type: 'dataBar', min: 0, max, tone} : {type: 'dataBar', min: 0, max};
}

/** Plus grande valeur publiée (les effectifs masqués sont ignorés) ; 0 sans valeur. */
export function maxOf<T>(rows: readonly T[], ...accessors: ((row: T) => number | null | undefined)[]): number {
  let max = 0;
  for (const row of rows) {
    for (const accessor of accessors) {
      const value = accessor(row);
      if (typeof value === 'number' && Number.isFinite(value) && value > max) max = value;
    }
  }
  return max;
}

/** Ligne de total (société, ou total consolidé d'un groupe). */
export function isTotalRow(row: unknown): boolean {
  return !!row && (row as { total?: unknown }).total === true;
}

/** Texte affiché dans une cellule. */
export function cellTextOf<T>(column: DirectionColumn<T>, row: T): string {
  if (column.text) return column.text(row);
  const value = column.valueAccessor(row);
  return value === null || value === undefined ? '' : String(value);
}

/**
 * Cellule ng-table à dessiner pour une ligne, ou `null` pour un texte simple : pas de barre pour une valeur masquée,
 * ni pour une ligne de total (elle écraserait l'échelle des centres) ; pas de pastille vide.
 */
export function cellFormatOf<T>(
  column: DirectionColumn<T>, row: T, value: unknown, text: string,
): NgTableCellFormat<T> | null {
  const cell = column.cell;
  if (!cell) return null;
  const total = isTotalRow(row);
  switch (cell.type) {
    case 'avatar':
      return text === '' || (total && !column.totalFormat) ? null : cell;
    case 'dataBar':
    case 'colorScale':
      return total || typeof value !== 'number' ? null : cell;
    case 'progress':
      return typeof value === 'number' ? cell : null;
    case 'badge':
      return text === '' ? null : cell;
    default:
      return cell;
  }
}

/** « +4.2 % » / « -1.0 % », ou rien si la variation n'est pas calculable. */
export function formatDelta(d: Delta | null | undefined): string {
  if (!d || d.pct === null) return '';
  return `${d.pct > 0 ? '+' : ''}${d.pct.toFixed(1)} %`;
}

type Extra<T> = Partial<DirectionColumn<T>>;

/** Fabrique de colonnes d'un tableau : mêmes largeurs et mêmes cellules d'un tableau à l'autre. */
function kit<T>(sortable: boolean) {
  const col = (id: string, header: string, value: (row: T) => unknown, extra: Extra<T>): DirectionColumn<T> =>
    ({id, header, valueAccessor: value, sortable, ...extra});
  return {
    text: (id: string, header: string, value: (row: T) => unknown, extra: Extra<T> = {}) =>
      col(id, header, value, {minWidthPx: 130, ...extra}),
    avatar: (id: string, header: string, value: (row: T) => string, extra: Extra<T> = {}) =>
      col(id, header, value, {minWidthPx: 190, cell: AVATAR_CELL as NgTableAvatarFormat<T>, ...extra}),
    bar: (
      id: string, header: string, value: (row: T) => number | null, max: number, text: (row: T) => string,
      extra: Extra<T> & { tone?: NgTableTone } = {},
    ) => {
      const {tone, ...rest} = extra;
      return col(id, header, value, {numeric: true, minWidthPx: 120, cell: dataBarCell(max, tone), text, ...rest});
    },
    rate: (
      id: string, header: string, value: (row: T) => number | null, text: (row: T) => string, extra: Extra<T> = {},
    ) => col(id, header, value, {numeric: true, minWidthPx: 160, cell: RATE_CELL, text, ...extra}),
  };
}

const levelEmphasis = (level: 'good' | 'warn' | 'bad' | 'none'): CellEmphasis | null => (level === 'none' ? null : level);
const moneyOrDash = (ctx: DirectionTableContext, value: number | null): string => (value === null ? '—' : ctx.money(value));

/** Activité et finances par centre, classés par chiffre d'affaires. */
export function centresColumns(
  ctx: DirectionTableContext, rows: readonly CentreStats[],
  rankOf: (centerId: string | null) => number, caShare: (centerId: string | null) => number,
): DirectionColumn<CentreStats>[] {
  const k = kit<CentreStats>(true);
  const count = (id: 'seances' | 'factures', header: string) =>
    k.bar(id, ctx.t(header), (r) => r[id], maxOf(rows, (r) => r[id]), (r) => String(r[id]));
  const money = (id: 'caTtc' | 'encaisse' | 'resteARecouvrer', header: string, tone?: NgTableTone) =>
    k.bar(id, ctx.t(header), (r) => r[id], maxOf(rows, (r) => r[id]), (r) => ctx.money(r[id]), {tone, minWidthPx: 150});
  const headcount = (id: 'patients' | 'patientsSousKt', header: string) =>
    k.bar(id, ctx.t(header), (r) => r[id], maxOf(rows, (r) => r[id]), (r) => ctx.headcount(r[id]));
  return [
    {
      id: 'rang', header: '#', valueAccessor: (r) => rankOf(r.centerId), sortable: true, widthPx: 64, minWidthPx: 56,
      cell: {type: 'badge', tone: RANK_TONES},
    },
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.nom),
    headcount('patients', 'DIRECTION.KPI.PATIENTS'),
    headcount('patientsSousKt', 'DIRECTION.COL.SOUS_KT'),
    count('seances', 'DIRECTION.KPI.SEANCES'),
    count('factures', 'DIRECTION.COL.FACTURES'),
    money('caTtc', 'DIRECTION.KPI.CA_TTC'),
    money('encaisse', 'DIRECTION.KPI.ENCAISSE', 'success'),
    money('resteARecouvrer', 'DIRECTION.KPI.RESTE', 'warning'),
    k.rate('tauxEncaissement', ctx.t('DIRECTION.KPI.TAUX'), (r) => r.tauxEncaissement,
      (r) => formatPct(r.tauxEncaissement), {emphasis: (r) => levelEmphasis(collectionLevel(r.tauxEncaissement))}),
    k.rate('partCa', ctx.t('DIRECTION.COL.PART_CA'), (r) => caShare(r.centerId),
      (r) => `${ctx.decimal(caShare(r.centerId), '1.0-0')} %`),
  ];
}

/** Ligne des tableaux cliniques et de stock : un centre, ou le total de la société. */
export type IndicatorRow = { c: CentreIndicators; total: boolean };

const KDIGO_MARKERS: readonly ['ktV' | 'hemoglobine' | 'phosphore' | 'pth' | 'albumine', string][] = [
  ['ktV', 'KTV'], ['hemoglobine', 'HB'], ['phosphore', 'PHOSPHORE'], ['pth', 'PTH'], ['albumine', 'ALBUMINE'],
];

type ClinicalCount = 'vhbPositifs' | 'vhcPositifs' | 'vihPositifs' | 'patientsObservanceEnRetard'
  | 'greffeListeAttente' | 'greffesPeriode';

const CLINICAL_COUNTS: readonly [ClinicalCount, string, NgTableTone?][] = [
  ['vhbPositifs', 'VHB'], ['vhcPositifs', 'VHC'], ['vihPositifs', 'VIH'],
  ['patientsObservanceEnRetard', 'OBSERVANCE', 'warning'], ['greffeListeAttente', 'GREFFE_ATTENTE'],
  ['greffesPeriode', 'GREFFES', 'success'],
];

/** Indicateurs cliniques : part des patients dans la cible KDIGO par marqueur, puis effectifs. */
export function clinicalColumns(ctx: DirectionTableContext, rows: readonly IndicatorRow[]): DirectionColumn<IndicatorRow>[] {
  const k = kit<IndicatorRow>(true);
  const clinique = (r: IndicatorRow): ClinicalIndicators => r.c.clinique;
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => (r.total ? ctx.t('DIRECTION.TOTAL') : r.c.nom ?? '')),
    ...KDIGO_MARKERS.map(([marker, key]) => k.rate(marker, ctx.t(`DIRECTION.CLINICAL.${key}`),
      (r) => clinique(r)[marker].pctDansCible, (r) => formatPct(clinique(r)[marker].pctDansCible),
      {emphasis: (r) => levelEmphasis(kdigoLevel(clinique(r)[marker].pctDansCible))})),
    ...CLINICAL_COUNTS.map(([field, key, tone]) => k.bar(field, ctx.t(`DIRECTION.CLINICAL.${key}`),
      (r) => clinique(r)[field], maxOf(rows, (r) => clinique(r)[field]), (r) => ctx.headcount(clinique(r)[field]),
      {tone})),
  ];
}

/** Stock par centre : articles, alertes de seuil et de péremption, valeur. */
export function stockColumns(ctx: DirectionTableContext, rows: readonly IndicatorRow[]): DirectionColumn<IndicatorRow>[] {
  const k = kit<IndicatorRow>(true);
  const count = (
    field: Exclude<keyof StockIndicators, 'valeurStock'>, key: string, tone?: NgTableTone, emphasis?: CellEmphasis,
  ) => k.bar(field, ctx.t(`DIRECTION.STOCK.${key}`), (r) => r.c.stock[field], maxOf(rows, (r) => r.c.stock[field]),
    (r) => String(r.c.stock[field]),
    {tone, emphasis: emphasis ? (r) => (r.c.stock[field] > 0 ? emphasis : null) : undefined});
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => (r.total ? ctx.t('DIRECTION.TOTAL') : r.c.nom ?? '')),
    count('articlesActifs', 'ARTICLES'),
    count('articlesSousSeuil', 'SOUS_SEUIL', 'danger', 'bad'),
    count('lotsPerimes', 'PERIMES', 'danger', 'bad'),
    count('lotsPeremptionProche', 'PEREMPTION_PROCHE', 'warning', 'warn'),
    k.bar('valeurStock', ctx.t('DIRECTION.STOCK.VALEUR'), (r) => r.c.stock.valeurStock,
      maxOf(rows, (r) => r.c.stock.valeurStock), (r) => ctx.money(r.c.stock.valeurStock), {minWidthPx: 150}),
  ];
}

/** Ligne du tableau GMAO : un centre, ou le total de la société. */
export type GmaoRow = { c: CentreGmao; total: boolean };

type GmaoCount = 'nbEquipements' | 'nbHorsService' | 'nbEnMaintenance' | 'nbReformes' | 'interventionsEnCours'
  | 'patientsSurEquipementIndisponible' | 'nbReformeRecommandee';

/** GMAO par centre : parc, pannes, coût de maintenance, indisponibilité, patients exposés. */
export function gmaoColumns(ctx: DirectionTableContext, rows: readonly GmaoRow[]): DirectionColumn<GmaoRow>[] {
  const k = kit<GmaoRow>(true);
  const count = (field: GmaoCount, key: string, tone?: NgTableTone, emphasis?: CellEmphasis) =>
    k.bar(field, ctx.t(`DIRECTION.GMAO.${key}`), (r) => r.c[field], maxOf(rows, (r) => r.c[field]),
      (r) => String(r.c[field]), {tone, emphasis: emphasis ? (r) => (r.c[field] > 0 ? emphasis : null) : undefined});
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => (r.total ? ctx.t('DIRECTION.TOTAL') : r.c.nom ?? '')),
    count('nbEquipements', 'NB_EQUIPEMENTS'),
    count('nbHorsService', 'HORS_SERVICE', 'danger', 'bad'),
    count('nbEnMaintenance', 'EN_MAINTENANCE', 'warning', 'warn'),
    count('nbReformes', 'REFORMES', 'neutral'),
    count('interventionsEnCours', 'INTERVENTIONS_EN_COURS'),
    k.bar('coutMaintenancePeriode', ctx.t('DIRECTION.GMAO.COUT_MAINTENANCE'), (r) => r.c.coutMaintenancePeriode,
      maxOf(rows, (r) => r.c.coutMaintenancePeriode), (r) => ctx.money(r.c.coutMaintenancePeriode), {minWidthPx: 150}),
    k.bar('indisponibiliteHeuresCumulees', ctx.t('DIRECTION.GMAO.INDISPONIBILITE'),
      (r) => r.c.indisponibiliteHeuresCumulees, maxOf(rows, (r) => r.c.indisponibiliteHeuresCumulees),
      (r) => `${ctx.decimal(r.c.indisponibiliteHeuresCumulees, '1.0-1')} h`, {tone: 'warning'}),
    count('patientsSurEquipementIndisponible', 'PATIENTS_A_RISQUE', 'danger', 'bad'),
    count('nbReformeRecommandee', 'A_REFORMER', 'warning', 'warn'),
  ];
}

/** Équipements les plus coûteux : coûts, ratio maintenance / prix d'achat, signal de réforme. */
export function gmaoTopColumns(ctx: DirectionTableContext, rows: readonly GmaoTopRow[]): DirectionColumn<GmaoTopRow>[] {
  const k = kit<GmaoTopRow>(true);
  const ratio = (r: GmaoTopRow): number | null => (r.e.ratioMaintenance == null ? null : r.e.ratioMaintenance * 100);
  const aReformer = ctx.t('DIRECTION.GMAO.A_REFORMER');
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.centre ?? ''),
    k.text('equipement', ctx.t('DIRECTION.GMAO.COL_EQUIPEMENT'), (r) => `${r.e.code} — ${r.e.designation}`,
      {minWidthPx: 220}),
    k.bar('coutPeriode', ctx.t('DIRECTION.GMAO.COL_COUT_PERIODE'), (r) => r.e.coutPeriode,
      maxOf(rows, (r) => r.e.coutPeriode), (r) => ctx.money(r.e.coutPeriode), {minWidthPx: 150}),
    k.bar('coutCumule', ctx.t('DIRECTION.GMAO.COL_COUT_CUMULE'), (r) => r.e.coutCumule,
      maxOf(rows, (r) => r.e.coutCumule), (r) => ctx.money(r.e.coutCumule), {minWidthPx: 150, tone: 'neutral'}),
    k.rate('ratioMaintenance', ctx.t('DIRECTION.GMAO.COL_RATIO'), ratio,
      (r) => (ratio(r) === null ? '—' : `${ctx.decimal(ratio(r)!, '1.0-0')} %`),
      {emphasis: (r) => (r.e.reformeRecommandee ? 'warn' : null)}),
    k.bar('indisponibiliteHeures', ctx.t('DIRECTION.GMAO.COL_INDISPONIBILITE'), (r) => r.e.indisponibiliteHeures,
      maxOf(rows, (r) => r.e.indisponibiliteHeures), (r) => `${ctx.decimal(r.e.indisponibiliteHeures, '1.0-1')} h`,
      {tone: 'warning'}),
    k.text('reformeRecommandee', aReformer, (r) => r.e.reformeRecommandee,
      {cell: {type: 'badge', tone: {true: 'warning'}}, text: (r) => (r.e.reformeRecommandee ? aReformer : '')}),
  ];
}

/** Capacité théorique par centre : générateurs, postes, capacité, file active, occupation et niveau. */
export function capaciteColumns(ctx: DirectionTableContext, rows: readonly CapaciteRow[]): DirectionColumn<CapaciteRow>[] {
  const k = kit<CapaciteRow>(true);
  const count = (field: 'generateurs' | 'generateursSecours' | 'postesActifs', key: string, tone?: NgTableTone) =>
    k.bar(field, ctx.t(`DIRECTION.CAPACITE.${key}`), (r) => r.ligne[field], maxOf(rows, (r) => r.ligne[field]),
      (r) => String(r.ligne[field]), {tone});
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.nom ?? ctx.t('DIRECTION.TOTAL')),
    count('generateurs', 'COL_GENERATEURS'),
    count('generateursSecours', 'COL_SECOURS', 'neutral'),
    count('postesActifs', 'COL_POSTES'),
    k.text('series', ctx.t('DIRECTION.CAPACITE.COL_SERIES'), (r) => (r.total ? null : r.ligne.series),
      {numeric: true, minWidthPx: 90, text: (r) => (r.total ? '—' : String(r.ligne.series))}),
    k.bar('capacite', ctx.t('DIRECTION.CAPACITE.COL_CAPACITE'), (r) => r.ligne.capacite,
      maxOf(rows, (r) => r.ligne.capacite), (r) => (r.ligne.capacite === null ? '—' : String(r.ligne.capacite)),
      {strong: true}),
    k.bar('fileActive', ctx.t('DIRECTION.CAPACITE.COL_FILE'), (r) => r.ligne.fileActive,
      maxOf(rows, (r) => r.ligne.fileActive), (r) => ctx.headcount(r.ligne.fileActive)),
    k.rate('tauxOccupation', ctx.t('DIRECTION.CAPACITE.COL_TAUX'), (r) => r.ligne.tauxOccupation,
      (r) => formatPct(r.ligne.tauxOccupation)),
    k.text('niveau', ctx.t('DIRECTION.CAPACITE.COL_STATUT'), (r) => r.ligne.niveau, {
      cell: {type: 'badge', tone: NIVEAU_CAPACITE_TONES},
      text: (r) => (r.ligne.niveau === null ? '—' : ctx.t(`DIRECTION.CAPACITE.NIVEAU.${r.ligne.niveau}`)),
    }),
  ];
}

type AbsencesCount = 'nbAbsences' | 'nbJustifiees' | 'nbNonJustifiees' | 'nbAQualifier' | 'nbRattrapees';

/** Absences de patients par centre : effectifs, taux d'absentéisme, valorisation et part du CA HT. */
export function absencesColumns(ctx: DirectionTableContext, rows: readonly AbsencesRow[]): DirectionColumn<AbsencesRow>[] {
  const k = kit<AbsencesRow>(true);
  const count = (field: AbsencesCount, key: string, extra: Extra<AbsencesRow> & { tone?: NgTableTone } = {}) =>
    k.bar(field, ctx.t(`DIRECTION.ABSENCES.${key}`), (r) => r.stats[field], maxOf(rows, (r) => r.stats[field]),
      (r) => ctx.headcount(r.stats[field]), extra);
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.nom ?? ctx.t('DIRECTION.TOTAL')),
    count('nbAbsences', 'COL_NB'),
    count('nbJustifiees', 'COL_JUSTIFIEES', {tone: 'success'}),
    count('nbNonJustifiees', 'COL_NON_JUSTIFIEES', {tone: 'danger'}),
    count('nbAQualifier', 'COL_A_QUALIFIER',
      {tone: 'warning', emphasis: (r) => ((r.stats.nbAQualifier ?? 0) > 0 ? 'warn' : null)}),
    count('nbRattrapees', 'COL_RATTRAPEES', {tone: 'neutral'}),
    k.rate('tauxAbsenteisme', ctx.t('DIRECTION.ABSENCES.COL_TAUX'), (r) => r.stats.tauxAbsenteisme,
      (r) => formatPct(r.stats.tauxAbsenteisme)),
    k.bar('valorisationHt', ctx.t('DIRECTION.ABSENCES.COL_VALORISATION'), (r) => r.stats.valorisationHt,
      maxOf(rows, (r) => r.stats.valorisationHt), (r) => moneyOrDash(ctx, r.stats.valorisationHt),
      {minWidthPx: 150, tone: 'warning'}),
    k.bar('caHt', ctx.t('DIRECTION.ABSENCES.COL_CA_HT'), (r) => r.stats.caHt, maxOf(rows, (r) => r.stats.caHt),
      (r) => ctx.money(r.stats.caHt), {minWidthPx: 150}),
    k.rate('partCaHt', ctx.t('DIRECTION.ABSENCES.COL_PART_CA'), (r) => r.stats.partCaHt,
      (r) => formatPct(r.stats.partCaHt), {strong: true}),
  ];
}

/** Absences par motif : effectif, part dans l'ensemble et valorisation. */
export function motifsColumns(
  ctx: DirectionTableContext, motifs: MotifAbsenceStat[],
): DirectionColumn<MotifAbsenceStat>[] {
  const k = kit<MotifAbsenceStat>(true);
  return [
    k.text('motif', ctx.t('DIRECTION.ABSENCES.MOTIF'), (m) => ctx.t(motifLabelKey(m.motif)), {
      minWidthPx: 200,
      cell: {type: 'badge', tone: (_value, m) => (m.motif === 'NON_QUALIFIE' ? 'warning' : 'info')},
    }),
    k.bar('nb', ctx.t('DIRECTION.ABSENCES.COL_NB'), (m) => m.nb, maxOf(motifs, (m) => m.nb),
      (m) => ctx.headcount(m.nb)),
    k.rate('part', ctx.t('DIRECTION.ABSENCES.COL_PART'), (m) => motifShare(m, motifs),
      (m) => formatPct(motifShare(m, motifs))),
    k.bar('valorisationHt', ctx.t('DIRECTION.ABSENCES.COL_VALORISATION'), (m) => m.valorisationHt,
      maxOf(motifs, (m) => m.valorisationHt), (m) => moneyOrDash(ctx, m.valorisationHt),
      {minWidthPx: 150, tone: 'warning'}),
  ];
}

/** Absences mois par mois. */
export function absencesMensuelColumns(
  ctx: DirectionTableContext, rows: readonly MoisAbsences[],
): DirectionColumn<MoisAbsences>[] {
  const k = kit<MoisAbsences>(true);
  return [
    k.text('mois', ctx.t('DIRECTION.REPORTS.MONTH'), (m) => `${m.annee}-${m.mois < 10 ? '0' + m.mois : m.mois}`),
    k.bar('nbAbsences', ctx.t('DIRECTION.ABSENCES.COL_NB'), (m) => m.nbAbsences, maxOf(rows, (m) => m.nbAbsences),
      (m) => ctx.headcount(m.nbAbsences)),
    k.bar('valorisationHt', ctx.t('DIRECTION.ABSENCES.COL_VALORISATION'), (m) => m.valorisationHt,
      maxOf(rows, (m) => m.valorisationHt), (m) => moneyOrDash(ctx, m.valorisationHt),
      {minWidthPx: 150, tone: 'warning'}),
  ];
}

/**
 * Valorisation du stock par groupe d'articles. Le total consolidé d'un groupe précède le détail de ses centres :
 * les colonnes ne sont pas triables, un tri séparerait un total de son détail. Les barres se mesurent sur les lignes
 * de centre ; « autres variations » est signé, d'où le dégradé centré sur zéro.
 */
export function stockGroupesColumns(
  ctx: DirectionTableContext, rows: readonly StockGroupeRow[],
): DirectionColumn<StockGroupeRow>[] {
  const k = kit<StockGroupeRow>(false);
  const detail = rows.filter((r) => !r.total);
  const money = (
    field: 'valeurDebut' | 'entrees' | 'sorties' | 'valeurFin', key: string,
    extra: Extra<StockGroupeRow> & { tone?: NgTableTone } = {},
  ) => k.bar(field, ctx.t(`DIRECTION.STOCK_GROUPES.${key}`), (r) => r.valeurs[field],
    maxOf(detail, (r) => r.valeurs[field]), (r) => ctx.money(r.valeurs[field]), {minWidthPx: 150, ...extra});
  const variation = maxOf(detail, (r) => Math.abs(r.valeurs.autresVariations));
  return [
    k.avatar('groupe', ctx.t('DIRECTION.STOCK_GROUPES.GROUPE'), (r) => r.nom, {totalFormat: true}),
    k.text('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.centre ?? ctx.t('DIRECTION.TOTAL'), {minWidthPx: 160}),
    k.bar('nbArticles', ctx.t('DIRECTION.STOCK_GROUPES.ARTICLES'), (r) => r.nbArticles,
      maxOf(detail, (r) => r.nbArticles), (r) => String(r.nbArticles)),
    money('valeurDebut', 'DEBUT', {tone: 'neutral'}),
    money('entrees', 'ENTREES', {tone: 'success'}),
    money('sorties', 'SORTIES', {tone: 'warning'}),
    k.text('autresVariations', ctx.t('DIRECTION.STOCK_GROUPES.AUTRES'), (r) => r.valeurs.autresVariations, {
      numeric: true, minWidthPx: 150,
      cell: {type: 'colorScale', diverging: true, min: -variation, max: variation},
      text: (r) => ctx.money(r.valeurs.autresVariations),
      emphasis: (r) => (r.valeurs.autresVariations < 0 ? 'warn' : null),
    }),
    money('valeurFin', 'FIN', {strong: true}),
  ];
}

/** Patients par sexe et par centre : les trois colonnes partagent la même échelle. */
export function sexeColumns(ctx: DirectionTableContext, rows: readonly SexeRow[]): DirectionColumn<SexeRow>[] {
  const k = kit<SexeRow>(true);
  const max = maxOf(rows, (r) => r.masculin, (r) => r.feminin, (r) => r.autre);
  const count = (field: 'masculin' | 'feminin' | 'autre', key: string) =>
    k.bar(field, ctx.t(`DIRECTION.BREAKDOWN.${key}`), (r) => r[field], max, (r) => ctx.headcount(r[field]));
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.nom),
    count('masculin', 'MASCULIN'), count('feminin', 'FEMININ'), count('autre', 'AUTRE'),
  ];
}

/** Patients par tranche d'âge et par centre : toutes les tranches partagent la même échelle. */
export function agesColumns(
  ctx: DirectionTableContext, rows: readonly AgeRow[], codes: readonly string[],
): DirectionColumn<AgeRow>[] {
  const k = kit<AgeRow>(true);
  const countOf = (r: AgeRow, code: string): number | null => r.tranches.find((t) => t.code === code)?.count ?? null;
  const max = maxOf(rows, ...codes.map((code) => (r: AgeRow) => countOf(r, code)));
  // Une tranche absente de la ligne n'était pas affichée : cellule vide, pas « < seuil ».
  const text = (r: AgeRow, code: string): string => {
    const tranche = r.tranches.find((t) => t.code === code);
    return tranche ? ctx.headcount(tranche.count) : '';
  };
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.nom),
    ...codes.map((code) => k.bar(`age_${code}`, ctx.t(`DIRECTION.BREAKDOWN.AGE.${code}`), (r) => countOf(r, code), max,
      (r) => text(r, code))),
  ];
}

/**
 * Tableau croisé caisse × centre pour la mesure choisie : une colonne par centre (même échelle entre centres), puis
 * le total de la société sur sa propre échelle.
 */
export function caissesColumns(
  ctx: DirectionTableContext, centres: readonly PivotCentre[], lines: readonly PivotLine[], metric: CaisseMetric,
): DirectionColumn<PivotLine>[] {
  const k = kit<PivotLine>(true);
  const text = (value: number | null | undefined): string => {
    if (metric === 'caHt') return ctx.money(value ?? null);
    if (metric === 'patients') return ctx.headcount(value ?? null);
    return value === null || value === undefined ? '' : String(value);
  };
  const max = maxOf(lines, ...centres.map((c) => (l: PivotLine) => l.cells[c.id]));
  const minWidthPx = metric === 'caHt' ? 150 : 120;
  return [
    k.avatar('caisse', ctx.t('DIRECTION.BREAKDOWN.CAISSE'),
      (l) => l.nom || ctx.t('DIRECTION.BREAKDOWN.CAISSE_INCONNUE')),
    ...centres.map((c) => k.bar(`centre_${c.id}`, c.nom, (l) => l.cells[c.id] ?? null, max,
      (l) => text(l.cells[c.id]), {minWidthPx})),
    k.bar('total', ctx.t('DIRECTION.TOTAL'), (l) => l.total, maxOf(lines, (l) => l.total), (l) => text(l.total),
      {minWidthPx, strong: true, tone: 'neutral'}),
  ];
}

/** Traitement de l'anémie par centre : patients sous EPO / fer, administrations et taux d'administration. */
export function anemieColumns(ctx: DirectionTableContext, rows: readonly AnemieRow[]): DirectionColumn<AnemieRow>[] {
  const k = kit<AnemieRow>(true);
  const headcount = (field: 'patientsSousEpo' | 'patientsSousFer' | 'patientsEpo' | 'patientsFer', key: string) =>
    k.bar(field, ctx.t(`DIRECTION.BREAKDOWN.${key}`), (r) => r[field], maxOf(rows, (r) => r[field]),
      (r) => ctx.headcount(r[field]));
  const count = (
    field: 'administreesEpo' | 'administreesFer' | 'nonAdministrees', key: string,
    extra: Extra<AnemieRow> & { tone?: NgTableTone } = {},
  ) => k.bar(field, ctx.t(`DIRECTION.BREAKDOWN.${key}`), (r) => r[field], maxOf(rows, (r) => r[field]),
    (r) => String(r[field]), extra);
  return [
    k.avatar('centre', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.nom),
    headcount('patientsSousEpo', 'SOUS_EPO'),
    headcount('patientsSousFer', 'SOUS_FER'),
    headcount('patientsEpo', 'PAT_EPO'),
    headcount('patientsFer', 'PAT_FER'),
    count('administreesEpo', 'ADM_EPO', {tone: 'success'}),
    count('administreesFer', 'ADM_FER', {tone: 'success'}),
    count('nonAdministrees', 'NON_ADM', {tone: 'danger', emphasis: (r) => (r.nonAdministrees > 0 ? 'bad' : null)}),
    k.rate('tauxAdministration', ctx.t('DIRECTION.BREAKDOWN.TAUX_ADM'), (r) => r.tauxAdministration,
      (r) => formatPct(r.tauxAdministration)),
  ];
}

/** Mois d'un rapport mensuel, avec la date de son instantané s'il est figé. */
export type ReportRow = { mois: string; generatedAt: string | null };

/** Rapports mensuels : mois et état (figé ou non) ; la colonne d'actions est ajoutée par la page. */
export function reportColumns(ctx: DirectionTableContext): DirectionColumn<ReportRow>[] {
  const k = kit<ReportRow>(false);
  return [
    k.text('mois', ctx.t('DIRECTION.REPORTS.MONTH'), (r) => r.mois),
    k.text('statut', ctx.t('DIRECTION.REPORTS.STATUS'), (r) => (r.generatedAt ? 'FROZEN' : 'NOT_FROZEN'), {
      minWidthPx: 240,
      cell: {type: 'badge', tone: REPORT_STATUS_TONES},
      text: (r) => (r.generatedAt
        ? `${ctx.t('DIRECTION.REPORTS.FROZEN_ON')} ${ctx.dateTime(r.generatedAt)}`
        : ctx.t('DIRECTION.REPORTS.NOT_FROZEN')),
    }),
  ];
}

/** Ligne du comparateur : un indicateur, ses valeurs des deux mois figés et leur variation (`null` : non calculée). */
export type CompareRow = { id: string; label: string; a: string; b: string; delta: Delta | null };

/** Indicateurs société des deux mois figés choisis (activité et finances), B comparé à A. */
export function compareRows(ctx: DirectionTableContext, a: Snapshot, b: Snapshot): CompareRow[] {
  const ta = a.overview.totaux;
  const tb = b.overview.totaux;
  return [
    {
      id: 'patients', label: ctx.t('DIRECTION.KPI.PATIENTS'), a: ctx.headcount(ta.patients),
      b: ctx.headcount(tb.patients), delta: null,
    },
    {
      id: 'seances', label: ctx.t('DIRECTION.KPI.SEANCES'), a: String(ta.seances), b: String(tb.seances),
      delta: delta(tb.seances, ta.seances),
    },
    {
      id: 'caTtc', label: ctx.t('DIRECTION.KPI.CA_TTC'), a: ctx.money(ta.caTtc), b: ctx.money(tb.caTtc),
      delta: delta(tb.caTtc, ta.caTtc),
    },
    {
      id: 'encaisse', label: ctx.t('DIRECTION.KPI.ENCAISSE'), a: ctx.money(ta.encaisse), b: ctx.money(tb.encaisse),
      delta: delta(tb.encaisse, ta.encaisse),
    },
    {
      id: 'tauxEncaissement', label: ctx.t('DIRECTION.KPI.TAUX'), a: formatPct(ta.tauxEncaissement),
      b: formatPct(tb.tauxEncaissement), delta: null,
    },
  ];
}

/**
 * Comparateur de deux mois : les lignes mêlent effectifs, montants et taux, donc pas de barre sur les valeurs ;
 * seule la variation, en % pour toutes, porte le dégradé centré sur zéro.
 */
export function compareColumns(ctx: DirectionTableContext, moisA: string, moisB: string): DirectionColumn<CompareRow>[] {
  const k = kit<CompareRow>(false);
  return [
    k.text('indicateur', ctx.t('DIRECTION.COL.CENTRE'), (r) => r.label, {minWidthPx: 180}),
    k.text('a', moisA, (r) => r.a, {numeric: true, minWidthPx: 150}),
    k.text('b', moisB, (r) => r.b, {numeric: true, minWidthPx: 150}),
    k.text('delta', ctx.t('DIRECTION.COMPARE.DELTA'), (r) => r.delta?.pct ?? null, {
      numeric: true, strong: true, minWidthPx: 130, cell: DELTA_CELL,
      text: (r) => formatDelta(r.delta) || '—',
      emphasis: (r) => (r.delta?.direction === 'up' ? 'good' : r.delta?.direction === 'down' ? 'bad' : null),
    }),
  ];
}
