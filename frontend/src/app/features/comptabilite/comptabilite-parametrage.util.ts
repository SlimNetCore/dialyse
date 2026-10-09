import {
  CompteItem,
  JournalItem,
  MappingComptableItem,
  OperationComptable,
  OPERATIONS_COMPTABLES,
} from '../../core/api/comptabilite-api.service';
import {toLocalIsoDate} from '../../shared/date.util';

/**
 * Comptes choisis sur l'écran, dans l'ordre d'affichage. Le compte client d'un payeur se règle payeur par payeur ;
 * ici ne figurent que le compte des patients qui paient eux-mêmes et le compte par défaut des autres payeurs.
 */
export const COMPTES_GENERAUX = [
  'compteVentes', 'compteClientPatient', 'compteClientDefaut', 'compteBanque', 'compteCaisse', 'compteTVACollectee',
] as const;
export const COMPTES_STOCK = [
  'compteStock', 'compteConsommation', 'compteFacturesNonParvenues', 'compteBoniInventaire', 'compteMaliInventaire',
] as const;
/** Seul le compte de TVA collectée est facultatif. */
export const COMPTES_OBLIGATOIRES: readonly CompteCle[] =
  [...COMPTES_GENERAUX.filter((c) => c !== 'compteTVACollectee'), ...COMPTES_STOCK];

export type CompteCle = typeof COMPTES_GENERAUX[number] | typeof COMPTES_STOCK[number];
export type ComptesModel = Record<CompteCle, string>;
export type JournauxParOperation = Record<OperationComptable, string>;

export const CODE_JOURNAL_MAX = 10;
export const LIBELLE_JOURNAL_MAX = 100;
const CODE_JOURNAL = new RegExp(`^[A-Z0-9]{1,${CODE_JOURNAL_MAX}}$`);

export function comptesVides(): ComptesModel {
  return Object.fromEntries([...COMPTES_GENERAUX, ...COMPTES_STOCK].map((c) => [c, ''])) as ComptesModel;
}

export function comptesDe(mapping: MappingComptableItem): ComptesModel {
  return Object.fromEntries(
    [...COMPTES_GENERAUX, ...COMPTES_STOCK].map((c) => [c, mapping[c] ?? ''])) as ComptesModel;
}

/** Comptes mal choisis : obligatoire laissé vide, ou numéro absent des comptes actifs du plan du centre. */
export function comptesInvalides(comptes: ComptesModel, plan: readonly CompteItem[]): CompteCle[] {
  const numeros = new Set(plan.map((c) => c.numero));
  return (Object.keys(comptes) as CompteCle[]).filter((cle) => {
    const valeur = comptes[cle].trim();
    return valeur ? !numeros.has(valeur) : COMPTES_OBLIGATOIRES.includes(cle);
  });
}

/** Code de journal tel qu'il sera enregistré : sans espaces, en majuscules. */
export function normaliserCodeJournal(code: string): string {
  return code.trim().toUpperCase();
}

export function journalValide(code: string, libelle: string): boolean {
  const texte = libelle.trim();
  return CODE_JOURNAL.test(normaliserCodeJournal(code)) && texte.length > 0 && texte.length <= LIBELLE_JOURNAL_MAX;
}

/** Opérations dont le journal choisi n'existe plus ou n'est plus actif : à corriger avant d'enregistrer. */
export function operationsSansJournal(choix: JournauxParOperation, actifs: readonly JournalItem[]): OperationComptable[] {
  const codes = new Set(actifs.map((j) => j.code));
  return OPERATIONS_COMPTABLES.filter((operation) => !codes.has(choix[operation]));
}

/** Opérations qui utilisent un journal : il ne peut alors être ni désactivé ni supprimé. */
export function operationsDuJournal(choix: JournauxParOperation | null | undefined, code: string): OperationComptable[] {
  return choix ? OPERATIONS_COMPTABLES.filter((operation) => choix[operation] === code) : [];
}

/** Période proposée pour comptabiliser le stock : du premier jour du mois à aujourd'hui. */
export function periodeParDefaut(aujourdhui: Date): { from: string; to: string } {
  return {
    from: toLocalIsoDate(new Date(aujourdhui.getFullYear(), aujourdhui.getMonth(), 1)),
    to: toLocalIsoDate(aujourdhui),
  };
}

/** Une période est comptabilisable si ses deux dates sont saisies, dans l'ordre, et couvrent un an au plus. */
export function periodeValide(from: string, to: string): boolean {
  if (!from || !to || from > to) return false;
  const jours = (new Date(to).getTime() - new Date(from).getTime()) / 86_400_000;
  return jours <= 366;
}
