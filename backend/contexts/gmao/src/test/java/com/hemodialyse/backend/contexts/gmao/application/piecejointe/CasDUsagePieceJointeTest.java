package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionIntrouvable;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.FichierRefuse;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionFermee;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeAjoutee;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRetiree;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.QuotaDePiecesAtteint;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePiece;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePieceInconnu;
import com.hemodialyse.backend.contexts.gmao.doubles.Fichiers;
import com.hemodialyse.backend.contexts.gmao.doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.gmao.doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.gmao.doubles.InterventionsHotesEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PiecesJointesEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PublicateurEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.UniteDeTravailDirecte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les cas d'usage des pièces jointes (R22, R23, KB-GMAO-58/61/64) avec des doubles en mémoire. */
class CasDUsagePieceJointeTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c2"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final InterventionId INTERVENTION = new InterventionId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final InterventionId INCONNUE = new InterventionId(UUID.fromString("00000000-0000-0000-0000-0000000000a9"));
    private static final Instant MAINTENANT = Instant.parse("2026-10-12T08:00:00Z");

    private final InterventionsHotesEnMemoire hotes = new InterventionsHotesEnMemoire();
    private final PiecesJointesEnMemoire pieces = new PiecesJointesEnMemoire();
    private final PublicateurEnMemoire publicateur = new PublicateurEnMemoire();
    private final UniteDeTravailDirecte uniteDeTravail = new UniteDeTravailDirecte();
    private final HorlogeFixe horloge = new HorlogeFixe(MAINTENANT);

    private JoindrePieceJointe joindre;
    private RetirerPieceJointe retirer;
    private ListerPiecesJointes lister;
    private TelechargerPieceJointe telecharger;

    @BeforeEach
    void composer() {
        hotes.ajouter(new InterventionHote(INTERVENTION, CENTRE, StatutIntervention.EN_COURS, false));
        joindre = new JoindrePieceJointe(hotes, pieces, publicateur, horloge, new IdentifiantsSequentiels(), uniteDeTravail);
        retirer = new RetirerPieceJointe(hotes, pieces, publicateur, horloge, uniteDeTravail);
        lister = new ListerPiecesJointes(hotes, pieces);
        telecharger = new TelechargerPieceJointe(hotes, pieces);
    }

    private PieceJointeVue joindre(String nom, byte[] contenu) {
        return joindre(INTERVENTION, CENTRE, "AUTRE", nom, contenu);
    }

    private PieceJointeVue joindre(InterventionId intervention, CentreId centre, String type, String nom, byte[] contenu) {
        return joindre.executer(new JoindrePieceJointeCommande(intervention, centre, type, nom, contenu, PAR));
    }

    // ------------------------------------------------------------------ joindre

    @Test
    void should_join_a_file_and_publish_piece_jointe_ajoutee_in_one_unit_of_work() {
        PieceJointeVue vue = joindre(INTERVENTION, CENTRE, "FACTURE", "devis.pdf", Fichiers.pdf());

        assertThat(vue.interventionId()).isEqualTo(INTERVENTION.valeur());
        assertThat(vue.type()).isEqualTo(TypeDePiece.FACTURE);
        assertThat(vue.nom()).isEqualTo("devis.pdf");
        assertThat(vue.contentType()).isEqualTo("application/pdf");
        assertThat(vue.taille()).isEqualTo(Fichiers.pdf().length);
        assertThat(vue.ajouteLe()).isEqualTo(MAINTENANT);
        assertThat(pieces.compter(INTERVENTION)).isEqualTo(1);
        assertThat(pieces.trouverParId(new PieceJointeId(vue.id()))).get().extracting("octets").isEqualTo(Fichiers.pdf());
        assertThat(publicateur.publies).singleElement().isInstanceOf(PieceJointeAjoutee.class);
        assertThat(uniteDeTravail.executions).isEqualTo(1);
    }

    @Test
    void should_lock_the_intervention_before_counting_so_that_the_quota_holds_under_concurrency() {
        joindre("a.pdf", Fichiers.pdf());

        assertThat(hotes.verrous).containsExactly(INTERVENTION);
    }

    @Test
    void should_accept_the_same_file_twice() {
        joindre("devis.pdf", Fichiers.pdf());
        joindre("devis.pdf", Fichiers.pdf());

        assertThat(pieces.compter(INTERVENTION)).isEqualTo(2);
    }

    @Test
    void should_treat_an_unknown_or_foreign_intervention_as_not_found() {
        assertThatThrownBy(() -> joindre(INCONNUE, CENTRE, "AUTRE", "a.pdf", Fichiers.pdf()))
                .isInstanceOf(InterventionIntrouvable.class).hasMessage("Intervention non trouvée");
        assertThatThrownBy(() -> joindre(INTERVENTION, AUTRE_CENTRE, "AUTRE", "a.pdf", Fichiers.pdf()))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThat(publicateur.publies).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatutIntervention.class, names = {"ANNULEE", "TERMINEE"})
    void should_refuse_to_join_on_a_cancelled_or_done_intervention(StatutIntervention statut) {
        hotes.ajouter(new InterventionHote(INTERVENTION, CENTRE, statut, false));

        assertThatThrownBy(() -> joindre("a.pdf", Fichiers.pdf())).isInstanceOf(InterventionFermee.class);
        assertThat(pieces.compter(INTERVENTION)).isZero();
        assertThat(publicateur.publies).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatutIntervention.class, names = {"PLANIFIEE", "EN_COURS", "EN_ATTENTE_VALIDATION"})
    void should_join_on_an_open_intervention(StatutIntervention statut) {
        hotes.ajouter(new InterventionHote(INTERVENTION, CENTRE, statut, false));

        assertThatCode(() -> joindre("a.pdf", Fichiers.pdf())).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_to_join_on_a_logically_deleted_intervention() {
        hotes.ajouter(new InterventionHote(INTERVENTION, CENTRE, StatutIntervention.PLANIFIEE, true));

        assertThatThrownBy(() -> joindre("a.pdf", Fichiers.pdf())).isInstanceOf(InterventionFermee.class);
    }

    @Test
    void should_accept_the_50th_piece_and_reject_the_51st_even_when_the_file_is_invalid_or_the_type_unknown() {
        for (int i = 1; i <= 50; i++) joindre("p" + i + ".pdf", Fichiers.pdf());

        assertThat(pieces.compter(INTERVENTION)).isEqualTo(50);
        assertThatThrownBy(() -> joindre("p51.pdf", Fichiers.pdf())).isInstanceOf(QuotaDePiecesAtteint.class);
        assertThatThrownBy(() -> joindre("p51.gif", Fichiers.octets("GIF89a"))).isInstanceOf(QuotaDePiecesAtteint.class);
        assertThatThrownBy(() -> joindre(INTERVENTION, CENTRE, "FOO", "p51.pdf", Fichiers.pdf()))
                .isInstanceOf(QuotaDePiecesAtteint.class);
        assertThat(pieces.compter(INTERVENTION)).isEqualTo(50);
    }

    @Test
    void should_count_the_pieces_of_each_intervention_separately() {
        InterventionId autre = new InterventionId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
        hotes.ajouter(new InterventionHote(autre, CENTRE, StatutIntervention.PLANIFIEE, false));
        for (int i = 1; i <= 50; i++) joindre("p" + i + ".pdf", Fichiers.pdf());

        assertThatCode(() -> joindre(autre, CENTRE, "AUTRE", "ok.pdf", Fichiers.pdf())).doesNotThrowAnyException();
    }

    @Test
    void should_reopen_a_place_after_a_withdrawal() {
        PieceJointeVue premiere = null;
        for (int i = 1; i <= 50; i++) {
            PieceJointeVue v = joindre("p" + i + ".pdf", Fichiers.pdf());
            if (i == 1) premiere = v;
        }
        retirer.executer(INTERVENTION, CENTRE, new PieceJointeId(premiere.id()), PAR);

        assertThatCode(() -> joindre("p51.pdf", Fichiers.pdf())).doesNotThrowAnyException();
    }

    @Test
    void should_reject_an_unknown_piece_type_before_looking_at_the_file() {
        assertThatThrownBy(() -> joindre(INTERVENTION, CENTRE, "foo", "a.gif", Fichiers.octets("GIF89a")))
                .isInstanceOf(TypeDePieceInconnu.class);
    }

    @Test
    void should_reject_an_unacceptable_file_and_keep_nothing() {
        assertThatThrownBy(() -> joindre("a.pdf", new byte[0])).isInstanceOf(FichierRefuse.class);
        assertThatThrownBy(() -> joindre("a.pdf", Fichiers.octets("%PDF-1.4\n<html>"))).isInstanceOf(FichierRefuse.class);
        assertThatThrownBy(() -> joindre("a.exe", Fichiers.pdf())).isInstanceOf(FichierRefuse.class);

        assertThat(pieces.compter(INTERVENTION)).isZero();
        assertThat(publicateur.publies).isEmpty();
    }

    // ------------------------------------------------------------------ retirer

    @Test
    void should_withdraw_a_piece_physically_and_publish_piece_jointe_retiree() {
        PieceJointeVue vue = joindre("a.pdf", Fichiers.pdf());
        publicateur.publies.clear();

        retirer.executer(INTERVENTION, CENTRE, new PieceJointeId(vue.id()), PAR);

        assertThat(pieces.compter(INTERVENTION)).isZero();
        assertThat(publicateur.publies).singleElement().isInstanceOf(PieceJointeRetiree.class);
    }

    @Test
    void should_report_a_second_withdrawal_and_a_piece_of_another_intervention_as_not_found() {
        PieceJointeVue vue = joindre("a.pdf", Fichiers.pdf());
        InterventionId autre = new InterventionId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
        hotes.ajouter(new InterventionHote(autre, CENTRE, StatutIntervention.PLANIFIEE, false));

        assertThatThrownBy(() -> retirer.executer(autre, CENTRE, new PieceJointeId(vue.id()), PAR))
                .isInstanceOf(PieceJointeIntrouvable.class).hasMessage("Document non trouvé");
        retirer.executer(INTERVENTION, CENTRE, new PieceJointeId(vue.id()), PAR);
        assertThatThrownBy(() -> retirer.executer(INTERVENTION, CENTRE, new PieceJointeId(vue.id()), PAR))
                .isInstanceOf(PieceJointeIntrouvable.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatutIntervention.class, names = {"ANNULEE", "TERMINEE"})
    void should_refuse_to_withdraw_on_a_cancelled_or_done_intervention_and_keep_the_piece(StatutIntervention statut) {
        PieceJointeVue vue = joindre("a.pdf", Fichiers.pdf());
        hotes.ajouter(new InterventionHote(INTERVENTION, CENTRE, statut, false));

        assertThatThrownBy(() -> retirer.executer(INTERVENTION, CENTRE, new PieceJointeId(vue.id()), PAR))
                .isInstanceOf(InterventionFermee.class);
        assertThat(pieces.compter(INTERVENTION)).isEqualTo(1);
    }

    @Test
    void should_refuse_to_withdraw_on_a_logically_deleted_intervention() {
        PieceJointeVue vue = joindre("a.pdf", Fichiers.pdf());
        hotes.ajouter(new InterventionHote(INTERVENTION, CENTRE, StatutIntervention.EN_COURS, true));

        assertThatThrownBy(() -> retirer.executer(INTERVENTION, CENTRE, new PieceJointeId(vue.id()), PAR))
                .isInstanceOf(InterventionFermee.class);
    }

    @Test
    void should_treat_a_withdrawal_on_a_foreign_intervention_as_not_found() {
        PieceJointeVue vue = joindre("a.pdf", Fichiers.pdf());

        assertThatThrownBy(() -> retirer.executer(INTERVENTION, AUTRE_CENTRE, new PieceJointeId(vue.id()), PAR))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThat(pieces.compter(INTERVENTION)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ lister

    @Test
    void should_list_the_pieces_from_the_latest_to_the_oldest_without_bytes() {
        for (int i = 1; i <= 5; i++) {
            horloge.avancerA(MAINTENANT.plusSeconds(i));
            joindre("p" + i + ".pdf", Fichiers.pdf());
        }

        PageDePiecesJointes page = lister.executer(INTERVENTION, CENTRE, 1, 2);

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.items()).extracting(PieceJointeVue::nom).containsExactly("p3.pdf", "p2.pdf");
    }

    @Test
    void should_list_pieces_of_a_closed_or_deleted_intervention() {
        joindre("a.pdf", Fichiers.pdf());
        hotes.ajouter(new InterventionHote(INTERVENTION, CENTRE, StatutIntervention.TERMINEE, true));

        assertThat(lister.executer(INTERVENTION, CENTRE, 0, 20).items()).hasSize(1);
    }

    @Test
    void should_refuse_a_listing_outside_the_page_bounds_and_for_a_foreign_intervention() {
        assertThatThrownBy(() -> lister.executer(INTERVENTION, CENTRE, -1, 20)).hasMessage("Page index must not be less than zero");
        assertThatThrownBy(() -> lister.executer(INTERVENTION, CENTRE, 0, 0)).hasMessage("Page size must not be less than one");
        assertThatThrownBy(() -> lister.executer(INTERVENTION, AUTRE_CENTRE, 0, 20)).isInstanceOf(InterventionIntrouvable.class);
        assertThatThrownBy(() -> lister.executer(INCONNUE, CENTRE, 0, 20)).isInstanceOf(InterventionIntrouvable.class);
    }

    @Test
    void should_sanitize_again_the_name_and_the_type_of_a_piece_stored_before_the_correction() {
        InterventionId i = INTERVENTION;
        pieces.enregistrer(com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe.reconstituer(
                new PieceJointeId(UUID.fromString("00000000-0000-0000-0000-0000000000d1")), i, CENTRE, TypeDePiece.AUTRE,
                "../../traversal.pdf", "text/html", 3, new byte[]{1, 2, 3}, PAR, MAINTENANT));

        PieceJointeVue vue = lister.executer(INTERVENTION, CENTRE, 0, 20).items().getFirst();

        assertThat(vue.nom()).isEqualTo("traversal.pdf");
        assertThat(vue.contentType()).isEqualTo("application/octet-stream");
    }

    // ------------------------------------------------------------------ télécharger

    @Test
    void should_download_the_exact_bytes_with_the_detected_type_and_the_sanitized_name() {
        PieceJointeVue vue = joindre("../../avant.png", Fichiers.png());

        PieceTelechargee t = telecharger.executer(INTERVENTION, CENTRE, new PieceJointeId(vue.id()));

        assertThat(t.nom()).isEqualTo("avant.png");
        assertThat(t.contentType()).isEqualTo("image/png");
        assertThat(t.octets()).isEqualTo(Fichiers.png());
    }

    @Test
    void should_download_a_legacy_piece_with_a_neutral_type_and_a_clean_name() {
        PieceJointeId id = new PieceJointeId(UUID.fromString("00000000-0000-0000-0000-0000000000d2"));
        pieces.enregistrer(com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe.reconstituer(id, INTERVENTION,
                CENTRE, TypeDePiece.AUTRE, "../../traversal.pdf", "text/html", 5, "<html>".getBytes(), null, MAINTENANT));

        PieceTelechargee t = telecharger.executer(INTERVENTION, CENTRE, id);

        assertThat(t.nom()).isEqualTo("traversal.pdf");
        assertThat(t.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void should_download_on_any_status_and_report_missing_things_as_not_found() {
        PieceJointeVue vue = joindre("a.pdf", Fichiers.pdf());
        hotes.ajouter(new InterventionHote(INTERVENTION, CENTRE, StatutIntervention.ANNULEE, true));

        assertThat(telecharger.executer(INTERVENTION, CENTRE, new PieceJointeId(vue.id())).octets()).isEqualTo(Fichiers.pdf());
        assertThatThrownBy(() -> telecharger.executer(INTERVENTION, CENTRE,
                new PieceJointeId(UUID.fromString("00000000-0000-0000-0000-0000000000d9")))).isInstanceOf(PieceJointeIntrouvable.class);
        assertThatThrownBy(() -> telecharger.executer(INTERVENTION, AUTRE_CENTRE, new PieceJointeId(vue.id())))
                .isInstanceOf(InterventionIntrouvable.class);
    }
}
