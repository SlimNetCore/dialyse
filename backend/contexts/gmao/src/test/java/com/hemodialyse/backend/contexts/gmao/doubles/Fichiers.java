package com.hemodialyse.backend.contexts.gmao.doubles;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Petits fichiers valides (PDF, PNG, JPEG) fabriqués octet par octet pour les tests. */
public final class Fichiers {

    private Fichiers() {
    }

    public static byte[] pdf() {
        return octets("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n");
    }

    /** PDF valide d'exactement {@code taille} octets : le remplissage se glisse avant le marqueur de fin. */
    public static byte[] pdf(int taille) {
        byte[] debut = octets("%PDF-1.4\n");
        byte[] fin = octets("%%EOF\n");
        byte[] r = new byte[taille];
        System.arraycopy(debut, 0, r, 0, debut.length);
        for (int i = debut.length; i < taille - fin.length; i++) r[i] = (byte) ('a' + (i % 26));
        System.arraycopy(fin, 0, r, taille - fin.length, fin.length);
        return r;
    }

    public static byte[] png() {
        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        flux.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        bloc(flux, "IHDR", new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0});
        bloc(flux, "IDAT", new byte[]{0x78, (byte) 0x9C, 0x63, 0x60, 0x60, 0x60, 0, 0, 0, 4, 0, 1});
        bloc(flux, "IEND", new byte[0]);
        return flux.toByteArray();
    }

    /** SOI, un segment APP0, EOI. */
    public static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 4, 'J', 'F', (byte) 0xFF, (byte) 0xD9};
    }

    /** Bloc PNG : longueur, type, données, somme de contrôle. */
    public static void bloc(ByteArrayOutputStream flux, String type, byte[] donnees) {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(donnees);
        flux.writeBytes(entier(donnees.length));
        flux.writeBytes(t);
        flux.writeBytes(donnees);
        flux.writeBytes(entier(crc.getValue()));
    }

    public static byte[] entier(long v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    public static byte[] octets(String texte) {
        return texte.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] sansLesDerniers(byte[] source, int n) {
        return Arrays.copyOf(source, source.length - n);
    }

    public static byte[] suivi(byte[] source, String suite) {
        byte[] s = octets(suite);
        byte[] r = Arrays.copyOf(source, source.length + s.length);
        System.arraycopy(s, 0, r, source.length, s.length);
        return r;
    }
}
