package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.LecturePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PageDePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PieceJointeVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePiece;
import com.hemodialyse.backend.contexts.gmao.doubles.Fichiers;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat partagé du repository et du read model des pièces jointes : exécuté contre le double in-memory et l'adapter JPA sur
 * H2 (même suite : le double est fidèle), puis sur PostgreSQL 16. Y sont verrouillés : l'aller-retour des octets (5 Mo exacts
 * compris), le comptage par intervention, la suppression physique, les listes du plus récent au plus ancien (ex aequo par
 * identifiant décroissant) avec leurs bornes (KB-GMAO-67), et le fait que le texte hérité (nom, type MIME) est rendu tel que
 * stocké par la persistance (c'est le cas d'usage qui l'assainit).
 */
public abstract class PieceJointeContrat {

    protected static final Instant T0 = Instant.parse("2026-04-01T09:00:00Z");
    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000a"));
    protected static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99997500-0000-0000-0000-0000000000f1"));
    protected static final InterventionId INTERVENTION_1 = new InterventionId(UUID.fromString("99997500-0000-0000-0000-0000000000b1"));
    protected static final InterventionId INTERVENTION_2 = new InterventionId(UUID.fromString("99997500-0000-0000-0000-0000000000b2"));

    private int sequence = 0;

    protected abstract PieceJointeRepository repository();

    protected abstract LecturePiecesJointes lecture();

    private PieceJointeId prochainId() {
        return new PieceJointeId(UUID.fromString(String.format("99997500-0000-0000-0000-%012d", ++sequence)));
    }

    protected PieceJointe piece(InterventionId intervention, String nom, byte[] contenu, Instant ajouteLe) {
        return PieceJointe.reconstituer(prochainId(), intervention, CENTRE, TypeDePiece.FACTURE, nom, "application/pdf",
                contenu.length, contenu, ADMIN, ajouteLe);
    }

    private PieceJointe enregistrer(InterventionId intervention, String nom, Instant ajouteLe) {
        PieceJointe piece = piece(intervention, nom, Fichiers.pdf(), ajouteLe);
        repository().enregistrer(piece);
        return piece;
    }

    // ------------------------------------------------------------------ repository

    @Test
    void should_give_back_the_exact_bytes_and_every_field_of_a_piece() {
        PieceJointe piece = PieceJointe.reconstituer(prochainId(), INTERVENTION_1, CENTRE, TypeDePiece.PHOTO, "avant.png",
                "image/png", Fichiers.png().length, Fichiers.png(), ADMIN, T0);
        repository().enregistrer(piece);

        PieceJointe relue = repository().trouverParId(piece.id()).orElseThrow();

        assertThat(relue.id()).isEqualTo(piece.id());
        assertThat(relue.interventionId()).isEqualTo(INTERVENTION_1);
        assertThat(relue.centreId()).isEqualTo(CENTRE);
        assertThat(relue.type()).isEqualTo(TypeDePiece.PHOTO);
        assertThat(relue.nom()).isEqualTo("avant.png");
        assertThat(relue.contentType()).isEqualTo("image/png");
        assertThat(relue.taille()).isEqualTo(Fichiers.png().length);
        assertThat(relue.octets()).isEqualTo(Fichiers.png());
        assertThat(relue.ajoutePar()).isEqualTo(ADMIN);
        assertThat(relue.ajouteLe()).isEqualTo(T0);
        assertThat(relue.tirerEvenements()).isEmpty();
    }

    @Test
    void should_store_exactly_5_mo() {
        byte[] cinqMo = Fichiers.pdf((int) PieceJointe.TAILLE_MAX_OCTETS);
        PieceJointe piece = piece(INTERVENTION_1, "limite.pdf", cinqMo, T0);
        repository().enregistrer(piece);

        assertThat(repository().trouverParId(piece.id()).orElseThrow().octets()).isEqualTo(cinqMo);
    }

    @Test
    void should_keep_a_150_character_name_and_a_piece_without_author() {
        PieceJointe piece = PieceJointe.reconstituer(prochainId(), INTERVENTION_1, CENTRE, TypeDePiece.AUTRE,
                "b".repeat(146) + ".pdf", "application/pdf", 1, new byte[]{1}, null, T0);
        repository().enregistrer(piece);

        PieceJointe relue = repository().trouverParId(piece.id()).orElseThrow();

        assertThat(relue.nom()).hasSize(150);
        assertThat(relue.ajoutePar()).isNull();
    }

    @Test
    void should_keep_legacy_text_as_stored() {
        PieceJointe piece = PieceJointe.reconstituer(prochainId(), INTERVENTION_1, CENTRE, TypeDePiece.AUTRE, "../../traversal.pdf",
                "text/html", 6, "<html>".getBytes(), ADMIN, T0);
        repository().enregistrer(piece);

        PieceJointe relue = repository().trouverParId(piece.id()).orElseThrow();

        assertThat(relue.nom()).isEqualTo("../../traversal.pdf");
        assertThat(relue.contentType()).isEqualTo("text/html");
    }

    @Test
    void should_find_a_piece_for_withdrawal_with_its_fields_but_without_bytes() {
        PieceJointe piece = piece(INTERVENTION_1, "a.pdf", Fichiers.pdf(), T0);
        repository().enregistrer(piece);

        PieceJointe enTete = repository().trouverPourRetrait(piece.id()).orElseThrow();

        assertThat(enTete.id()).isEqualTo(piece.id());
        assertThat(enTete.interventionId()).isEqualTo(INTERVENTION_1);
        assertThat(enTete.centreId()).isEqualTo(CENTRE);
        assertThat(enTete.nom()).isEqualTo("a.pdf");
        assertThat(enTete.taille()).isEqualTo(piece.taille());
        assertThat(enTete.octets()).isEmpty();
        assertThat(repository().trouverPourRetrait(prochainId())).isEmpty();
    }

    @Test
    void should_find_nothing_for_an_unknown_piece() {
        assertThat(repository().trouverParId(prochainId())).isEmpty();
    }

    @Test
    void should_count_the_pieces_of_each_intervention() {
        enregistrer(INTERVENTION_1, "a.pdf", T0);
        enregistrer(INTERVENTION_1, "b.pdf", T0.plusSeconds(1));
        enregistrer(INTERVENTION_2, "c.pdf", T0);

        assertThat(repository().compter(INTERVENTION_1)).isEqualTo(2);
        assertThat(repository().compter(INTERVENTION_2)).isEqualTo(1);
        assertThat(repository().compter(new InterventionId(UUID.randomUUID()))).isZero();
    }

    @Test
    void should_remove_a_piece_physically_and_ignore_an_unknown_one() {
        PieceJointe a = enregistrer(INTERVENTION_1, "a.pdf", T0);
        PieceJointe b = enregistrer(INTERVENTION_1, "b.pdf", T0.plusSeconds(1));

        repository().supprimer(a.id());
        repository().supprimer(prochainId());

        assertThat(repository().trouverParId(a.id())).isEmpty();
        assertThat(repository().trouverParId(b.id())).isPresent();
        assertThat(repository().compter(INTERVENTION_1)).isEqualTo(1);
        assertThat(lecture().deLIntervention(INTERVENTION_1, 0, 20).items()).extracting(PieceJointeVue::nom).containsExactly("b.pdf");
    }

    // ------------------------------------------------------------------ read model

    @Test
    void should_list_from_the_latest_to_the_oldest_with_the_metadata_only() {
        enregistrer(INTERVENTION_1, "p1.pdf", T0);
        enregistrer(INTERVENTION_1, "p3.pdf", T0.plusSeconds(120));
        PieceJointe milieu = enregistrer(INTERVENTION_1, "p2.pdf", T0.plusSeconds(60));
        enregistrer(INTERVENTION_2, "autre.pdf", T0.plusSeconds(500));

        PageDePiecesJointes page = lecture().deLIntervention(INTERVENTION_1, 0, 20);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.items()).extracting(PieceJointeVue::nom).containsExactly("p3.pdf", "p2.pdf", "p1.pdf");
        PieceJointeVue vue = page.items().get(1);
        assertThat(vue.id()).isEqualTo(milieu.id().valeur());
        assertThat(vue.interventionId()).isEqualTo(INTERVENTION_1.valeur());
        assertThat(vue.type()).isEqualTo(TypeDePiece.FACTURE);
        assertThat(vue.contentType()).isEqualTo("application/pdf");
        assertThat(vue.taille()).isEqualTo(Fichiers.pdf().length);
        assertThat(vue.ajouteLe()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void should_order_ties_by_decreasing_identifier() {
        PieceJointe premiere = enregistrer(INTERVENTION_1, "premiere.pdf", T0);
        PieceJointe seconde = enregistrer(INTERVENTION_1, "seconde.pdf", T0);

        assertThat(premiere.id().valeur()).isLessThan(seconde.id().valeur());
        assertThat(lecture().deLIntervention(INTERVENTION_1, 0, 20).items()).extracting(PieceJointeVue::nom)
                .containsExactly("seconde.pdf", "premiere.pdf");
    }

    @Test
    void should_paginate() {
        for (int i = 1; i <= 5; i++) enregistrer(INTERVENTION_1, "p" + i + ".pdf", T0.plusSeconds(i));

        assertThat(lecture().deLIntervention(INTERVENTION_1, 0, 2).items()).extracting(PieceJointeVue::nom).containsExactly("p5.pdf", "p4.pdf");
        assertThat(lecture().deLIntervention(INTERVENTION_1, 2, 2).items()).extracting(PieceJointeVue::nom).containsExactly("p1.pdf");
        PageDePiecesJointes audela = lecture().deLIntervention(INTERVENTION_1, 99, 2);
        assertThat(audela.items()).isEmpty();
        assertThat(audela.total()).isEqualTo(5);
        assertThat(lecture().deLIntervention(INTERVENTION_1, 0, Integer.MAX_VALUE).items()).hasSize(5);
    }

    @Test
    void should_list_nothing_for_an_intervention_without_piece() {
        PageDePiecesJointes page = lecture().deLIntervention(INTERVENTION_2, 0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void should_refuse_page_bounds_with_the_messages_of_spring_data() {
        assertThatThrownBy(() -> lecture().deLIntervention(INTERVENTION_1, -1, 20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page index must not be less than zero");
        assertThatThrownBy(() -> lecture().deLIntervention(INTERVENTION_1, 0, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page size must not be less than one");
        assertThatThrownBy(() -> lecture().deLIntervention(INTERVENTION_1, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
