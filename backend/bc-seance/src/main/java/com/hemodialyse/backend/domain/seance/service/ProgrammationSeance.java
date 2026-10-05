package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.SituationPlanning;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Domain Service — un patient est-il attendu à une date ? Mêmes règles que la détection des absences (RG-ABS) :
 * jour de dialyse, centre ouvert, patient actif et présent ce jour-là (transféré, décédé, greffé ou guéri : attendu
 * jusqu'à sa sortie ; occasionnel ou vacancier : jusqu'à la fin du séjour incluse).
 */
public final class ProgrammationSeance {

    private static final Set<String> SEJOUR_LIMITE = Set.of("OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER");
    private static final Set<String> SORTIE_DEFINITIVE = Set.of("DECEDE", "GREFFE");
    private static final Set<String> SORTIE_INCLUSE = Set.of("TRANSFERE", "GUERRI");
    private ProgrammationSeance() {
    }

    /**
     * @return la raison du hors planning, ou vide si le patient est attendu à la date donnée
     */
    public static Optional<RaisonHorsPlanning> evaluer(SituationPlanning s, LocalDate date) {
        if (s.centreFerme()) {
            return Optional.of(RaisonHorsPlanning.CENTRE_FERME);
        }
        if (!s.jourDialyse()) {
            return Optional.of(RaisonHorsPlanning.JOUR_NON_DIALYSE);
        }
        boolean attendu = !s.enSommeil()
                && (s.dateAdmission() == null || !s.dateAdmission().isAfter(date))
                && presentCeJour(s, date);
        return attendu ? Optional.empty() : Optional.of(RaisonHorsPlanning.PATIENT_NON_ATTENDU);
    }

    private static boolean presentCeJour(SituationPlanning s, LocalDate date) {
        String etat = s.etatPatient();
        LocalDate evenement = s.dateEvenementEtat();
        if (etat == null) {
            return true;
        }
        if (SEJOUR_LIMITE.contains(etat)) {
            return evenement == null || !date.isAfter(evenement);
        }
        if (SORTIE_DEFINITIVE.contains(etat)) {
            return evenement != null && date.isBefore(evenement);
        }
        if (SORTIE_INCLUSE.contains(etat)) {
            return evenement != null && !date.isAfter(evenement);
        }
        return true;
    }

    /**
     * Raison pour laquelle un patient n'est pas attendu à la date demandée.
     */
    public enum RaisonHorsPlanning {
        CENTRE_FERME,
        JOUR_NON_DIALYSE,
        PATIENT_NON_ATTENDU
    }
}
