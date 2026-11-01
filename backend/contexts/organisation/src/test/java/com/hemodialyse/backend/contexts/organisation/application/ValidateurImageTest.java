package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete;
import com.hemodialyse.backend.contexts.organisation.domain.LogoRefuse;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ValidateurImageTest {

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
    void accepte_png_decodable() throws Exception {
        assertDoesNotThrow(() -> ValidateurImage.verifierDecodable(image("png", 4, 4)));
    }

    @Test
    void rejeter_dimensions_excessives() throws Exception {
        byte[] grand = image("png", LogoDeSociete.MAX_DIMENSION + 1, 8);
        LogoRefuse refuse = assertThrows(LogoRefuse.class,
                () -> ValidateurImage.verifierDecodable(grand));
        assertEquals("DIMENSIONS", refuse.code());
    }

    @Test
    void rejeter_octets_non_image() {
        byte[] fauxPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x01, 0x02, 0x03};
        LogoRefuse refuse = assertThrows(LogoRefuse.class,
                () -> ValidateurImage.verifierDecodable(fauxPng));
        assertEquals("IMAGE_INVALID", refuse.code());
    }
}
