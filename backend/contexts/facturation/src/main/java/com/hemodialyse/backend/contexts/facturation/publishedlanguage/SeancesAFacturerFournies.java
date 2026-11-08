package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié de la Facturation, côté FOURNI (ADR 0097) : les séances à facturer d'un centre, telles que la Séance les publie. Le module
 * Facturation ne peut pas dépendre du module Séance (cycle Maven Séance - Absences-patients - Facturation) : il exprime son besoin ici, en
 * types simples, et la plateforme le fournit en appelant {@code seance.v1}. Seules les séances {@code VALIDEE} ou {@code SIGNEE} (casse exacte,
 * KB-FAC-17), non facturées, de la période (bornes incluses) et d'un patient du même centre sont fournies, triées par nom, prénom, date puis
 * identifiant. Les prises en charge ne sont pas fournies : la politique de forfait reste à la Facturation.
 */
public interface SeancesAFacturerFournies {

    List<SeanceAFacturerFournie> dansLaPeriode(UUID centre, LocalDate debut, LocalDate fin);

    /**
     * @param forfaitImposeId forfait imposé à la séance, {@code null} s'il n'y en a pas (libellé et prix aussi) ; prix copié à l'identique
     */
    record SeanceAFacturerFournie(UUID id, LocalDate date, String statut, UUID forfaitImposeId, String libelleImpose,
                                  BigDecimal prixHtImpose, PatientFourni patient) {
    }

    record PatientFourni(UUID id, String code, String nom, String prenom, String etat, String numeroAssurance, UUID centrePayeur,
                         UUID agence) {
    }
}
