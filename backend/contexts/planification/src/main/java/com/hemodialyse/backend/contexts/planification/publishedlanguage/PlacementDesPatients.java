package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.UUID;

/**
 * Port publié (commande) appelé par la fiche du patient (Patient et couverture, ADR 0052/0053) : toute place saisie sur une fiche
 * est contrôlée et écrite par la Planification, sous le verrou du centre, <b>dans la transaction de l'appelant</b>. La fiche cesse
 * d'écrire les colonnes de place.
 * <p>
 * Rien de renseigné (ni salle, ni créneau, ni générateur, ni jour) = pas de place, acceptée. Une partie seulement :
 * {@code PLACEMENT_INCOMPLET}. Refus : {@link Refus} avec le code stable {@code PLACEMENT_<règle>} (422) ; verrou non obtenu en 5 s :
 * {@link EcritureConcurrente} (409 {@code PLANIFICATION_ECRITURE_CONCURRENTE}).
 */
public interface PlacementDesPatients {

    /** Place refusée par une règle de la planification (code stable, ex. {@code PLACEMENT_GENERATEUR_OCCUPE}). */
    class Refus extends RuntimeException {
        private final String code;

        public Refus(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Le centre est modifié par une autre écriture (409). */
    class EcritureConcurrente extends RuntimeException {
        public EcritureConcurrente(String message) {
            super(message);
        }
    }

    /**
     * Contrôle la place d'une fiche avant son enregistrement et tient le verrou du centre jusqu'à la fin de la transaction.
     *
     * @param patient nul à la création de la fiche
     */
    void verifier(UUID centre, UUID patient, UUID salle, UUID creneau, UUID generateur, Set<DayOfWeek> jours);

    /** Contrôle puis écrit la place d'un patient existant (ou l'efface si rien n'est renseigné). */
    void placer(UUID centre, UUID patient, UUID salle, UUID creneau, UUID generateur, Set<DayOfWeek> jours);
}
