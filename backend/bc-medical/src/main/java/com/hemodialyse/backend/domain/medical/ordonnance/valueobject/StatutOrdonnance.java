package com.hemodialyse.backend.domain.medical.ordonnance.valueobject;

/**
 * Cycle de vie d'une ordonnance : {@code BROUILLON → SIGNEE → IMPRIMEE}, avec {@code ANNULEE}
 * accessible depuis n'importe quel statut non terminal — annuler après signature/impression
 * reflète une décision clinique (erreur détectée, traitement changé), pas une correction en place.
 * <p>
 * Une fois {@code SIGNEE}, les lignes de l'ordonnance ne sont plus modifiables : l'agrégat
 * n'expose d'ailleurs aucun mutateur de contenu, seulement des transitions de statut — corriger
 * une ordonnance signée impose de l'annuler et d'en créer une nouvelle.
 */
public enum StatutOrdonnance {
    BROUILLON,
    SIGNEE,
    IMPRIMEE,
    ANNULEE
}
