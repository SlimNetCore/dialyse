package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete;
import com.hemodialyse.backend.contexts.organisation.domain.LogoRefuse;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

/**
 * Vérifie qu'un logo est une vraie image décodable aux dimensions bornées (anti
 * « bombe de décompression » : dimensions lues avant décodage complet).
 * <p>
 * Vit dans la couche application car ArchUnit interdit {@code javax.*} dans le domaine.
 * Le contrôle léger (vide, taille, signature PNG/JPEG) reste dans le domaine
 * ({@link LogoDeSociete#valider(byte[])}).
 */
public final class ValidateurImage {

    private ValidateurImage() {
    }

    public static void verifierDecodable(byte[] bytes) {
        try (ByteArrayInputStream flux = new ByteArrayInputStream(bytes);
             ImageInputStream in = ImageIO.createImageInputStream(flux)) {
            Iterator<ImageReader> lecteurs = ImageIO.getImageReaders(in);
            if (!lecteurs.hasNext()) {
                throw new LogoRefuse("IMAGE_INVALID", "Image illisible.");
            }
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(in, true, true);
                int largeur = lecteur.getWidth(0);
                int hauteur = lecteur.getHeight(0);
                if (largeur <= 0 || hauteur <= 0
                        || largeur > LogoDeSociete.MAX_DIMENSION
                        || hauteur > LogoDeSociete.MAX_DIMENSION) {
                    throw new LogoRefuse("DIMENSIONS",
                            "Dimensions maximales : " + LogoDeSociete.MAX_DIMENSION
                                    + " x " + LogoDeSociete.MAX_DIMENSION + " px.");
                }
                lecteur.read(0);
            } finally {
                lecteur.dispose();
            }
        } catch (java.io.IOException | RuntimeException e) {
            if (e instanceof LogoRefuse refuse) throw refuse;
            throw new LogoRefuse("IMAGE_INVALID", "Image illisible ou corrompue.");
        }
    }
}
