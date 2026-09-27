import {DashboardChange} from '../../core/api/direction-api.service';

/** Une notification : les changements d'une famille d'indicateurs pour un centre. */
export type ChangeGroup = {
  key: string;
  centerId: string;
  centre: string;
  family: string;
  changes: DashboardChange[];
};

/** Regroupe les changements par centre et famille (une notification par groupe, ordre d'arrivée conservé). */
export function groupChanges(changes: readonly DashboardChange[]): ChangeGroup[] {
  const groups = new Map<string, ChangeGroup>();
  for (const c of changes) {
    const key = `${c.centerId}|${c.family}`;
    const group = groups.get(key) ?? {key, centerId: c.centerId, centre: c.centre, family: c.family, changes: []};
    group.changes.push(c);
    groups.set(key, group);
  }
  return [...groups.values()];
}

/** Variation signée d'un changement (après - avant). */
export function delta(change: DashboardChange): number {
  return change.after - change.before;
}

/** « +3 », « -1,5 » : variation lisible (le signe est toujours affiché). */
export function formatDelta(value: number): string {
  const rounded = Math.round(value * 100) / 100;
  return `${rounded > 0 ? '+' : ''}${rounded}`;
}

/** Nom court d'un indicateur pour la traduction : les indicateurs par caisse/tranche sont préfixés (« caHt:CNAS »). */
export function metricKey(name: string): string {
  const i = name.indexOf(':');
  return i < 0 ? name : name.slice(0, i);
}

/**
 * Ancienneté lisible d'une notification (« il y a 3 min ») dans la langue donnée ; « maintenant » sous 45 secondes.
 * Les langues que `Intl` ne connaît pas (ex. kabyle) retombent sur le français.
 */
export function timeAgo(at: string, now: number, lang: string): string {
  const seconds = Math.max(0, Math.round((now - new Date(at).getTime()) / 1000));
  const supported = Intl.RelativeTimeFormat.supportedLocalesOf([lang]).length > 0 ? lang : 'fr';
  const rtf = new Intl.RelativeTimeFormat(supported, {numeric: 'auto', style: 'short'});
  if (seconds < 45) return rtf.format(0, 'second');
  if (seconds < 3600) return rtf.format(-Math.max(1, Math.round(seconds / 60)), 'minute');
  if (seconds < 86400) return rtf.format(-Math.round(seconds / 3600), 'hour');
  return rtf.format(-Math.round(seconds / 86400), 'day');
}

/** Alertes apparues (0 → valeur) ou disparues (valeur → 0) dans un groupe. */
export function alertTransitions(group: ChangeGroup): { code: string; raised: boolean }[] {
  return group.changes
    .filter((c) => c.family === 'ALERTES')
    .map((c) => ({code: c.name, raised: c.before === 0 && c.after > 0}));
}
