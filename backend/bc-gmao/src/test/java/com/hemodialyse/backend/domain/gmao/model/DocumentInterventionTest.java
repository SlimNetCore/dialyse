package com.hemodialyse.backend.domain.gmao.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentInterventionTest {

    private static final byte[] PDF = "%PDF-1.7 contenu".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

    private DocumentIntervention creer(String nom, byte[] contenu) {
        return DocumentIntervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeDocumentIntervention.FACTURE,
                nom, contenu, UUID.randomUUID());
    }

    @Test
    void should_detect_the_real_type_from_the_content_not_from_the_name() {
        assertEquals("application/pdf", creer("facture.png", PDF).contentType());
        assertEquals("image/png", creer("photo.pdf", PNG).contentType());
        assertEquals("image/jpeg", creer("photo.jpg", JPEG).contentType());
    }

    @Test
    void should_reject_unknown_formats_empty_and_oversized_files() {
        assertThrows(IllegalArgumentException.class, () -> creer("script.pdf", "<html>x</html>".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> creer("vide.pdf", new byte[0]));
        byte[] trop = new byte[(int) DocumentIntervention.TAILLE_MAX_OCTETS + 1];
        System.arraycopy(PDF, 0, trop, 0, PDF.length);
        assertThrows(IllegalArgumentException.class, () -> creer("gros.pdf", trop));
    }

    @Test
    void should_keep_only_a_safe_file_name() {
        assertEquals("facture.pdf", creer("C:\\Users\\bob\\..\\facture.pdf", PDF).nom());
        assertEquals("facture.pdf", creer("../../etc/facture.pdf", PDF).nom());
        assertEquals("document", creer("  ", PDF).nom());
        assertEquals("ab.pdf", creer("a\"<b>.pdf", PDF).nom());
    }
}
