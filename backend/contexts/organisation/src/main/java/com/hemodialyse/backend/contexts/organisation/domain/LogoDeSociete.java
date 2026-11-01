package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.Arrays;

/**
 * Logo d'une société (value object) : image PNG ou JPEG reconnue à sa signature.
 * <p>
 * Le domaine ne fait que le contrôle léger (vide, taille, signature magique) : le décodage
 * complet et le contrôle des dimensions vivent dans la couche application
 * ({@code ValidateurImage}), car ArchUnit interdit {@code javax.*} dans le domaine.
 */
public record LogoDeSociete(byte[] contenu, String contentType) {

    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_DIMENSION = 2000;

    public LogoDeSociete {
        if (contenu == null || contenu.length == 0) throw new LogoRefuse("EMPTY", "Le fichier est vide.");
        if (contentType == null || contentType.isBlank()) throw new LogoRefuse("FILE_TYPE", "Type de contenu inconnu.");
    }

    /**
     * Valide des octets téléversés et construit le logo, ou lève {@link LogoRefuse}
     * avec un code stable ({@code EMPTY}, {@code TOO_LARGE}, {@code FILE_TYPE},
     * {@code IMAGE_INVALID}, {@code DIMENSIONS}).
     */
    public static LogoDeSociete valider(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new LogoRefuse("EMPTY", "Le fichier est vide.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new LogoRefuse("TOO_LARGE", "Taille maximale : " + MAX_BYTES / 1024 + " Ko.");
        }
        String contentType = renifler(bytes);
        if (contentType == null) {
            throw new LogoRefuse("FILE_TYPE", "Seuls les fichiers PNG et JPEG sont acceptés.");
        }
        return new LogoDeSociete(Arrays.copyOf(bytes, bytes.length), contentType);
    }

    private static String renifler(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }
}
