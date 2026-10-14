package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.time.Instant;

/** Commande de clôture. {@code etatApres} est le code reçu de l'état de l'équipement après intervention. */
public record TerminerInterventionCommande(InterventionId id, CentreId centre, String actions, String etatApres, Instant fin,
                                           String cause, UtilisateurId par) {
}
