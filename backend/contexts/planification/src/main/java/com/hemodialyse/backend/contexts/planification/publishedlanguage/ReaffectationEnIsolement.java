package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.UUID;

/**
 * Port publié (commande) appelé par la sérologie (Dossier médical, legacy) <b>dans la transaction de l'enregistrement de la
 * sérologie</b> : la sérologie et la réaffectation réussissent ou échouent ensemble (décision du 27/10, KB-PLN-59). Le centre
 * est verrouillé (409 {@code PLANIFICATION_ECRITURE_CONCURRENTE} si le verrou n'est pas obtenu en 5 s). Un patient d'un autre centre
 * est refusé ({@link PlacementDesPatients.Refus}, code {@code PLACEMENT_PATIENT_INTROUVABLE}).
 * <p>
 * Patient à risque : replacé en salle d'isolement (jours inchangés), ou signalé (alerte {@code ISOLEMENT_IMPOSSIBLE} émise une
 * fois par patient et par jour du centre) s'il n'y a plus de place. Patient redevenu sans risque : le retour en salle ordinaire est
 * <b>proposé</b> à l'administration, jamais appliqué (KB-PLN-61).
 */
public interface ReaffectationEnIsolement {

    enum Issue {
        AUCUN_RISQUE,
        NON_PLACE,
        DEJA_CONFORME,
        DEPLACE,
        A_REPLANIFIER,
        /** Patient sans risque en salle d'isolement : un retour en salle ordinaire est proposé (rien n'est déplacé). */
        RETOUR_PROPOSE
    }

    Issue siRisque(UUID centre, UUID patient);
}
