package com.hemodialyse.backend.domain.gmao.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Pièce justificative jointe à une intervention (bon d'intervention, facture, photo).
 * Le contenu est vérifié sur sa signature (PDF, PNG ou JPEG) et non sur le seul nom ou type déclaré.
 * {@code contenu} est null dans les listes (métadonnées seules).
 */
public record DocumentIntervention(
        UUID id,
        UUID interventionId,
        UUID centreId,
        TypeDocumentIntervention type,
        String nom,
        String contentType,
        long taille,
        byte[] contenu,
        UUID ajoutePar,
        OffsetDateTime ajouteLe
) {
    public static final long TAILLE_MAX_OCTETS = 5L * 1024 * 1024;
    public static final int MAX_PAR_INTERVENTION = 50;

    public static DocumentIntervention creer(
            UUID interventionId, UUID centreId, TypeDocumentIntervention type, String nomFichier,
            byte[] contenu, UUID ajoutePar) {
        if (interventionId == null || centreId == null)
            throw new IllegalArgumentException("Intervention et centre requis");
        if (type == null) throw new IllegalArgumentException("Type de document requis");
        if (contenu == null || contenu.length == 0) throw new IllegalArgumentException("Fichier vide");
        if (contenu.length > TAILLE_MAX_OCTETS)
            throw new IllegalArgumentException("Fichier trop volumineux (5 Mo maximum)");
        String contentType = detecterType(contenu);
        if (contentType == null) throw new IllegalArgumentException("Format non accepté (PDF, PNG ou JPEG uniquement)");
        return new DocumentIntervention(UUID.randomUUID(), interventionId, centreId, type, nettoyerNom(nomFichier),
                contentType, contenu.length, contenu, ajoutePar, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Type réel déduit des premiers octets ; null si le format n'est pas accepté.
     */
    static String detecterType(byte[] c) {
        if (c.length >= 5 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F' && c[4] == '-') {
            return "application/pdf";
        }
        if (c.length >= 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G') {
            return "image/png";
        }
        if (c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    /**
     * Garde uniquement le nom du fichier (sans chemin ni caractères de contrôle), borné à 150 caractères.
     */
    static String nettoyerNom(String nom) {
        String n = nom == null ? "" : nom.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>:*?|]", "").trim();
        if (n.isEmpty()) n = "document";
        return n.length() > 150 ? n.substring(n.length() - 150) : n;
    }

    public static DocumentIntervention reconstruct(
            UUID id, UUID interventionId, UUID centreId, TypeDocumentIntervention type, String nom,
            String contentType, long taille, byte[] contenu, UUID ajoutePar, OffsetDateTime ajouteLe) {
        return new DocumentIntervention(id, interventionId, centreId, type, nom, contentType, taille, contenu,
                ajoutePar, ajouteLe);
    }
}
