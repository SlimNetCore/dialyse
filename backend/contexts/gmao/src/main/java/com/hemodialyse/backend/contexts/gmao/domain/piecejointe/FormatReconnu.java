package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Les trois formats acceptés pour une pièce (R22). Le format se déduit du contenu, jamais du type déclaré par le client ni de
 * l'extension. Depuis la décision du 2026-10-12 (KB-GMAO-59) la structure est vérifiée, sans bibliothèque : le début ET la fin du
 * fichier doivent être ceux d'un fichier complet (un fichier tronqué, ou suivi de n'importe quoi, est refusé).
 * <ul>
 *   <li>PDF : en-tête {@code %PDF-x.y} et marqueur {@code %%EOF} dans les derniers octets, suivi de blancs seulement ;</li>
 *   <li>PNG : signature de 8 octets, suite de blocs dont les sommes de contrôle sont justes, bloc IHDR cohérent, IDAT, puis
 *       IEND exactement en dernier ;</li>
 *   <li>JPEG : {@code FFD8FF} au début et {@code FFD9} (EOI) à la fin.</li>
 * </ul>
 */
public enum FormatReconnu {
    PDF("application/pdf", Set.of("pdf")),
    PNG("image/png", Set.of("png")),
    JPEG("image/jpeg", Set.of("jpg", "jpeg"));

    private static final int FENETRE_FIN_PDF = 1024;
    private static final byte[] SIGNATURE_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final String contentType;
    private final Set<String> extensions;

    FormatReconnu(String contentType, Set<String> extensions) {
        this.contentType = contentType;
        this.extensions = extensions;
    }

    public String contentType() {
        return contentType;
    }

    /** Extensions (minuscules) cohérentes avec ce format. */
    public Set<String> extensions() {
        return extensions;
    }

    /** Format dont le type MIME est celui-ci ; vide pour tout autre type (y compris null). */
    public static Optional<FormatReconnu> depuisContentType(String contentType) {
        for (FormatReconnu format : values()) {
            if (format.contentType.equals(contentType)) return Optional.of(format);
        }
        return Optional.empty();
    }

    /**
     * Reconnaît le format d'un contenu non vide.
     *
     * @throws FichierRefuse format inconnu, ou format reconnu mais fichier incomplet ou altéré
     */
    static FormatReconnu reconnaitre(byte[] c) {
        FormatReconnu format;
        if (commencePar(c, '%', 'P', 'D', 'F', '-')) {
            format = PDF;
        } else if (commencePar(c, 0x89, 'P', 'N', 'G')) {
            format = PNG;
        } else if (commencePar(c, 0xFF, 0xD8, 0xFF)) {
            format = JPEG;
        } else {
            throw FichierRefuse.formatNonAccepte();
        }
        boolean complet = switch (format) {
            case PDF -> pdfComplet(c);
            case PNG -> pngComplet(c);
            case JPEG -> jpegComplet(c);
        };
        if (!complet) throw FichierRefuse.altere(format);
        return format;
    }

    private static boolean commencePar(byte[] c, int... attendu) {
        if (c.length < attendu.length) return false;
        for (int i = 0; i < attendu.length; i++) {
            if ((c[i] & 0xFF) != attendu[i]) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ PDF

    private static boolean pdfComplet(byte[] c) {
        // %PDF-1.4 : chiffre, point, chiffre
        if (c.length < 8 || !estChiffre(c[5]) || c[6] != '.' || !estChiffre(c[7])) return false;
        int debutFenetre = Math.max(8, c.length - FENETRE_FIN_PDF);
        for (int i = c.length - 5; i >= debutFenetre; i--) {
            if (c[i] == '%' && c[i + 1] == '%' && c[i + 2] == 'E' && c[i + 3] == 'O' && c[i + 4] == 'F') {
                for (int j = i + 5; j < c.length; j++) {
                    if (!estBlancDePdf(c[j])) return false;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean estChiffre(byte b) {
        return b >= '0' && b <= '9';
    }

    private static boolean estBlancDePdf(byte b) {
        return b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32;
    }

    // ------------------------------------------------------------------ PNG

    private static boolean pngComplet(byte[] c) {
        if (!commencePar(c, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return false;
        int position = SIGNATURE_PNG.length;
        boolean premier = true;
        boolean donnees = false;
        while (position + 12 <= c.length) {
            long longueur = entier32(c, position);
            if (longueur > Integer.MAX_VALUE || position + 12L + longueur > c.length) return false;
            int type = position + 4;
            int debutDonnees = position + 8;
            int fin = debutDonnees + (int) longueur;
            if (!lettres(c, type)) return false;
            CRC32 somme = new CRC32();
            somme.update(c, type, 4 + (int) longueur);
            if (somme.getValue() != entier32(c, fin)) return false;
            boolean ihdr = nomDeBloc(c, type, 'I', 'H', 'D', 'R');
            if (premier != ihdr) return false;
            if (ihdr && !enteteCoherent(c, debutDonnees, longueur)) return false;
            premier = false;
            if (nomDeBloc(c, type, 'I', 'D', 'A', 'T')) donnees = true;
            if (nomDeBloc(c, type, 'I', 'E', 'N', 'D')) {
                return longueur == 0 && donnees && fin + 4 == c.length;
            }
            position = fin + 4;
        }
        return false;
    }

    private static boolean enteteCoherent(byte[] c, int d, long longueur) {
        if (longueur != 13) return false;
        long largeur = entier32(c, d);
        long hauteur = entier32(c, d + 4);
        int profondeur = c[d + 8] & 0xFF;
        int couleur = c[d + 9] & 0xFF;
        boolean combinaison = switch (couleur) {
            case 0 -> profondeur == 1 || profondeur == 2 || profondeur == 4 || profondeur == 8 || profondeur == 16;
            case 3 -> profondeur == 1 || profondeur == 2 || profondeur == 4 || profondeur == 8;
            case 2, 4, 6 -> profondeur == 8 || profondeur == 16;
            default -> false;
        };
        int entrelacement = c[d + 12] & 0xFF;
        return largeur > 0 && largeur <= Integer.MAX_VALUE && hauteur > 0 && hauteur <= Integer.MAX_VALUE && combinaison
                && c[d + 10] == 0 && c[d + 11] == 0 && (entrelacement == 0 || entrelacement == 1);
    }

    private static long entier32(byte[] c, int i) {
        return ((c[i] & 0xFFL) << 24) | ((c[i + 1] & 0xFFL) << 16) | ((c[i + 2] & 0xFFL) << 8) | (c[i + 3] & 0xFFL);
    }

    private static boolean lettres(byte[] c, int i) {
        for (int k = i; k < i + 4; k++) {
            boolean lettre = (c[k] >= 'A' && c[k] <= 'Z') || (c[k] >= 'a' && c[k] <= 'z');
            if (!lettre) return false;
        }
        return true;
    }

    private static boolean nomDeBloc(byte[] c, int i, char a, char b, char d, char e) {
        return c[i] == a && c[i + 1] == b && c[i + 2] == d && c[i + 3] == e;
    }

    // ------------------------------------------------------------------ JPEG

    private static boolean jpegComplet(byte[] c) {
        // SOI + un segment + EOI : six octets au moins
        return c.length >= 6 && (c[c.length - 2] & 0xFF) == 0xFF && (c[c.length - 1] & 0xFF) == 0xD9;
    }
}
