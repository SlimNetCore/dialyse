import {SeanceHistoryQuery} from '../../../core/api/backend-api.service';

/** Colonnes de l'historique triables côté serveur (liste blanche identique à celle du backend). */
export const HISTORY_SORTABLE_COLUMNS = ['dateSeance', 'patient', 'status'] as const;

/** Statuts d'une séance, dans l'ordre du parcours (filtre de la colonne « Statut »). */
export const SEANCE_STATUSES = ['CREE', 'VALIDEE', 'SIGNEE', 'FACTUREE'] as const;

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Mois courant au format « AAAA-MM » (heure locale). */
export function currentMonthIso(now = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** « 2026-10 » → année et mois ; null quand le texte n'est pas un mois valide. */
export function parseMonth(raw: string | null | undefined): { year: number; month: number } | null {
  const [yearText, monthText] = (raw ?? '').split('-');
  const year = Number(yearText);
  const month = Number(monthText);
  return Number.isInteger(year) && year > 0 && Number.isInteger(month) && month >= 1 && month <= 12 ? {
    year,
    month
  } : null;
}

/** Plage saisie dans le filtre de date : « 2026-10-01 » (un jour) ou « 2026-10-01..2026-10-31 » (bornes facultatives). */
export function parseDateRange(raw: string | undefined): { from: string | null; to: string | null } {
  const value = (raw ?? '').trim();
  if (!value) return {from: null, to: null};
  const valid = (v: string): string | null => (ISO_DATE.test(v.trim()) ? v.trim() : null);
  if (value.includes('..')) {
    const [from = '', to = ''] = value.split('..', 2);
    return {from: valid(from), to: valid(to)};
  }
  const day = valid(value);
  return {from: day, to: day};
}

/** Valeur du filtre de date de la colonne pour une période « du … au … » (vide = sans borne ; les deux vides = aucun filtre). */
export function dateRangeFilter(from: string, to: string): string {
  const f = ISO_DATE.test((from ?? '').trim()) ? from.trim() : '';
  const t = ISO_DATE.test((to ?? '').trim()) ? to.trim() : '';
  return f || t ? `${f}..${t}` : '';
}

/**
 * Traduit l'état de la liste (filtres par colonne + tri) en critères d'historique pour le serveur. Les colonnes
 * non gérées par le serveur sont ignorées ; sans tri valide, les séances sont triées de la plus récente à la plus
 * ancienne. Le critère de date a une seule source : le filtre `dateSeance` de la colonne (la période « du … au … »
 * au-dessus de la table écrit ce même filtre), donc la puce de la table montre toujours ce qui est envoyé.
 */
export function toSeanceHistoryQuery(
  filters: Record<string, string>,
  sortColumnId: string | null,
  sortDirection: 'asc' | 'desc' | '' | null,
): SeanceHistoryQuery {
  const {from, to} = parseDateRange(filters['dateSeance']);
  // La liste de statuts de la table est multiple (CSV) : le serveur accepte plusieurs statuts séparés par une virgule.
  const statuses = (filters['status'] ?? '').split(',').map((s) => s.trim().toUpperCase())
    .filter((s, i, all) => (SEANCE_STATUSES as readonly string[]).includes(s) && all.indexOf(s) === i);
  const text = (filters['patient'] ?? '').trim();
  const sortable = (HISTORY_SORTABLE_COLUMNS as readonly string[]).includes(sortColumnId ?? '');
  return {
    from,
    to,
    status: statuses.length ? statuses.join(',') : null,
    q: text || null,
    sortBy: sortable && sortDirection ? sortColumnId : 'dateSeance',
    sortDir: sortable && sortDirection ? sortDirection : 'desc',
  };
}
