package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Dernier jour où un patient est attendu en séance selon son état et la date de l'évènement (règle de libération de la place, RG-PAT-032
 * côté Patient ; traduite ici pour que le domaine de la Séance reste sans dépendance vers le Patient). Transféré ou guéri : la date
 * incluse ; décédé ou greffé : la veille ; état de sortie sans date : place déjà libérée ({@link LocalDate#MIN}) ; séjour limité
 * (occasionnel, vacancier) : la date de fin de séjour, non bornée sans date ; permanent ou état inconnu : sans limite.
 * Résidu daté de l'ADR 0095 : à remplacer par un langage publié du Patient.
 */
public final class FinDeLaPriseEnCharge {

    private static final Set<String> ETATS_DE_SORTIE = Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI");
    private static final Set<String> ETATS_SEJOUR_LIMITE = Set.of("OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER");
    private static final Set<String> ETATS_LIBERES_LA_VEILLE = Set.of("DECEDE", "GREFFE");

    private FinDeLaPriseEnCharge() {
    }

    /** @return vide si le patient est attendu sans limite ; {@link LocalDate#MIN} si sa place est déjà libérée */
    public static Optional<LocalDate> dernierJourAttendu(String etat, LocalDate dateDeLEvenement) {
        if (etat != null && ETATS_SEJOUR_LIMITE.contains(etat)) return Optional.ofNullable(dateDeLEvenement);
        if (etat == null || !ETATS_DE_SORTIE.contains(etat)) return Optional.empty();
        if (dateDeLEvenement == null) return Optional.of(LocalDate.MIN);
        return Optional.of(ETATS_LIBERES_LA_VEILLE.contains(etat) ? dateDeLEvenement.minusDays(1) : dateDeLEvenement);
    }
}
