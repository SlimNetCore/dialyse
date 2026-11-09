package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.BaseDeLaCapaciteTheorique;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteTheoriqueDesCentres;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteTheoriqueDesCentres.Capacite;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteTheoriqueDesCentres.Niveau;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** SE7f (ADR 0100) : la capacité théorique publiée compose la base de la Planification et le calcul du domaine, pour la Direction. */
class ConsulterLaCapaciteTheoriqueTest {

    private static final UUID CENTRE = new UUID(0, 0xA);
    private static final LocalDate DU = LocalDate.of(2026, 1, 1);
    private static final LocalDate AU = LocalDate.of(2026, 1, 31);

    private static CapaciteTheoriqueDesCentres capacite(int creneaux, int parPoste, long fileActive) {
        BaseDeLaCapaciteTheorique base = (centre, du, au) -> new BaseDeLaCapaciteTheorique.Base(creneaux, parPoste, fileActive);
        return new ConsulterLaCapaciteTheorique(base);
    }

    @Test
    void should_compute_the_reference_example_from_the_base_of_the_centre_and_the_generators_in_service() {
        Capacite c = capacite(2, 3, 42).duCentre(CENTRE, 16, DU, AU);

        assertThat(c.generateurs()).isEqualTo(16);
        assertThat(c.generateursSecours()).isEqualTo(2);
        assertThat(c.postesActifs()).isEqualTo(14);
        assertThat(c.series()).isEqualTo(2);
        assertThat(c.patientsParPosteEtSerie()).isEqualTo(3);
        assertThat(c.capacite()).isEqualTo(84);
        assertThat(c.fileActive()).isEqualTo(42);
        assertThat(c.tauxOccupation()).isEqualByComparingTo("50.0");
        assertThat(c.niveau()).isEqualTo(Niveau.MARGE);
        assertThat(c.atteinte()).isFalse();
    }

    @Test
    void should_consolidate_centres_and_translate_every_level_both_ways() {
        var service = capacite(2, 3, 0);
        Capacite a = service.duCentre(CENTRE, 16, DU, AU);
        Capacite b = new Capacite(8, 1, 7, 2, 3, 42, 10, new BigDecimal("23.8"), Niveau.MARGE);
        Capacite atteinte = capacite(2, 3, 84).duCentre(CENTRE, 16, DU, AU);
        Capacite sans = capacite(0, 3, 10).duCentre(CENTRE, 16, DU, AU);

        Capacite total = service.consolidee(List.of(a, b));

        assertThat(total.generateurs()).isEqualTo(24);
        assertThat(total.capacite()).isEqualTo(126);
        assertThat(total.fileActive()).isEqualTo(10);
        assertThat(total.series()).isZero();
        assertThat(atteinte.niveau()).isEqualTo(Niveau.ATTEINTE);
        assertThat(atteinte.atteinte()).isTrue();
        assertThat(sans.niveau()).isEqualTo(Niveau.SANS_CAPACITE);
        assertThat(sans.tauxOccupation()).isNull();
        assertThat(service.consolidee(List.of()).niveau()).isEqualTo(Niveau.SANS_CAPACITE);
        assertThat(CapaciteTheoriqueDesCentres.GENERATEURS_PAR_SECOURS).isEqualTo(8);
        assertThat(CapaciteTheoriqueDesCentres.SEUIL_PROCHE_POURCENT).isEqualTo(90);
    }
}
