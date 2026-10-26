package com.hemodialyse.backend.contexts.planification.infrastructure;

import com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse.GenerateurDeMotDePasseTemporaire;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mot de passe temporaire d'un compte créé depuis une fiche (RG-INF-011).
 */
class GenerateurDeMotDePasseTemporaireTest {

    private final GenerateurDeMotDePasseTemporaire generateur = new GenerateurDeMotDePasseTemporaire();

    @Test
    void should_generate_fourteen_characters_mixing_the_four_classes_without_ambiguous_ones() {
        for (int i = 0; i < 200; i++) {
            String motDePasse = generateur.generer();

            assertThat(motDePasse).hasSize(GenerateurDeMotDePasseTemporaire.LONGUEUR);
            assertThat(motDePasse).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*[0-9].*").matches(".*[!#$%&*+\\-=?@].*");
            assertThat(motDePasse).doesNotContainPattern("[0O1lI]");
        }
    }

    @Test
    void should_not_repeat_itself_and_not_always_start_with_an_upper_case_letter() {
        Set<String> distincts = new HashSet<>();
        int sansMajusculeEnTete = 0;
        for (int i = 0; i < 100; i++) {
            String motDePasse = generateur.generer();
            distincts.add(motDePasse);
            if (!Character.isUpperCase(motDePasse.charAt(0))) {
                sansMajusculeEnTete++;
            }
        }

        assertThat(distincts).hasSize(100);
        assertThat(sansMajusculeEnTete).isPositive();
    }
}
