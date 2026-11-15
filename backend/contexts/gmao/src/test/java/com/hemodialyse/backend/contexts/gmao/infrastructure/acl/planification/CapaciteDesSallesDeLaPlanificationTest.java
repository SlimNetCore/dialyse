package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.gmao.application.equipement.SalleADCapaciteMaximale;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.CapaciteDesSallesFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ACL de la GMAO vers la capacité des salles (ADR 0192) : arguments transmis tels quels, refus traduit avec le même message. */
class CapaciteDesSallesDeLaPlanificationTest {

    private static final UUID CENTRE = new UUID(0xC1, 1);
    private static final UUID SALLE = new UUID(0xC1, 2);
    private static final UUID EQUIPEMENT = new UUID(0xC1, 3);

    @Test
    void should_transmettre_le_centre_la_salle_et_l_equipement_tels_quels() {
        List<UUID> recus = new ArrayList<>();
        CapaciteDesSallesFournie fournie = (centre, salle, equipement) -> recus.addAll(java.util.Arrays.asList(centre, salle, equipement));

        new CapaciteDesSallesDeLaPlanification(fournie).verifierAffectation(new CentreId(CENTRE), new SalleId(SALLE), new EquipementId(EQUIPEMENT));

        assertThat(recus).containsExactly(CENTRE, SALLE, EQUIPEMENT);
    }

    @Test
    void should_transmettre_une_salle_et_un_equipement_absents_comme_nuls() {
        List<UUID> recus = new ArrayList<>();
        CapaciteDesSallesFournie fournie = (centre, salle, equipement) -> {
            recus.add(centre);
            recus.add(salle);
            recus.add(equipement);
        };

        new CapaciteDesSallesDeLaPlanification(fournie).verifierAffectation(new CentreId(CENTRE), null, null);

        assertThat(recus).containsExactly(CENTRE, null, null);
    }

    @Test
    void should_traduire_le_refus_de_la_planification_en_refus_de_la_gmao_avec_le_meme_message() {
        CapaciteDesSallesFournie fournie = (centre, salle, equipement) -> {
            throw new CapaciteDesSallesFournie.CapaciteDeLaSalleAtteinte("La salle « S1 » est à sa capacité maximale (2 générateur(s))");
        };

        assertThatThrownBy(() -> new CapaciteDesSallesDeLaPlanification(fournie)
                .verifierAffectation(new CentreId(CENTRE), new SalleId(SALLE), null))
                .isInstanceOf(SalleADCapaciteMaximale.class)
                .hasMessage("La salle « S1 » est à sa capacité maximale (2 générateur(s))");
    }
}
