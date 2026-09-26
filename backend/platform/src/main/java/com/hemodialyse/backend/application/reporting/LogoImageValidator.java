package com.hemodialyse.backend.application.reporting;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Valide le logo d'une société avant de l'accepter : le fichier est imprimé dans tous les documents et affiché
 * dans l'interface, il doit donc être une vraie image raster et rien d'autre.
 * <ul>
 *   <li>PNG ou JPEG uniquement, reconnus à leur <i>contenu</i> (signature) et non à l'extension ni au type
 *       déclaré par le client ; le SVG (script embarqué possible) est refusé ;</li>
 *   <li>taille bornée, et dimensions bornées <i>avant</i> décodage complet (anti « bombe de décompression ») ;</li>
 *   <li>l'image doit être décodable.</li>
 * </ul>
 * Classe pure : aucune dépendance Spring ni JPA.
 */
public final class LogoImageValidator {

    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_DIMENSION = 2000;

    private static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    public Result validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Result.error("EMPTY", "Le fichier est vide.");
        }
        if (bytes.length > MAX_BYTES) {
            return Result.error("TOO_LARGE", "Taille maximale : " + MAX_BYTES / 1024 + " Ko.");
        }
        String contentType = sniff(bytes);
        if (contentType == null) {
            return Result.error("FILE_TYPE", "Seuls les fichiers PNG et JPEG sont acceptés.");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return Result.error("IMAGE_INVALID", "Image illisible.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    return Result.error("DIMENSIONS", "Dimensions maximales : " + MAX_DIMENSION + " x " + MAX_DIMENSION + " px.");
                }
                reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Result.error("IMAGE_INVALID", "Image illisible ou corrompue.");
        }
        return Result.ok(contentType);
    }

    public record Result(String contentType, String errorCode, String detail) {
        static Result ok(String contentType) {
            return new Result(contentType, null, null);
        }

        static Result error(String code, String detail) {
            return new Result(null, code, detail);
        }

        public boolean accepted() {
            return errorCode == null;
        }
    }
}
