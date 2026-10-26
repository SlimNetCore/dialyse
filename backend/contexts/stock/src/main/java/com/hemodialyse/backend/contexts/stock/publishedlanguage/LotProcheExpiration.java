package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Événement d'intégration du Stock, contrat <b>v1</b> (ADR 0013, 0046) : un lot encore en stock périme dans les 30 jours ou est déjà
 * périmé, constaté par le contrôle quotidien des péremptions d'un centre actif. Publié par l'Outbox (registre Spring Modulith),
 * livré au moins une fois : les consommateurs sont idempotents sur {@link #identifiant()} (déterministe : un lot, un jour du centre).
 * La version fait partie du contenu ; tout changement incompatible crée un contrat v2.
 *
 * @param version        numéro du contrat ({@value #VERSION})
 * @param identifiant    clé d'idempotence (UUID v3 de « LotProcheExpiration | lot | jour du contrôle »)
 * @param centre         centre du lot
 * @param jourDuControle jour du centre auquel le contrôle a eu lieu
 * @param article        article du lot
 * @param codeArticle    code de l'article
 * @param lot            identifiant du lot
 * @param numeroLot      numéro de lot
 * @param datePeremption date de péremption
 * @param quantite       quantité restante (&gt; 0)
 * @param perime         vrai si la date de péremption est passée
 */
public record LotProcheExpiration(int version, UUID identifiant, UUID centre, LocalDate jourDuControle, UUID article,
                                  String codeArticle, UUID lot, String numeroLot, LocalDate datePeremption, BigDecimal quantite,
                                  boolean perime) {

    public static final int VERSION = 1;

    public static LotProcheExpiration v1(UUID centre, LocalDate jourDuControle, UUID article, String codeArticle, UUID lot,
                                         String numeroLot, LocalDate datePeremption, BigDecimal quantite, boolean perime) {
        UUID identifiant = UUID.nameUUIDFromBytes(
                ("LotProcheExpiration|" + lot + "|" + jourDuControle).getBytes(StandardCharsets.UTF_8));
        return new LotProcheExpiration(VERSION, identifiant, centre, jourDuControle, article, codeArticle, lot, numeroLot,
                datePeremption, quantite, perime);
    }
}
