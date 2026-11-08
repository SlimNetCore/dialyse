package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trace de la suppression d'une séance : quelle séance (patient, jour, statut au moment de la suppression), pourquoi, par qui et quand.
 * La séance et ses saisies disparaissent ; cette trace reste (journal {@code seance_suppression}). La trace ne garde ni la facture ni son
 * lien (reproduit) : c'est pourquoi une séance rattachée à une facture n'est plus supprimable (KB-SEA-50, corrigé).
 */
public record SuppressionDeSeance(UUID id, CentreId centre, SeanceId seance, PatientId patient, LocalDate jour, StatutDeSeance statut,
                                  MotifDeSuppression motif, String auteur, Instant supprimeLe) {

    /** Auteur retenu quand la session n'en donne pas. */
    public static final String AUTEUR_PAR_DEFAUT = "system";

    /**
     * Prépare la suppression d'une séance après avoir vérifié qu'elle est supprimable. Le motif est déjà construit : il a donc été contrôlé
     * AVANT le statut (ordre du legacy, KB-SEA-52, reproduit).
     */
    public static SuppressionDeSeance de(UUID id, Seance seance, MotifDeSuppression motif, String auteur, Instant maintenant) {
        seance.exigerSupprimable();
        return new SuppressionDeSeance(id, seance.centre(), seance.id(), seance.patient(), seance.jour(), seance.statut(), motif,
                auteur == null || auteur.isBlank() ? AUTEUR_PAR_DEFAUT : auteur, maintenant);
    }
}
