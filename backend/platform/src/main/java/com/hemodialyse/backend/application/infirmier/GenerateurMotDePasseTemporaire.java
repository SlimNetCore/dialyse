package com.hemodialyse.backend.application.infirmier;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Génère le mot de passe temporaire d'un compte créé depuis une fiche infirmier : 14 caractères mêlant majuscules,
 * minuscules, chiffres et symboles, sans caractères ambigus (0/O, 1/l/I). Il n'est communiqué qu'une fois à
 * l'administrateur et n'est jamais journalisé.
 */
@Component
public class GenerateurMotDePasseTemporaire {

    static final int LONGUEUR = 14;
    private static final String MAJUSCULES = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULES = "abcdefghijkmnpqrstuvwxyz";
    private static final String CHIFFRES = "23456789";
    private static final String SYMBOLES = "!#$%&*+-=?@";
    private static final String TOUS = MAJUSCULES + MINUSCULES + CHIFFRES + SYMBOLES;

    private final SecureRandom random = new SecureRandom();

    public String generer() {
        List<Character> caracteres = new ArrayList<>();
        caracteres.add(tirer(MAJUSCULES));
        caracteres.add(tirer(MINUSCULES));
        caracteres.add(tirer(CHIFFRES));
        caracteres.add(tirer(SYMBOLES));
        while (caracteres.size() < LONGUEUR) caracteres.add(tirer(TOUS));
        Collections.shuffle(caracteres, random);
        StringBuilder mdp = new StringBuilder();
        caracteres.forEach(mdp::append);
        return mdp.toString();
    }

    private char tirer(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
