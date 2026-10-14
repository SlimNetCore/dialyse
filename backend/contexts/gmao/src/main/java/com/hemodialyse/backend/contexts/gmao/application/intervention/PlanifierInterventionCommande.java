package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;

import java.time.Instant;

/**
 * Commande de planification. {@code type}, {@code etatAvant} et {@code priorite} sont les codes reçus : le cas d'usage les
 * reconnaît après avoir vérifié l'équipement (même ordre de refus que le legacy). Priorité vide ou nulle : normale.
 */
public record PlanifierInterventionCommande(EquipementId equipementId, CentreId centreId, String type, Instant debut,
                                            String description, IntervenantId intervenantId, String etatAvant,
                                            String symptome, String priorite, Instant echeance, UtilisateurId par) {
}
