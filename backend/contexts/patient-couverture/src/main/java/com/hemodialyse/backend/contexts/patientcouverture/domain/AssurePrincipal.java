package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Règles de l'assuré principal d'un patient (R1 : un seul assuré principal à la fois), sans état. Vocabulaire du legacy : le
 * principal est la ligne {@code assure_patient} où {@code is_primary} est vrai ; le numéro d'assurance de l'assuré est une clé
 * globale ({@code assure.numero_assurance}).
 */
public final class AssurePrincipal {

    private AssurePrincipal() {
    }

    /**
     * Un assuré ne se capture que dans son centre : la fiche d'un centre qui déclare le numéro d'un assuré d'un AUTRE centre est
     * refusée en 422 ({@code ASSURE_D_UN_AUTRE_CENTRE}) ; elle ne reprend plus la ligne ni ne bascule son centre (décision du
     * 2026-11-03, KB-PAT-31).
     *
     * @param centreDeLAssure centre de l'assuré existant, nul si le numéro est nouveau
     */
    public static void verifierQueLAssureEstDuCentre(UUID centreDeLAssure, CentreId centre) {
        if (centreDeLAssure != null && !centreDeLAssure.equals(centre.valeur())) {
            throw captureRefusee();
        }
    }

    /** Le refus de capturer un assuré d'un autre centre (KB-PAT-31), aussi levé par le dépôt quand l'autre centre gagne la course. */
    public static RegleDePatientViolee captureRefusee() {
        return new RegleDePatientViolee("ASSURE_D_UN_AUTRE_CENTRE", "Ce numéro d'assurance appartient à un assuré d'un autre centre");
    }

    /**
     * Enregistrer la fiche d'un ayant droit ajoute une ligne d'affectation seulement si l'assuré déclaré n'est pas déjà le
     * principal du patient (idempotence, décision du 2026-11-03, KB-PAT-32) ; sinon l'ancien principal est retiré puis la ligne
     * ajoutée.
     *
     * @param principalActuel numéro de l'assuré principal actuel du patient, s'il y en a un
     */
    public static boolean doitAjouterUneAffectation(Optional<String> principalActuel, String numeroDeclare) {
        return principalActuel.map(actuel -> !actuel.equals(numeroDeclare)).orElse(true);
    }

    /**
     * La modification d'un assuré remplace tout (documenté) mais exige le NOM de l'assuré (400) : un corps vide n'efface plus
     * l'assuré (décision du 2026-11-03, KB-PAT-33). Les autres champs restent facultatifs, comme à la création par la fiche.
     */
    /** La fin d'une affectation ne précède pas son début (quand les deux sont connues ; le reste est permissif, KB-PAT-29). */
    public static void verifierLesDatesDeLAffectation(LocalDate debut, LocalDate fin) {
        if (debut != null && fin != null && fin.isBefore(debut)) {
            throw new SaisieInvalide("La date de fin doit être >= à la date de début");
        }
    }

    public static void verifierPourModification(String nom) {
        if (nom == null || nom.isBlank()) throw new SaisieInvalide("Nom de l'assuré obligatoire");
    }
}
