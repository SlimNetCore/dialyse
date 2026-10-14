package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;
import java.util.UUID;

/**
 * Rectification d'une intervention terminée (R15) : motif, auteur, instant, et la clôture annulée (instant et auteur) pour
 * conserver la trace de ce qui a été rouvert. Valeur immuable, ajoutée à l'intervention et jamais retirée. Les champs de la
 * clôture annulée sont nuls pour une intervention qui n'avait pas de clôture enregistrée.
 */
public record Rectification(UUID id, String motif, UtilisateurId par, Instant le, Instant clotureAnterieureLe,
                            UtilisateurId clotureAnterieurePar) {
}
