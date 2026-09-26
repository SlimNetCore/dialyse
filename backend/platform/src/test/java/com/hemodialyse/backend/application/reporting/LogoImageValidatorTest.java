package com.hemodialyse.backend.application.reporting;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoImageValidatorTest {

    private final LogoImageValidator validator = new LogoImageValidator();

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void unPngEtUnJpegValidesSontAcceptes() throws Exception {
        var png = validator.validate(image("png", 40, 20));
        var jpeg = validator.validate(image("jpg", 40, 20));
        assertTrue(png.accepted());
        assertEquals("image/png", png.contentType());
        assertTrue(jpeg.accepted());
        assertEquals("image/jpeg", jpeg.contentType());
    }

    @Test
    void unSvgEstRefuseMemeAvecUneExtensionOuUnTypeImage() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        assertEquals("FILE_TYPE", validator.validate(svg).errorCode());
    }

    @Test
    void unExecutableOuUnHtmlSontRefuses() {
        assertEquals("FILE_TYPE", validator.validate("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1)).errorCode());
        assertEquals("FILE_TYPE", validator.validate("<html><script>x</script></html>".getBytes(StandardCharsets.UTF_8)).errorCode());
    }

    @Test
    void uneSignaturePngSansImageValideEstRefusee() throws Exception {
        byte[] fake = Arrays.copyOf(image("png", 10, 10), 40); // en-tête réel puis fichier tronqué
        assertEquals("IMAGE_INVALID", validator.validate(fake).errorCode());
    }

    @Test
    void unFichierVideOuTropVolumineuxEstRefuse() throws Exception {
        assertEquals("EMPTY", validator.validate(new byte[0]).errorCode());
        assertEquals("EMPTY", validator.validate(null).errorCode());
        byte[] big = new byte[LogoImageValidator.MAX_BYTES + 1];
        System.arraycopy(image("png", 10, 10), 0, big, 0, 20);
        assertEquals("TOO_LARGE", validator.validate(big).errorCode());
    }

    @Test
    void desDimensionsExcessivesSontRefuseesAvantDecodage() throws Exception {
        var result = validator.validate(image("png", LogoImageValidator.MAX_DIMENSION + 1, 10));
        assertEquals("DIMENSIONS", result.errorCode());
        assertNull(result.contentType());
    }
}
