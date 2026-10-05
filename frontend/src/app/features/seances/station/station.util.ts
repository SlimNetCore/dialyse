/** Étapes de saisie d'une séance au poste infirmier, dans l'ordre. */
export const STATION_STEPS = ['constantes', 'consommables', 'anemie', 'remarques'] as const;
export type StationStep = (typeof STATION_STEPS)[number];

/** Nombre de consommables proposés en un toucher quand le centre n'a rien configuré. */
export const QUICK_ARTICLES_MAX = 6;

/** Nombre maximal de raccourcis qu'un centre peut configurer (identique au serveur). */
export const SHORTCUTS_MAX = 12;

/** Évolution de poids d'une séance passée : perte de poids (avant − après) en kg, null si une valeur manque. */
export function weightLossKg(avant: number | null | undefined, apres: number | null | undefined): number | null {
  if (avant == null || apres == null) return null;
  return Math.round((avant - apres) * 10) / 10;
}

/** Statut GMAO d'un générateur qui ne pose aucun problème (« FONCTIONNEL » : ancien libellé, avant la GMAO v2). */
const GENERATOR_OK_STATES = ['EN_SERVICE', 'FONCTIONNEL'];

export function todayIsoDate(now = new Date()): string {
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** Nombre de jours passés où une séance restée « À valider » est proposée à la régularisation (= fenêtre de détection des absences). */
export const PENDING_DAYS = 7;

/** Période des séances à régulariser : les {@link PENDING_DAYS} jours qui précèdent aujourd'hui, hier inclus. */
export function pendingWindow(now = new Date()): { from: string; to: string } {
  const day = (offset: number) => todayIsoDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - offset));
  return {from: day(PENDING_DAYS), to: day(1)};
}

/** Initiales d'un patient pour son avatar (« DJ » pour Dupont Jean) ; « ? » quand aucun nom n'est connu. */
export function initials(nom: string | null | undefined, prenom: string | null | undefined): string {
  const letters = [nom, prenom].map((part) => (part ?? '').trim().charAt(0).toUpperCase()).join('');
  return letters || '?';
}

/** Nombre saisi au clavier (virgule ou point) ; vide ou invalide : null. */
export function parseDecimal(raw: string | null | undefined): number | null {
  const text = (raw ?? '').trim().replace(',', '.');
  if (!text) return null;
  const value = Number(text);
  return Number.isFinite(value) ? value : null;
}

export function toNullableText(value: string | null | undefined): string | null {
  const text = (value ?? '').trim();
  return text.length ? text : null;
}

/** Vrai quand l'état du générateur affecté mérite d'alerter l'infirmier (état connu et différent de « en service »). */
export function generatorNeedsAttention(state: string | null | undefined): boolean {
  const normalized = (state ?? '').trim().toUpperCase();
  return normalized.length > 0 && !GENERATOR_OK_STATES.includes(normalized);
}

/** Identifiants des articles les plus sortis (quantité décroissante), limités à {@link QUICK_ARTICLES_MAX}. */
export function quickArticleIds(sorties: ReadonlyArray<{ articleId: string; quantiteTotale: number }>): string[] {
  return [...sorties]
    .sort((a, b) => b.quantiteTotale - a.quantiteTotale)
    .slice(0, QUICK_ARTICLES_MAX)
    .map((s) => s.articleId);
}
