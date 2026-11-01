package com.hemodialyse.backend.contexts.organisation.domain;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogoDeSocieteTest {

    private static byte[] image(String format, int largeur, int hauteur) throws Exception {
        BufferedImage img = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < largeur; x++) {
            for (int y = 0; y < hauteur; y++) {
                img.setRGB(x, y, (x * 31 + y * 17) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, format, bos);
        return bos.toByteArray();
    }

    @Test
    void accepte_png_valide() throws Exception {
        LogoDeSociete logo = LogoDeSociete.valider(image("png", 4, 4));
        assertEquals("image/png", logo.contentType());
    }

    @Test
    void accepte_jpeg_valide() throws Exception {
        LogoDeSociete logo = LogoDeSociete.valider(image("jpg", 4, 4));
        assertEquals("image/jpeg", logo.contentType());
    }

    @Test
    void rejeter_fichier_vide() {
        LogoRefuse refuse = assertThrows(LogoRefuse.class, () -> LogoDeSociete.valider(new byte[0]));
        assertEquals("EMPTY", refuse.code());
    }

    @Test
    void rejeter_fichier_trop_gros() {
        LogoRefuse refuse = assertThrows(LogoRefuse.class,
                () -> LogoDeSociete.valider(new byte[LogoDeSociete.MAX_BYTES + 1]));
        assertEquals("TOO_LARGE", refuse.code());
    }

    @Test
    void rejeter_svg_texte() {
        LogoRefuse refuse = assertThrows(LogoRefuse.class, () ->
                LogoDeSociete.valider("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes()));
        assertEquals("FILE_TYPE", refuse.code());
    }
}
