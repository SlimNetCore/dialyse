package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.time.Instant;
import java.util.UUID;

/**
 * Evenement de domaine publie : un resultat d'examen a ete recu pour une demande.
 * Produit par la couche application apres execution de {@code RecevoirResultat}.
 * Le centre est un UUID brut pour ne pas exposer le shared-kernel (regle ArchUnit
 * {@code le_langage_publie_ne_depend_de_rien_du_contexte}).
 */
public record ResultatExamenRecu(UUID eventId, UUID demandeExamenId, UUID patientId, UUID centreId,
                                 String categorie, Instant dateReception) {
}
