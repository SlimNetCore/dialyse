package com.hemodialyse.backend.contexts.planification.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.CapaciteDesSallesFournie;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesSalles;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Port fourni à la GMAO par la Planification (ADR 0192) : délégation pure, refus traduit avec le même message. */
class CapaciteDesSallesFournieParLaPlanificationTest {

    private static final UUID CENTRE = new UUID(0xC2, 1);
    private static final UUID SALLE = new UUID(0xC2, 2);

    @Test
    void should_deleguer_les_arguments_sans_changement() {
        List<UUID> recus = new ArrayList<>();
        CapaciteDesSalles capacites = (centre, salle, equipement) -> {
            recus.add(centre);
            recus.add(salle);
            recus.add(equipement);
        };

        new CapaciteDesSallesFournieParLaPlanification(capacites).verifierAffectation(CENTRE, SALLE, null);

        assertThat(recus).containsExactly(CENTRE, SALLE, null);
    }

    @Test
    void should_traduire_le_refus_de_capacite_avec_le_meme_message() {
        CapaciteDesSalles capacites = (centre, salle, equipement) -> {
            throw new CapaciteDesSalles.CapaciteDeLaSalleDepassee("S1", 2);
        };

        assertThatThrownBy(() -> new CapaciteDesSallesFournieParLaPlanification(capacites).verifierAffectation(CENTRE, SALLE, null))
                .isInstanceOf(CapaciteDesSallesFournie.CapaciteDeLaSalleAtteinte.class)
                .hasMessage(new CapaciteDesSalles.CapaciteDeLaSalleDepassee("S1", 2).getMessage());
    }
}
