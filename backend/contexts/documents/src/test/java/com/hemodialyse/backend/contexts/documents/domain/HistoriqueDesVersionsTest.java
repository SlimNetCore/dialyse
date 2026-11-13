package com.hemodialyse.backend.contexts.documents.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les règles de version et d'activation d'un gabarit de centre (DOC3, ADR 0163), sans aucune infrastructure. */
class HistoriqueDesVersionsTest {

    private static final UUID CENTRE = new UUID(0L, 1L);
    private static final UUID MODELE = new UUID(0L, 2L);
    private static final Instant MAINTENANT = Instant.parse("2026-11-13T10:00:00Z");
    private static int compteur;

    private static FichierTeleverse fichier(String contenu) {
        return FichierTeleverse.accepter("modele.jrxml", contenu.getBytes(StandardCharsets.UTF_8), 1024);
    }

    private static HistoriqueDesVersions vide() {
        return HistoriqueDesVersions.de(CENTRE, MODELE, List.of());
    }

    private static VersionDeGabarit deposer(HistoriqueDesVersions h, String contenu) {
        return h.deposer(new UUID(1L, ++compteur), fichier(contenu), null, "admin", MAINTENANT);
    }

    @Test
    void should_number_the_first_deposit_1_and_put_it_in_force() {
        HistoriqueDesVersions h = vide();

        VersionDeGabarit v = deposer(h, "<a/>");

        assertThat(v.numero()).isEqualTo(1);
        assertThat(v.active()).isTrue();
        assertThat(h.numeroEnVigueur()).hasValue(1);
        assertThat(h.depotEnAttente()).hasValueSatisfying(d -> assertThat(d.contenu()).isEqualTo("<a/>"));
    }

    @Test
    void should_replace_the_version_in_force_by_each_new_deposit_keeping_at_most_one_active() {
        HistoriqueDesVersions h = vide();
        deposer(h, "<a/>");
        deposer(h, "<b/>");
        deposer(h, "<c/>");

        assertThat(h.versions()).extracting(VersionDeGabarit::numero).containsExactly(1, 2, 3);
        assertThat(h.versions()).extracting(VersionDeGabarit::active).containsExactly(false, false, true);
        assertThat(h.numeroEnVigueur()).hasValue(3);
    }

    @Test
    void should_never_reuse_a_number_even_after_a_reset() {
        HistoriqueDesVersions h = vide();
        deposer(h, "<a/>");
        deposer(h, "<b/>");
        h.reinitialiser();

        VersionDeGabarit suivante = deposer(h, "<c/>");

        assertThat(suivante.numero()).isEqualTo(3);
    }

    @Test
    void should_put_back_in_force_an_older_version() {
        HistoriqueDesVersions h = vide();
        deposer(h, "<a/>");
        deposer(h, "<b/>");

        var activee = h.mettreEnVigueur(1);

        assertThat(activee).hasValueSatisfying(v -> assertThat(v.active()).isTrue());
        assertThat(h.numeroEnVigueur()).hasValue(1);
        assertThat(h.versions()).extracting(VersionDeGabarit::active).containsExactly(true, false);
    }

    @Test
    void should_change_nothing_when_the_version_to_put_in_force_does_not_exist() {
        HistoriqueDesVersions h = vide();
        deposer(h, "<a/>");

        assertThat(h.mettreEnVigueur(9)).isEmpty();
        assertThat(h.mettreEnVigueur(0)).isEmpty();
        assertThat(h.mettreEnVigueur(-1)).isEmpty();
        assertThat(h.numeroEnVigueur()).hasValue(1);
    }

    @Test
    void should_keep_the_history_when_going_back_to_the_delivered_template() {
        HistoriqueDesVersions h = vide();
        deposer(h, "<a/>");
        deposer(h, "<b/>");

        h.reinitialiser();

        assertThat(h.numeroEnVigueur()).isEmpty();
        assertThat(h.versions()).hasSize(2);
        h.reinitialiser();
        assertThat(h.versions()).hasSize(2);
    }

    @Test
    void should_list_the_most_recent_version_first() {
        HistoriqueDesVersions h = vide();
        deposer(h, "<a/>");
        deposer(h, "<b/>");

        assertThat(h.versionsRecentesDAbord()).extracting(VersionDeGabarit::numero).containsExactly(2, 1);
    }

