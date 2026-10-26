package com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse;

import com.hemodialyse.backend.contexts.planification.application.GenerateurDeMotDePasse;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mot de passe temporaire d'un compte créé depuis une fiche (RG-INF-011) : 14 caractères mêlant majuscules, minuscules, chiffres et
 * symboles, sans caractères ambigus (0/O, 1/l/I). Il n'est communiqué qu'une fois à l'administrateur et n'est jamais journalisé.
 */
public class GenerateurDeMotDePasseTemporaire implements GenerateurDeMotDePasse {

    public static final int LONGUEUR = 14;
    private static final String MAJUSCULES = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULES = "abcdefghijkmnpqrstuvwxyz";
    private static final String CHIFFRES = "23456789";
    private static final String SYMBOLES = "!#$%&*+-=?@";
    private static final String TOUS = MAJUSCULES + MINUSCULES + CHIFFRES + SYMBOLES;

    private final SecureRandom hasard = new SecureRandom();

    @Override
    public String generer() {
        List<Character> caracteres = new ArrayList<>();
        caracteres.add(tirer(MAJUSCULES));
        caracteres.add(tirer(MINUSCULES));
        caracteres.add(tirer(CHIFFRES));
        caracteres.add(tirer(SYMBOLES));
        while (caracteres.size() < LONGUEUR) {
            caracteres.add(tirer(TOUS));
        }
        Collections.shuffle(caracteres, hasard);
        StringBuilder motDePasse = new StringBuilder();
        caracteres.forEach(motDePasse::append);
        return motDePasse.toString();
    }

    private char tirer(String alphabet) {
        return alphabet.charAt(hasard.nextInt(alphabet.length()));
    }
}
