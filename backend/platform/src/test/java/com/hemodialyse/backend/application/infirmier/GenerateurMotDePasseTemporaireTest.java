package com.hemodialyse.backend.application.infirmier;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateurMotDePasseTemporaireTest {

    private final GenerateurMotDePasseTemporaire generateur = new GenerateurMotDePasseTemporaire();

    @Test
    void a_password_has_the_expected_length_and_mixes_the_four_character_classes() {
        for (int i = 0; i < 200; i++) {
            String mdp = generateur.generer();

            assertEquals(GenerateurMotDePasseTemporaire.LONGUEUR, mdp.length());
            assertTrue(mdp.chars().anyMatch(Character::isUpperCase), mdp);
            assertTrue(mdp.chars().anyMatch(Character::isLowerCase), mdp);
            assertTrue(mdp.chars().anyMatch(Character::isDigit), mdp);
            assertTrue(mdp.chars().anyMatch(c -> "!#$%&*+-=?@".indexOf(c) >= 0), mdp);
        }
    }

    @Test
    void ambiguous_characters_are_never_used_and_passwords_do_not_repeat() {
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String mdp = generateur.generer();

            assertFalse(mdp.chars().anyMatch(c -> "0O1lI".indexOf(c) >= 0), mdp);
            assertTrue(vus.add(mdp), "mot de passe répété : " + mdp);
        }
    }
}