    @Test
    void should_rebuild_from_versions_given_in_any_order_and_repair_several_active_ones_on_activation() {
        VersionDeGabarit v1 = new VersionDeGabarit(new UUID(2L, 1L), 1, true, "e", 1, null, "a", MAINTENANT);
        VersionDeGabarit v2 = new VersionDeGabarit(new UUID(2L, 2L), 2, true, "e", 1, null, "a", MAINTENANT);
        HistoriqueDesVersions h = HistoriqueDesVersions.de(CENTRE, MODELE, List.of(v2, v1));

        assertThat(h.versions()).extracting(VersionDeGabarit::numero).containsExactly(1, 2);
        h.mettreEnVigueur(1);
        assertThat(h.versions()).extracting(VersionDeGabarit::active).containsExactly(true, false);
    }

    @Test
    void should_record_the_digest_size_depositor_and_date_of_the_deposit() {
        HistoriqueDesVersions h = vide();
        FichierTeleverse f = fichier("<a/>");

        VersionDeGabarit v = h.deposer(new UUID(3L, 3L), f, "  Marge  ", "claire", MAINTENANT);

        assertThat(v.empreinte()).isEqualTo(f.empreinte()).hasSize(64);
        assertThat(v.tailleEnOctets()).isEqualTo(4);
        assertThat(v.deposePar()).isEqualTo("claire");
        assertThat(v.deposeLe()).isEqualTo(MAINTENANT);
        assertThat(v.commentaire()).isEqualTo("Marge");
    }

    @Test
    void should_trim_the_comment_cut_it_at_500_characters_and_keep_an_absent_one_absent() {
        HistoriqueDesVersions h = vide();

        assertThat(h.deposer(new UUID(4L, 1L), fichier("<a/>"), null, "a", MAINTENANT).commentaire()).isNull();
        assertThat(h.deposer(new UUID(4L, 2L), fichier("<a/>"), "   ", "a", MAINTENANT).commentaire()).isEmpty();
        assertThat(h.deposer(new UUID(4L, 3L), fichier("<a/>"), "x".repeat(600), "a", MAINTENANT).commentaire()).hasSize(500);
    }

    @Test
    void should_refuse_a_deposit_that_is_not_a_jrxml_file_empty_too_large_or_not_utf8() {
        byte[] ok = "<a/>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> FichierTeleverse.accepter("x.exe", ok, 1024)).isInstanceOfSatisfying(GabaritRefuse.class,
                e -> assertThat(e.code()).isEqualTo("FILE_TYPE"));
        assertThatThrownBy(() -> FichierTeleverse.accepter(null, ok, 1024)).isInstanceOfSatisfying(GabaritRefuse.class,
                e -> assertThat(e.code()).isEqualTo("FILE_TYPE"));
        assertThatThrownBy(() -> FichierTeleverse.accepter("x.jrxml", new byte[0], 1024)).isInstanceOfSatisfying(GabaritRefuse.class,
                e -> assertThat(e.code()).isEqualTo("EMPTY"));
        assertThatThrownBy(() -> FichierTeleverse.accepter("x.jrxml", new byte[1025], 1024)).isInstanceOfSatisfying(GabaritRefuse.class,
                e -> assertThat(e.code() + " / " + e.detail()).isEqualTo("TOO_LARGE / Taille maximale : 1 Ko."));
        assertThat(FichierTeleverse.accepter("X.JRXML", new byte[1024], 1024).tailleEnOctets()).isEqualTo(1024);
        FichierTeleverse pasUtf8 = FichierTeleverse.accepter("x.jrxml", new byte[]{'<', (byte) 0xE9, '>'}, 1024);
        assertThatThrownBy(pasUtf8::contenu).isInstanceOfSatisfying(GabaritRefuse.class, e -> assertThat(e.code()).isEqualTo("ENCODING"));
    }

    @Test
    void should_bound_a_page_of_the_history_without_ever_refusing_it() {
        assertThat(Pagination.de(-3, 0)).isEqualTo(new Pagination(0, 1));
        assertThat(Pagination.de(2, 1000)).isEqualTo(new Pagination(2, 100));
        List<Integer> tous = List.of(1, 2, 3, 4, 5);
        assertThat(Pagination.de(0, 2).tranche(tous)).containsExactly(1, 2);
        assertThat(Pagination.de(2, 2).tranche(tous)).containsExactly(5);
        assertThat(Pagination.de(3, 2).tranche(tous)).isEmpty();
        assertThat(Pagination.de(Integer.MAX_VALUE, 100).tranche(tous)).isEmpty();
    }
}
