package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

/** Commande : ajouter une tâche à une intervention du centre. */
public record AjouterTacheCommande(InterventionId id, CentreId centre, String description, UtilisateurId par) {
}
