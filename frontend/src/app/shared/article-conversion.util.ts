/** Champs de la fiche article nécessaires à la conversion d'une dose prescrite en quantité de stock. */
export interface ArticleDosage {
  unite?: string | null;
  dosageParUnite?: number | null;
  uniteDosage?: string | null;
}

export type ConversionDoseResult =
  | { quantite: number; erreur: null }
  | { quantite: null; erreur: 'UNITE_INCOMPATIBLE' | 'DOSAGE_NON_DEFINI' | 'DOSE_INVALIDE' };

const sameUnit = (a?: string | null, b?: string | null): boolean =>
  !!a && !!b && a.trim().toLowerCase() === b.trim().toLowerCase();

/**
 * Quantité à sortir du stock (dans l'unité de stock de l'article) pour une dose prescrite (UI, mg…) :
 * {@code dose ÷ dosageParUnite}. Miroir de {@code Article.quantiteStockPourDose} côté serveur, qui reste l'autorité :
 * ceci ne sert qu'à prévenir l'utilisateur avant l'envoi.
 */
export function quantiteStockPourDose(
  article: ArticleDosage | null | undefined,
  dose: number | null | undefined,
  uniteDose: string | null | undefined,
): ConversionDoseResult {
  if (!article || dose == null || !(dose > 0)) return {quantite: null, erreur: 'DOSE_INVALIDE'};
  if (article.dosageParUnite && article.dosageParUnite > 0) {
    if (!sameUnit(uniteDose, article.uniteDosage)) return {quantite: null, erreur: 'UNITE_INCOMPATIBLE'};
    return {quantite: Math.round((dose / article.dosageParUnite) * 10000) / 10000, erreur: null};
  }
  if (sameUnit(uniteDose, article.unite)) return {quantite: dose, erreur: null};
  return {quantite: null, erreur: 'DOSAGE_NON_DEFINI'};
}
