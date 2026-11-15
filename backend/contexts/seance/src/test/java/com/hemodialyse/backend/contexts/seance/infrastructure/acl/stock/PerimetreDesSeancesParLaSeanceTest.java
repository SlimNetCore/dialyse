package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees.PlaceDeSeancePubliee;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.PerimetreDesSeancesFourni.DemiJourneeFournie;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.PerimetreDesSeancesFourni.PerimetreFourni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Traduction table-driven de la place publiée par la Séance en périmètre fourni au Stock (ADR 0096, KB-SEA-68 et 69 reproduits ; ADR 0191). */
class PerimetreDesSeancesParLaSeanceTest {

    private static final UUID CENTRE = new UUID(0, 1);
    private static final UUID SEANCE = new UUID(1, 1);

    @ParameterizedTest(name = "code={0} libelle={1} donne {2}")
    @CsvSource(nullValues = "NULL", value = {
            "M1, matin, MATIN",
            "MATIN, -, MATIN",
            "AM, Après-midi, APRES_MIDI",
            "MIDI, x, APRES_MIDI",
            "S3, Soirée, APRES_MIDI",
            "P2, Matin puis soir, APRES_MIDI",
            "C1, Première vague, NULL",
            "NULL, NULL, NULL"})
    void la_demi_journee_se_lit_dans_le_code_ou_le_libelle_du_creneau(String code, String libelle, String attendue) {
        DemiJourneeFournie demi = attendue == null ? null : DemiJourneeFournie.valueOf(attendue);

        assertThat(PerimetreDesSeancesParLaSeance.demiJournee(code, libelle)).isEqualTo(demi);
    }

    @Test
    void une_place_inconnue_donne_la_sortie_de_la_journee() {
        var traducteur = new PerimetreDesSeancesParLaSeance((centre, seance) -> Optional.empty());

        assertThat(traducteur.perimetreDe(CENTRE, SEANCE)).isEqualTo(PerimetreFourni.journee());
    }

    @Test
    void la_salle_et_la_demi_journee_de_la_place_forment_le_perimetre() {
        var traducteur = new PerimetreDesSeancesParLaSeance(
                (centre, seance) -> Optional.of(new PlaceDeSeancePubliee("S-A", "C1", "Matin")));

        assertThat(traducteur.perimetreDe(CENTRE, SEANCE)).isEqualTo(new PerimetreFourni("S-A", DemiJourneeFournie.MATIN));
    }

    @Test
    void le_statut_facturee_est_celui_publie_par_la_seance_pour_le_centre() {
        var traducteur = new StatutDesSeancesParLaSeance((centre, seance) -> centre.equals(CENTRE) && seance.equals(SEANCE));

        assertThat(traducteur.estFacturee(CENTRE, SEANCE)).isTrue();
        assertThat(traducteur.estFacturee(new UUID(0, 2), SEANCE)).isFalse();
    }
}
