package com.hemodialyse.backend.contexts.documents.contrat;

import com.hemodialyse.backend.contexts.documents.application.GabaritPersonnaliseDuCentre.Gabarit;
import com.hemodialyse.backend.contexts.documents.domain.FichierTeleverse;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersions;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersionsRepository;
import com.hemodialyse.backend.contexts.documents.domain.VersionDeGabarit;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de l'historique des versions de gabarit : exécuté sur le double en mémoire ET sur l'adapter JDBC (H2), pour garantir que le
 * double est fidèle. Une seule version en vigueur à la fois, numéros qui se suivent, contenu lu à part, isolement par centre et par modèle.
 */
abstract class HistoriqueDesVersionsContrat {

    static final UUID D = UUID.fromString("99999163-0000-0000-0000-00000000000d");
    static final UUID E = UUID.fromString("99999163-0000-0000-0000-00000000000e");
    static final UUID M1 = UUID.fromString("99999163-0000-0000-0000-000000000a01");
    static final UUID M2 = UUID.fromString("99999163-0000-0000-0000-000000000a02");
    static final UUID M3 = UUID.fromString("99999163-0000-0000-0000-000000000a03");
    private static final Instant QUAND = Instant.parse("2026-11-13T10:00:00.123456Z");
    private static int compteur;

    protected abstract HistoriqueDesVersionsRepository historiques();

    /** Le même objet, vu comme le port lu par le moteur de rendu. */
    protected abstract com.hemodialyse.backend.contexts.documents.application.GabaritPersonnaliseDuCentre pourLeMoteur();

    private VersionDeGabarit deposer(UUID centre, UUID modele, String contenu, String commentaire) {
        HistoriqueDesVersions h = historiques().historique(centre, modele);
        VersionDeGabarit v = h.deposer(new UUID(0x163L, ++compteur), FichierTeleverse.accepter("x.jrxml",
                contenu.getBytes(StandardCharsets.UTF_8), 4096), commentaire, "claire", QUAND);
        historiques().enregistrer(h);
        return v;
    }

    @Test
    void should_start_empty() {
        HistoriqueDesVersions h = historiques().historique(D, M1);

        assertThat(h.versions()).isEmpty();
        assertThat(h.numeroEnVigueur()).isEmpty();
        assertThat(historiques().contenuEnVigueur(D, M1)).isEmpty();
        assertThat(pourLeMoteur().actif(D, M1)).isEmpty();
    }

    @Test
    void should_store_a_deposit_with_its_metadata_and_its_content() {
        VersionDeGabarit v = deposer(D, M1, "<é/>", "Marge");

        VersionDeGabarit relue = historiques().historique(D, M1).version(1).orElseThrow();
        assertThat(relue.id()).isEqualTo(v.id());
        assertThat(relue.active()).isTrue();
        assertThat(relue.empreinte()).isEqualTo(v.empreinte());
        assertThat(relue.tailleEnOctets()).isEqualTo("<é/>".getBytes(StandardCharsets.UTF_8).length);
        assertThat(relue.commentaire()).isEqualTo("Marge");
        assertThat(relue.deposePar()).isEqualTo("claire");
        assertThat(relue.deposeLe()).isEqualTo(QUAND);
        assertThat(historiques().contenuDe(D, M1, 1)).hasValue("<é/>");
        assertThat(historiques().contenuEnVigueur(D, M1)).hasValue("<é/>");
    }

    @Test
    void should_keep_an_absent_comment_absent_and_a_blank_one_blank() {
        deposer(D, M1, "<a/>", null);
        deposer(D, M1, "<b/>", "");

        assertThat(historiques().historique(D, M1).version(1).orElseThrow().commentaire()).isNull();
        assertThat(historiques().historique(D, M1).version(2).orElseThrow().commentaire()).isEmpty();
    }

    @Test
    void should_write_the_change_of_the_version_in_force_without_touching_the_content() {
        deposer(D, M1, "<a/>", null);
        deposer(D, M1, "<b/>", null);

        HistoriqueDesVersions h = historiques().historique(D, M1);
        h.mettreEnVigueur(1);
        historiques().enregistrer(h);

        HistoriqueDesVersions relu = historiques().historique(D, M1);
        assertThat(relu.versions()).extracting(VersionDeGabarit::active).containsExactly(true, false);
        assertThat(historiques().contenuEnVigueur(D, M1)).hasValue("<a/>");
        assertThat(historiques().contenuDe(D, M1, 2)).hasValue("<b/>");
        assertThat(pourLeMoteur().actif(D, M1)).hasValue(new Gabarit(1, "<a/>"));
    }

    @Test
    void should_keep_every_version_when_going_back_to_the_delivered_template() {
        deposer(D, M1, "<a/>", null);
        HistoriqueDesVersions h = historiques().historique(D, M1);
        h.reinitialiser();
        historiques().enregistrer(h);

        assertThat(historiques().historique(D, M1).versions()).hasSize(1);
        assertThat(historiques().historique(D, M1).numeroEnVigueur()).isEmpty();
        assertThat(pourLeMoteur().actif(D, M1)).isEmpty();
        assertThat(deposer(D, M1, "<b/>", null).numero()).isEqualTo(2);
    }

    @Test
    void should_isolate_the_histories_by_centre_and_by_model() {
        deposer(D, M1, "<d1/>", null);
        deposer(E, M3, "<e1/>", null);
        deposer(D, M2, "<d2/>", null);

        assertThat(historiques().contenuEnVigueur(D, M1)).hasValue("<d1/>");
        assertThat(historiques().contenuEnVigueur(E, M3)).hasValue("<e1/>");
        assertThat(historiques().contenuEnVigueur(E, M1)).isEmpty();
        assertThat(historiques().contenuEnVigueur(D, M2)).hasValue("<d2/>");
        assertThat(historiques().historique(D, M1).versions()).hasSize(1);
        assertThat(historiques().contenuDe(E, M2, 1)).isEqualTo(Optional.empty());
    }

    @Test
    void should_remove_the_whole_history_of_a_model_only() {
        deposer(D, M1, "<a/>", null);
        deposer(D, M1, "<b/>", null);
        deposer(D, M2, "<c/>", null);

        historiques().retirerTout(M1);

        assertThat(historiques().historique(D, M1).versions()).isEmpty();
        assertThat(historiques().contenuEnVigueur(D, M2)).hasValue("<c/>");
    }
}
