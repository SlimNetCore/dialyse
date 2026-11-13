package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import com.hemodialyse.backend.contexts.documents.application.GabaritsLivres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Les gabarits livrés, lus dans le classpath sous {@code reports/<nom>.jrxml} : seul le nom de fichier du chemin de modèle compte, et il
 * doit être {@code [A-Za-z0-9_-]+.jrxml} (aucun chemin disque, aucun {@code ..}).
 */
public class GabaritsLivresDuClasspath implements GabaritsLivres {

    private static final Logger journal = LoggerFactory.getLogger(GabaritsLivresDuClasspath.class);
    private static final Pattern NOM_SUR = Pattern.compile("[A-Za-z0-9_\\-]+\\.jrxml");

    @Override
    public Optional<String> source(String cheminDuGabarit) {
        String nom = nomSur(cheminDuGabarit);
        if (nom == null) {
            return Optional.empty();
        }
        ClassPathResource ressource = new ClassPathResource("reports/" + nom);
        if (!ressource.exists()) {
            return Optional.empty();
        }
        try (var flux = ressource.getInputStream()) {
            return Optional.of(new String(flux.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            journal.warn("Modèle d'origine illisible : {}", nom, e);
            return Optional.empty();
        }
    }

    /** Nom de fichier sûr (sans chemin) d'un chemin de modèle, ou {@code null}. */
    static String nomSur(String chemin) {
        if (chemin == null) {
            return null;
        }
        String nettoye = chemin.replace('\\', '/');
        String nom = nettoye.substring(nettoye.lastIndexOf('/') + 1);
        return NOM_SUR.matcher(nom).matches() ? nom : null;
    }
}
