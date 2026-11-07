package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Service de domaine : un patient est-il attendu à une date ? Mêmes règles que la détection des absences : jour de dialyse, centre
 * ouvert, patient actif et présent ce jour-là (transféré ou guéri : attendu jusqu'à sa sortie incluse ; décédé ou greffé : jusqu'à la
 * veille ; occasionnel ou vacancier : jusqu'à la fin du séjour incluse). Sans état.
 */
public final class ProgrammationDuPatient {

    private static final Set<String> SEJOUR_LIMITE = Set.of("OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER");
    private static final Set<String> SORTIE_DEFINITIVE = Set.of("DECEDE", "GREFFE");
    private static final Set<String> SORTIE_INCLUSE = Set.of("TRANSFERE", "GUERRI");

    private ProgrammationDuPatient() {
    }

    /**
     * Applique la règle du hors planning : attendu, aucune dérogation ; sinon la dérogation confirmée est exigée. Un jour de fermeture
     * n'est franchi que par un administrateur confirmant un motif ; la secrétaire ne confirme jamais (aucune dérogation fournie).
     *
     * @return la dérogation à inscrire sur la séance, ou vide si le patient est attendu
     */
    public static Optional<DerogationHorsPlanning> exiger(SituationDuPlanning situation, LocalDate jour,
                                                          DerogationHorsPlanning derogation) {
        Optional<RaisonHorsPlanning> raison = evaluer(situation, jour);
        if (raison.isEmpty()) {
            return Optional.empty();
        }
        return switch (raison.get()) {
            case CENTRE_FERME -> {
                if (derogation == null || !derogation.administrateur()) {
                    throw new SeanceRefusee(MotifDeRefus.CENTRE_FERME,
                            "Le centre est fermé ce jour : seul un administrateur peut enregistrer une séance");
                }
                yield Optional.of(derogation);
            }
            case JOUR_NON_DIALYSE -> Optional.of(exigerUneDerogation(derogation, MotifDeRefus.JOUR_NON_DIALYSE,
                    "Ce patient n'est pas programmé aujourd'hui (ce n'est pas un de ses jours de dialyse)"));
            case PATIENT_NON_ATTENDU -> Optional.of(exigerUneDerogation(derogation, MotifDeRefus.PATIENT_NON_ATTENDU,
                    "Ce patient n'est pas attendu aujourd'hui (sommeil, sortie ou séjour terminé)"));
        };
    }

    /** La raison pour laquelle le patient n'est pas attendu à la date, ou vide s'il l'est. */
    public static Optional<RaisonHorsPlanning> evaluer(SituationDuPlanning s, LocalDate jour) {
        if (s.centreFerme()) return Optional.of(RaisonHorsPlanning.CENTRE_FERME);
        if (!s.jourDeDialyse()) return Optional.of(RaisonHorsPlanning.JOUR_NON_DIALYSE);
        boolean attendu = !s.enSommeil()
                && (s.dateDAdmission() == null || !s.dateDAdmission().isAfter(jour))
                && presentCeJour(s, jour);
        return attendu ? Optional.empty() : Optional.of(RaisonHorsPlanning.PATIENT_NON_ATTENDU);
    }

    private static DerogationHorsPlanning exigerUneDerogation(DerogationHorsPlanning derogation, MotifDeRefus motif, String message) {
        if (derogation == null) throw new SeanceRefusee(motif, message);
        return derogation;
    }

    private static boolean presentCeJour(SituationDuPlanning s, LocalDate jour) {
        String etat = s.etatDuPatient();
        LocalDate evenement = s.dateDeLEtat();
        if (etat == null) return true;
        if (SEJOUR_LIMITE.contains(etat)) return evenement == null || !jour.isAfter(evenement);
        if (SORTIE_DEFINITIVE.contains(etat)) return evenement != null && jour.isBefore(evenement);
        if (SORTIE_INCLUSE.contains(etat)) return evenement != null && !jour.isAfter(evenement);
        return true;
    }

    /** Pourquoi un patient n'est pas attendu à la date demandée. */
    public enum RaisonHorsPlanning {
        CENTRE_FERME, JOUR_NON_DIALYSE, PATIENT_NON_ATTENDU
    }
}
