package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.application.RappelsDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.RappelDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.SeancesARegulariser;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du port {@link RappelsDeRegularisation} : un seul rappel par centre et par jour du centre (KB-SEA-35). Le double en mémoire
 * et l'ACL vers notifications.v1 passent le même jeu (PL13b).
 */
abstract class RappelsContrat {

    static final CentreId CENTRE_A = new CentreId(new UUID(0, 0xA));
    static final CentreId CENTRE_B = new CentreId(new UUID(0, 0xB));
    static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    protected abstract RappelsDeRegularisation rappels();

    /** Les alertes réellement émises, au format « centre|jour|nombre|plusAncienne ». */
    protected abstract List<String> emissions();

    private static RappelDeRegularisation rappel(CentreId centre, LocalDate jour, long total, LocalDate plusAncienne) {
        return new RappelDeRegularisation(centre, jour, new SeancesARegulariser(total, plusAncienne));
    }

    @Test
    void le_premier_rappel_du_centre_pour_le_jour_est_emis_avec_son_nombre_et_sa_plus_ancienne() {
        assertThat(rappels().rappeler(rappel(CENTRE_A, JOUR, 3, JOUR.minusDays(4)))).isTrue();

        assertThat(emissions()).containsExactly(CENTRE_A.valeur() + "|2026-06-15|3|2026-06-11");
    }

    @Test
    void un_second_rappel_du_meme_centre_le_meme_jour_n_est_pas_emis() {
        rappels().rappeler(rappel(CENTRE_A, JOUR, 3, JOUR.minusDays(4)));

        assertThat(rappels().rappeler(rappel(CENTRE_A, JOUR, 2, JOUR.minusDays(1)))).isFalse();

        assertThat(emissions()).hasSize(1);
    }

    @Test
    void le_lendemain_et_un_autre_centre_sont_rappeles() {
        rappels().rappeler(rappel(CENTRE_A, JOUR, 3, JOUR.minusDays(4)));

        assertThat(rappels().rappeler(rappel(CENTRE_A, JOUR.plusDays(1), 3, JOUR.minusDays(3)))).isTrue();
        assertThat(rappels().rappeler(rappel(CENTRE_B, JOUR, 1, JOUR.minusDays(1)))).isTrue();

        assertThat(emissions()).hasSize(3);
    }

    @Test
    void une_plus_ancienne_inconnue_est_transmise_vide() {
        rappels().rappeler(rappel(CENTRE_A, JOUR, 1, null));

        assertThat(emissions()).containsExactly(CENTRE_A.valeur() + "|2026-06-15|1|null");
    }
}
