package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import com.hemodialyse.backend.contexts.gmao.doubles.Fichiers;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R22, R23 et KB-GMAO-58/59/60/61/64 sur l'agrégat {@link PieceJointe}. */
class PieceJointeTest {

    private static final PieceJointeId ID = new PieceJointeId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final InterventionId INTERVENTION = new InterventionId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final Instant MAINTENANT = Instant.parse("2026-10-12T08:00:00Z");

    private static PieceJointe joindre(String nom, byte[] contenu) {
        return PieceJointe.joindre(ID, INTERVENTION, CENTRE, TypeDePiece.FACTURE, nom, contenu, PAR, MAINTENANT);
    }

    // ------------------------------------------------------------------ R22

    @Test
    void should_join_a_valid_file_with_the_detected_type_and_the_exact_size() {
        byte[] pdf = Fichiers.pdf();

        PieceJointe piece = joindre("devis.pdf", pdf);

        assertThat(piece.id()).isEqualTo(ID);
        assertThat(piece.interventionId()).isEqualTo(INTERVENTION);
        assertThat(piece.centreId()).isEqualTo(CENTRE);
        assertThat(piece.type()).isEqualTo(TypeDePiece.FACTURE);
        assertThat(piece.nom()).isEqualTo("devis.pdf");
        assertThat(piece.contentType()).isEqualTo("application/pdf");
        assertThat(piece.taille()).isEqualTo(pdf.length);
        assertThat(piece.octets()).isEqualTo(pdf);
        assertThat(piece.ajoutePar()).isEqualTo(PAR);
        assertThat(piece.ajouteLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void should_detect_the_type_on_the_content_whatever_the_extension_says_when_there_is_none() {
        assertThat(joindre("sans-extension", Fichiers.png()).contentType()).isEqualTo("image/png");
        assertThat(joindre("photo.JPG", Fichiers.jpeg()).contentType()).isEqualTo("image/jpeg");
        assertThat(joindre("photo.jpeg", Fichiers.jpeg()).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void should_reject_an_empty_file() {
        assertThatThrownBy(() -> joindre("a.pdf", new byte[0])).isInstanceOf(FichierRefuse.class).hasMessage("Fichier vide");
        assertThatThrownBy(() -> joindre("a.pdf", null)).isInstanceOf(FichierRefuse.class).hasMessage("Fichier vide");
    }

    @Test
    void should_accept_exactly_5_mo_and_reject_one_byte_more() {
        int max = (int) PieceJointe.TAILLE_MAX_OCTETS;

        assertThat(joindre("limite.pdf", Fichiers.pdf(max)).taille()).isEqualTo(max);
        assertThat(joindre("presque.pdf", Fichiers.pdf(max - 1)).taille()).isEqualTo(max - 1);
        assertThatThrownBy(() -> joindre("trop.pdf", Fichiers.pdf(max + 1))).isInstanceOf(FichierRefuse.class)
                .hasMessage("Fichier trop volumineux (5 Mo maximum)");
    }

    @Test
    void should_reject_a_format_other_than_pdf_png_or_jpeg() {
        assertThatThrownBy(() -> joindre("a.gif", Fichiers.octets("GIF89a......"))).isInstanceOf(FichierRefuse.class)
                .hasMessage("Format non accepté (PDF, PNG ou JPEG uniquement)");
    }

    @Test
    void should_reject_a_truncated_pdf_a_jpeg_cut_short_and_a_pdf_followed_by_html() {
        assertThatThrownBy(() -> joindre("a.pdf", Fichiers.octets("%PDF-1.4\n1 0 obj"))).isInstanceOf(FichierRefuse.class)
                .hasMessageContaining("incomplet ou altéré");
        assertThatThrownBy(() -> joindre("a.jpg", Fichiers.sansLesDerniers(Fichiers.jpeg(), 2))).isInstanceOf(FichierRefuse.class)
                .hasMessageContaining("incomplet ou altéré");
        assertThatThrownBy(() -> joindre("a.pdf", Fichiers.suivi(Fichiers.pdf(), "<html><script>x</script>")))
                .isInstanceOf(FichierRefuse.class).hasMessageContaining("incomplet ou altéré");
    }

    @Test
    void should_reject_an_extension_that_does_not_match_the_detected_type() {
        assertThatThrownBy(() -> joindre("malware.exe", Fichiers.pdf())).isInstanceOf(FichierRefuse.class)
                .hasMessageContaining(".exe");
        assertThatThrownBy(() -> joindre("photo.pdf", Fichiers.jpeg())).isInstanceOf(FichierRefuse.class);
        assertThatThrownBy(() -> joindre("scan.png", Fichiers.pdf())).isInstanceOf(FichierRefuse.class);
        assertThatThrownBy(() -> joindre("x.html", Fichiers.pdf())).isInstanceOf(FichierRefuse.class);
    }

    @Test
    void should_reject_a_forbidden_extension_hidden_behind_a_no_break_space() {
        assertThatThrownBy(() -> joindre("facture.exe ", Fichiers.pdf())).hasMessageContaining("extension");
    }

    @Test
    void should_check_the_extension_on_the_sanitized_name() {
        assertThatThrownBy(() -> joindre("../../malware.exe", Fichiers.pdf())).isInstanceOf(FichierRefuse.class);
        assertThat(joindre("../../ok.pdf", Fichiers.pdf()).nom()).isEqualTo("ok.pdf");
    }

    @Test
    void should_store_a_sanitized_name() {
        assertThat(joindre("rtl‮gnp.exe.pdf", Fichiers.pdf()).nom()).isEqualTo("rtlgnp.exe.pdf");
        assertThat(joindre(null, Fichiers.pdf()).nom()).isEqualTo("document");
    }

    @Test
    void should_report_the_refusals_in_a_stable_order_empty_then_size_then_format_then_structure_then_extension() {
        assertThatThrownBy(() -> joindre("x.exe", new byte[0])).hasMessage("Fichier vide");
        assertThatThrownBy(() -> joindre("x.exe", Fichiers.octets("GIF89a"))).hasMessageContaining("Format non accepté");
        assertThatThrownBy(() -> joindre("x.exe", Fichiers.octets("%PDF-1.4"))).hasMessageContaining("incomplet");
        assertThatThrownBy(() -> joindre("x.exe", Fichiers.pdf())).hasMessageContaining("extension");
    }

    // ------------------------------------------------------------------ R23

    @Test
    void should_accept_the_50th_piece_and_reject_the_51st() {
        assertThatCode(() -> PieceJointe.verifierLeQuota(49)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PieceJointe.verifierLeQuota(50)).isInstanceOf(QuotaDePiecesAtteint.class)
                .hasMessage("Nombre maximal de documents atteint pour cette intervention");
        assertThatThrownBy(() -> PieceJointe.verifierLeQuota(120)).isInstanceOf(QuotaDePiecesAtteint.class);
    }

    // ------------------------------------------------------------------ KB-GMAO-64

    @ParameterizedTest
    @EnumSource(StatutIntervention.class)
    void should_allow_changing_the_pieces_unless_the_intervention_is_cancelled_or_done(StatutIntervention statut) {
        InterventionHote hote = new InterventionHote(INTERVENTION, CENTRE, statut, false);
        if (statut == StatutIntervention.ANNULEE || statut == StatutIntervention.TERMINEE) {
            assertThatThrownBy(hote::exigerOuverte).isInstanceOf(InterventionFermee.class)
                    .isInstanceOf(IllegalStateException.class);
        } else {
            assertThatCode(hote::exigerOuverte).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @EnumSource(StatutIntervention.class)
    void should_refuse_changing_the_pieces_of_a_logically_deleted_intervention_whatever_its_status(StatutIntervention statut) {
        assertThatThrownBy(() -> new InterventionHote(INTERVENTION, CENTRE, statut, true).exigerOuverte())
                .isInstanceOf(InterventionFermee.class);
    }

    // ------------------------------------------------------------------ événements

    @Test
    void should_raise_piece_jointe_ajoutee_without_the_bytes_when_joining() {
        PieceJointe piece = joindre("devis.pdf", Fichiers.pdf());

        assertThat(piece.tirerEvenements()).containsExactly(new PieceJointeAjoutee(ID, INTERVENTION, CENTRE,
                TypeDePiece.FACTURE, "devis.pdf", piece.taille(), PAR, MAINTENANT));
        assertThat(piece.tirerEvenements()).isEmpty();
    }

    @Test
    void should_raise_piece_jointe_retiree_when_withdrawing() {
        PieceJointe piece = PieceJointe.reconstituer(ID, INTERVENTION, CENTRE, TypeDePiece.PHOTO, "avant.png", "image/png", 7,
                Fichiers.png(), PAR, MAINTENANT);
        Instant plusTard = MAINTENANT.plusSeconds(60);

        piece.retirer(PAR, plusTard);

        assertThat(piece.tirerEvenements()).containsExactly(new PieceJointeRetiree(ID, INTERVENTION, CENTRE, "avant.png", 7, PAR, plusTard));
    }

    @Test
    void should_not_raise_any_event_when_reconstituting() {
        PieceJointe piece = PieceJointe.reconstituer(ID, INTERVENTION, CENTRE, TypeDePiece.AUTRE, "a.pdf", "application/pdf", 1,
                new byte[]{1}, null, MAINTENANT);

        assertThat(piece.tirerEvenements()).isEmpty();
        assertThat(piece.ajoutePar()).isNull();
    }

    // ------------------------------------------------------------------ KB-GMAO-61 : relecture

    @Test
    void should_sanitize_again_the_name_of_a_piece_stored_before_the_correction() {
        PieceJointe heritee = PieceJointe.reconstituer(ID, INTERVENTION, CENTRE, TypeDePiece.AUTRE, "../../traversal.pdf",
                "application/pdf", 1, new byte[]{1}, PAR, MAINTENANT);

        assertThat(heritee.nomAffiche()).isEqualTo("traversal.pdf");
        assertThat(heritee.nom()).isEqualTo("../../traversal.pdf");
    }

    @Test
    void should_serve_the_stored_type_only_when_it_is_one_of_the_three_accepted_formats() {
        assertThat(piece("application/pdf").contentTypeServi()).isEqualTo("application/pdf");
        assertThat(piece("image/png").contentTypeServi()).isEqualTo("image/png");
        assertThat(piece("image/jpeg").contentTypeServi()).isEqualTo("image/jpeg");
        assertThat(piece("text/html").contentTypeServi()).isEqualTo("application/octet-stream");
        assertThat(piece("n'importe quoi").contentTypeServi()).isEqualTo("application/octet-stream");
        assertThat(piece(null).contentTypeServi()).isEqualTo("application/octet-stream");
    }

    private static PieceJointe piece(String contentType) {
        return PieceJointe.reconstituer(ID, INTERVENTION, CENTRE, TypeDePiece.AUTRE, "a.pdf", contentType, 1, new byte[]{1}, PAR,
                MAINTENANT);
    }

    @Test
    void should_refuse_a_missing_identity() {
        assertThatThrownBy(() -> new PieceJointeId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ type de pièce

    @Test
    void should_read_the_piece_type_case_sensitively() {
        assertThat(TypeDePiece.depuis("BON_INTERVENTION")).isEqualTo(TypeDePiece.BON_INTERVENTION);
        assertThat(TypeDePiece.depuis("AUTRE")).isEqualTo(TypeDePiece.AUTRE);
        for (String invalide : new String[]{"photo", "PHOTO ", "", " ", null, "FOO"}) {
            assertThatThrownBy(() -> TypeDePiece.depuis(invalide)).isInstanceOf(TypeDePieceInconnu.class)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(((TypeDePieceInconnu) catchTypeInconnu("FOO")).code()).isEqualTo("FOO");
    }

    private static Throwable catchTypeInconnu(String code) {
        try {
            TypeDePiece.depuis(code);
            return null;
        } catch (TypeDePieceInconnu e) {
            return e;
        }
    }
}
