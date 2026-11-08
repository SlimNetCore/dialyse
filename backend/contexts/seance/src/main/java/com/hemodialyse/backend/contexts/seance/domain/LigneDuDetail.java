package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Une ligne nominative du détail du tableau de bord : un patient, le jour de la séance (réalisée ou manquée) et son statut.
 * KB-SEA-66 (reproduit, décision métier / DPO requise) : nom, prénom, identifiant et présence sont servis sans pagination ni filtre à
 * tout profil autorisé sur la route, secrétaire comprise.
 */
public record LigneDuDetail(PatientId patient, String nom, String prenom, LocalDate jour, String statut) {

    /**
     * Ordre du détail : jour, puis nom, puis prénom, par ordre des caractères (KB-SEA-67 : sensible à la casse, les majuscules avant les
     * minuscules, reproduit).
     */
    public static final Comparator<LigneDuDetail> ORDRE = Comparator.comparing(LigneDuDetail::jour)
            .thenComparing(LigneDuDetail::nom).thenComparing(LigneDuDetail::prenom);
}
