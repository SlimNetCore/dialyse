package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CauseDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementReforme;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRemisEnService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateurDevenuIndisponible;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VersLeLangagePublieTest {

    private static final UUID EQUIPEMENT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final Instant T = Instant.parse("2026-10-27T08:00:00Z");

    private static EquipementMisHorsService horsService(TypeEquipement type) {
        return new EquipementMisHorsService(new EquipementId(EQUIPEMENT), new CentreId(CENTRE), type,
                new CodeEquipement("G-01"), "Générateur 01", StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE,
                CauseDIndisponibilite.MISE_HORS_SERVICE, PAR, T);
    }

    @Test
    void should_publish_a_generator_put_out_of_service_with_its_designation() {
        assertThat(VersLeLangagePublie.traduire(horsService(TypeEquipement.GENERATEUR_DIALYSE))).contains(
                new GenerateurDevenuIndisponible(EQUIPEMENT, CENTRE, "G-01", "Générateur 01", "EN_SERVICE", "HORS_SERVICE", T));
    }

    @Test
    void should_publish_a_reformed_generator_with_the_status_reforme() {
        var evenement = new EquipementReforme(new EquipementId(EQUIPEMENT), new CentreId(CENTRE),
                TypeEquipement.GENERATEUR_DIALYSE, new CodeEquipement("G-01"), "Générateur 01",
                StatutEquipement.EN_MAINTENANCE, PAR, T);

        assertThat(VersLeLangagePublie.traduire(evenement)).contains(new GenerateurDevenuIndisponible(EQUIPEMENT, CENTRE,
                "G-01", "Générateur 01", "EN_MAINTENANCE", "REFORME", T));
    }

    @Test
    void should_not_publish_other_equipment_nor_other_events() {
        assertThat(VersLeLangagePublie.traduire(horsService(TypeEquipement.AUTRE))).isEmpty();
        assertThat(VersLeLangagePublie.traduire(new EquipementRemisEnService(new EquipementId(EQUIPEMENT),
                new CentreId(CENTRE), TypeEquipement.GENERATEUR_DIALYSE, new CodeEquipement("G-01"),
                StatutEquipement.HORS_SERVICE, PAR, T))).isEmpty();
        assertThat(VersLeLangagePublie.traduire("autre")).isEmpty();
    }
}
