package com.hemodialyse.backend.contexts.documents.domain;

/**
 * Identité imprimée d'un centre et de sa société, telle que le papier en-tête la pose sur un document (nom, adresse, contacts, mentions
 * légales, pied de page, logo). Donnée d'Organisation : Documents ne la possède pas, il la compose (ADR 0140 D4). Tous les textes sont non
 * nuls (vides si absents) ; seul le logo peut être {@code null}.
 */
public record IdentiteImprimable(String nom, String adresse, String ville, String wilaya, String telephone, String email, String siteWeb,
                                 Societe societe) {

    /** Identité imprimée d'une société ; {@code logo} : les octets d'une image, {@code null} s'il est absent. */
    public record Societe(String raisonSociale, String adresse, String ville, String wilaya, String telephone, String email, String siteWeb,
                          String nif, String nis, String rc, String piedDePage, byte[] logo) {

        /** Une société entièrement vide (centre sans société : cas défensif). */
        public static Societe vide() {
            return new Societe("", "", "", "", "", "", "", "", "", "", "", null);
        }
    }
}
