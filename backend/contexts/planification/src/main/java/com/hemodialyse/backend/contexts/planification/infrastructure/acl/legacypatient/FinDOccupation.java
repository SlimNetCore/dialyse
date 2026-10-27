package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Traduction de la règle de libération d'une place du legacy (RG-PLN-010, RG-PAT-032) : dernier jour où le patient occupe sa place
 * selon son état et la date de l'évènement. Transféré ou guéri : la date incluse ; décédé ou greffé : la veille ; état de sortie sans
 * date : place déjà libérée ; séjour limité (occasionnel, vacancier) : la date de fin de séjour, sans date non borné ; permanent :
 * sans limite. Les codes d'état sont ceux de la table {@code patients} (modèle de Patient et couverture, pas celui du contexte).
 */
public final class FinDOccupation {

    private static final Set<String> ETATS_DE_SORTIE = Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI");
    private static final Set<String> ETATS_SEJOUR_LIMITE = Set.of("OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER");
    private static final Set<String> ETATS_LIBERES_LA_VEILLE = Set.of("DECEDE", "GREFFE");

    private FinDOccupation() {
    }

    /** @return vide si la place est occupée sans limite ; {@link LocalDate#MIN} si elle est déjà libérée */
    public static Optional<LocalDate> dernierJourOccupe(String etat, LocalDate dateEvenement) {
        if (etat != null && ETATS_SEJOUR_LIMITE.contains(etat)) return Optional.ofNullable(dateEvenement);
        if (etat == null || !ETATS_DE_SORTIE.contains(etat)) return Optional.empty();
        if (dateEvenement == null) return Optional.of(LocalDate.MIN);
        return Optional.of(ETATS_LIBERES_LA_VEILLE.contains(etat) ? dateEvenement.minusDays(1) : dateEvenement);
    }
}
