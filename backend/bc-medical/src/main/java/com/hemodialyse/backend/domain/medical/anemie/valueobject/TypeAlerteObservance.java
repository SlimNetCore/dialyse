package com.hemodialyse.backend.domain.medical.anemie.valueobject;

/**
 * Nature d'une alerte d'observance :
 * <ul>
 *   <li>{@code RETARD_CONSTATE} : une période déjà close (semaine, mois...) s'est terminée avec
 *   un nombre d'administrations inférieur à la prescription — un fait acquis, ne se résout pas
 *   tout seul (l'infirmier ne peut plus rattraper une période passée) ; le médecin l'acquitte
 *   manuellement une fois le constat pris en compte.</li>
 *   <li>{@code RAPPEL_ECHEANCE} : la période en cours (encore ouverte) approche de sa fin et il
 *   reste des doses à administrer — un rappel actionnable, qui se résout automatiquement dès que
 *   l'infirmier rattrape le retard avant l'échéance.</li>
 * </ul>
 */
public enum TypeAlerteObservance {
    RETARD_CONSTATE,
    RAPPEL_ECHEANCE
}
