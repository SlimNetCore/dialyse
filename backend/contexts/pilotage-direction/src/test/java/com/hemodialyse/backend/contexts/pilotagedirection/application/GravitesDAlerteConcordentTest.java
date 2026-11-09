package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.GraviteDAlerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Revue PD3 m4 : {@code GraviteDAlerte} (domaine) et {@code AlertesDeLaDirection.Gravite} (langage publié) sont traduites par
 * {@code valueOf(name())} ; une constante renommée d'un seul côté casserait à l'exécution. Ce test la fait casser ici, dans l'ordre déclaré.
 */
class GravitesDAlerteConcordentTest {

    @Test
    void les_deux_enumerations_de_gravite_portent_les_memes_constantes_dans_le_meme_ordre() {
        List<String> domaine = Arrays.stream(GraviteDAlerte.values()).map(Enum::name).toList();
        List<String> publie = Arrays.stream(AlertesDeLaDirection.Gravite.values()).map(Enum::name).toList();
        assertEquals(domaine, publie);
    }
}
