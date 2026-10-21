package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Événement d'intégration publié après l'enregistrement d'un règlement de facture (F12), version 2 (slice F4, ADR 0033). Publié
 * dans la transaction de l'enregistrement et traité après son commit (livraison <b>au plus une fois</b> : écouteur en mémoire après le
 * commit, rien n'est persisté ; un arrêt entre le commit et l'écouteur laisse le règlement sans écriture, rattrapé par
 * {@code POST /api/v1/comptabilite/rebuild}. Les consommateurs sont idempotents, par {@code reglementId}). Jamais publié quand
 * un renvoi à clé d'idempotence ne crée rien.
 *
 * @param reglementId identifiant du règlement enregistré
 * @param factureId   la facture réglée
 * @param centerId    le centre
 * @param montant     montant <b>signé</b> tel que saisi, non arrondi (KB-FAC-46 reproduit) ; négatif pour un remboursement
 * @param dateReglement date effective du règlement
 * @param userId      auteur effectif (l'utilisateur de la session, ou {@code system})
 * @param mode        mode de règlement ({@code BANQUE, CHEQUE, CAISSE, ESPECES}) ; {@code null} : comptabilisé en banque
 */
public record FactureReglementRecordedEvent(UUID reglementId, UUID factureId, UUID centerId, BigDecimal montant,
                                            LocalDate dateReglement, String userId, String mode) {
}
