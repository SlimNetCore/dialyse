package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.LectureIntervenants;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.PageDIntervenants;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.TarifIntervenant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat partagé des ports de persistance et de lecture des intervenants : exécuté contre le double in-memory, l'adapter
 * JPA sur H2 et l'adapter sur PostgreSQL 16 (même suite : le double est fidèle). Les comportements du legacy reproduits y
 * sont verrouillés : tarif arrondi à deux décimales par la base (KB-GMAO-26), longueurs et précision refusées par la
 * base (KB-GMAO-27), liste triée par nom désactivés compris, doublons acceptés (KB-GMAO-27/28).
 */
public abstract class IntervenantContrat {

    protected static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997000-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997000-0000-0000-0000-00000000000b"));
    protected static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99997000-0000-0000-0000-0000000000f1"));

    private int sequence = 0;

    protected abstract IntervenantRepository repository();

    protected abstract LectureIntervenants lecture();

    protected Intervenant enregistrer(CentreId centre, String nom, NatureIntervenant nature, BigDecimal tarif) {
        IntervenantId id = new IntervenantId(UUID.fromString(String.format("99997000-0000-0000-0000-%012d", ++sequence)));
        Intervenant intervenant = Intervenant.enregistrer(id, centre, nom, nature, "0555000000", "contact@test.local",
                TarifIntervenant.ouAucun(tarif), ADMIN, T0);
        repository().enregistrer(intervenant);
        return intervenant;
    }

    @Test
    void should_restore_all_fields_of_a_registered_intervenant() {
        Intervenant intervenant = enregistrer(CENTRE_A, "Bernard Technicien", NatureIntervenant.INTERNE, new BigDecimal("45.50"));

        Intervenant relu = repository().trouverParId(intervenant.id()).orElseThrow();

        assertThat(relu.id()).isEqualTo(intervenant.id());
        assertThat(relu.centreId()).isEqualTo(CENTRE_A);
        assertThat(relu.nom()).isEqualTo("Bernard Technicien");
        assertThat(relu.nature()).isEqualTo(NatureIntervenant.INTERNE);
        assertThat(relu.telephone()).isEqualTo("0555000000");
        assertThat(relu.email()).isEqualTo("contact@test.local");
        assertThat(relu.tarif().valeur()).isEqualByComparingTo("45.50");
        assertThat(relu.estActif()).isTrue();
        assertThat(relu.tirerEvenements()).isEmpty();
    }

    @Test
    void should_accept_phone_email_and_tarif_to_be_absent() {
        IntervenantId id = new IntervenantId(UUID.fromString("99997000-0000-0000-0000-0000000000d1"));
        repository().enregistrer(Intervenant.enregistrer(id, CENTRE_A, "Minimal", NatureIntervenant.EXTERNE, null, null, null,
                ADMIN, T0));

        Intervenant relu = repository().trouverParId(id).orElseThrow();

        assertThat(relu.telephone()).isNull();
        assertThat(relu.email()).isNull();
        assertThat(relu.tarif()).isNull();
    }

    @Test
    void should_return_empty_when_the_intervenant_is_unknown() {
        IntervenantId inconnu = new IntervenantId(UUID.fromString("99997000-0000-0000-0000-0000000000ff"));

        assertThat(repository().trouverParId(inconnu)).isEmpty();
        assertThat(lecture().parId(inconnu)).isEmpty();
    }

    @Test
    void should_persist_a_total_replacement_and_a_deactivation_of_a_reloaded_intervenant() {
        Intervenant intervenant = enregistrer(CENTRE_A, "Ancien nom", NatureIntervenant.INTERNE, new BigDecimal("30.00"));
        Intervenant charge = repository().trouverParId(intervenant.id()).orElseThrow();

        charge.corriger("Nouveau nom", NatureIntervenant.EXTERNE, null, null, null, ADMIN, T0.plusSeconds(60));
        repository().enregistrer(charge);
        charge.desactiver(ADMIN, T0.plusSeconds(120));
        repository().enregistrer(charge);

        Intervenant relu = repository().trouverParId(intervenant.id()).orElseThrow();
        assertThat(relu.nom()).isEqualTo("Nouveau nom");
        assertThat(relu.nature()).isEqualTo(NatureIntervenant.EXTERNE);
        assertThat(relu.telephone()).as("remplacement total : champ omis effacé (KB-GMAO-30)").isNull();
        assertThat(relu.email()).isNull();
        assertThat(relu.tarif()).as("le tarif omis est effacé (KB-GMAO-30)").isNull();
        assertThat(relu.estActif()).isFalse();
        assertThat(relu.centreId()).isEqualTo(CENTRE_A);
    }

    @Test
    void should_round_the_tarif_to_two_decimals_half_up_like_the_legacy_column() {
        Intervenant a = enregistrer(CENTRE_A, "A arrondi 12.345", NatureIntervenant.INTERNE, new BigDecimal("12.345"));
        Intervenant b = enregistrer(CENTRE_A, "B arrondi 12.344", NatureIntervenant.INTERNE, new BigDecimal("12.344"));
        Intervenant c = enregistrer(CENTRE_A, "C entier 45", NatureIntervenant.INTERNE, new BigDecimal("45"));
        Intervenant d = enregistrer(CENTRE_A, "D echelle 45.5", NatureIntervenant.INTERNE, new BigDecimal("45.5"));

        assertThat(repository().trouverParId(a.id()).orElseThrow().tarif().valeur()).isEqualTo(new BigDecimal("12.35"));
        assertThat(repository().trouverParId(b.id()).orElseThrow().tarif().valeur()).isEqualTo(new BigDecimal("12.34"));
        assertThat(lecture().parId(c.id()).orElseThrow().tarifHoraire()).isEqualTo(new BigDecimal("45.00"));
        assertThat(lecture().parId(d.id()).orElseThrow().tarifHoraire()).isEqualTo(new BigDecimal("45.50"));
    }

    @Test
    void should_keep_a_zero_tarif_distinct_from_no_tarif() {
        Intervenant zero = enregistrer(CENTRE_A, "Tarif zero", NatureIntervenant.INTERNE, BigDecimal.ZERO);
        Intervenant aucun = enregistrer(CENTRE_A, "Sans tarif", NatureIntervenant.INTERNE, null);

        assertThat(repository().trouverParId(zero.id()).orElseThrow().tarif().valeur()).isEqualByComparingTo("0");
        assertThat(repository().trouverParId(aucun.id()).orElseThrow().tarif()).isNull();
    }

    @Test
    void should_accept_the_maximal_tarif_and_refuse_a_larger_one() {
        Intervenant maximal = enregistrer(CENTRE_A, "Tarif maximal", NatureIntervenant.INTERNE, new BigDecimal("99999999.99"));

        assertThat(repository().trouverParId(maximal.id()).orElseThrow().tarif().valeur()).isEqualTo(new BigDecimal("99999999.99"));
        assertThatThrownBy(() -> enregistrer(CENTRE_A, "Tarif trop grand", NatureIntervenant.INTERNE, new BigDecimal("100000000.00")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_accept_the_maximal_lengths_and_refuse_longer_values() {
        IntervenantId limite = new IntervenantId(UUID.fromString("99997000-0000-0000-0000-0000000000c1"));
        repository().enregistrer(Intervenant.enregistrer(limite, CENTRE_A, "n".repeat(255), NatureIntervenant.INTERNE,
                "t".repeat(50), "e".repeat(255), null, ADMIN, T0));
        assertThat(repository().trouverParId(limite).orElseThrow().nom()).hasSize(255);

        assertThatThrownBy(() -> repository().enregistrer(Intervenant.enregistrer(
                new IntervenantId(UUID.fromString("99997000-0000-0000-0000-0000000000c2")), CENTRE_A, "n".repeat(256),
                NatureIntervenant.INTERNE, null, null, null, ADMIN, T0))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> repository().enregistrer(Intervenant.enregistrer(
                new IntervenantId(UUID.fromString("99997000-0000-0000-0000-0000000000c3")), CENTRE_A, "Tel",
                NatureIntervenant.INTERNE, "t".repeat(51), null, null, ADMIN, T0))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> repository().enregistrer(Intervenant.enregistrer(
                new IntervenantId(UUID.fromString("99997000-0000-0000-0000-0000000000c4")), CENTRE_A, "Mail",
                NatureIntervenant.INTERNE, null, "e".repeat(256), null, ADMIN, T0))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_accept_duplicate_names_in_a_centre() {
        enregistrer(CENTRE_A, "Doublon", NatureIntervenant.INTERNE, null);
        enregistrer(CENTRE_A, "Doublon", NatureIntervenant.EXTERNE, null);

        assertThat(lecture().duCentre(CENTRE_A, 0, 10).items()).extracting(IntervenantVue::nom).containsExactly("Doublon", "Doublon");
    }

    @Test
    void should_read_the_view_by_id_whatever_the_centre() {
        Intervenant autre = enregistrer(CENTRE_B, "Autre centre", NatureIntervenant.EXTERNE, new BigDecimal("80"));

        IntervenantVue vue = lecture().parId(autre.id()).orElseThrow();

        assertThat(vue.id()).isEqualTo(autre.id().valeur());
        assertThat(vue.centreId()).isEqualTo(CENTRE_B.valeur());
        assertThat(vue.nature()).isEqualTo(NatureIntervenant.EXTERNE);
        assertThat(vue.actif()).isTrue();
    }

    @Test
    void should_list_the_centre_by_name_with_the_deactivated_but_not_other_centres() {
        enregistrer(CENTRE_A, "Charles", NatureIntervenant.INTERNE, null);
        Intervenant ancien = enregistrer(CENTRE_A, "Alice", NatureIntervenant.EXTERNE, null);
        enregistrer(CENTRE_A, "Bernard", NatureIntervenant.INTERNE, null);
        enregistrer(CENTRE_B, "Zoe d'un autre centre", NatureIntervenant.INTERNE, null);
        Intervenant charge = repository().trouverParId(ancien.id()).orElseThrow();
        charge.desactiver(ADMIN, T0);
        repository().enregistrer(charge);

        PageDIntervenants page = lecture().duCentre(CENTRE_A, 0, 20);

        assertThat(page.items()).extracting(IntervenantVue::nom).containsExactly("Alice", "Bernard", "Charles");
        assertThat(page.items().get(0).actif()).isFalse();
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void should_page_the_list_and_answer_an_empty_page_beyond_the_last_one() {
        for (String nom : List.of("D", "B", "A", "C", "E")) {
            enregistrer(CENTRE_A, nom, NatureIntervenant.INTERNE, null);
        }

        assertThat(lecture().duCentre(CENTRE_A, 0, 2).items()).extracting(IntervenantVue::nom).containsExactly("A", "B");
        assertThat(lecture().duCentre(CENTRE_A, 1, 2).items()).extracting(IntervenantVue::nom).containsExactly("C", "D");
        assertThat(lecture().duCentre(CENTRE_A, 2, 2).items()).extracting(IntervenantVue::nom).containsExactly("E");
        PageDIntervenants horsLimites = lecture().duCentre(CENTRE_A, 9, 2);
        assertThat(horsLimites.items()).isEmpty();
        assertThat(horsLimites.total()).isEqualTo(5);
        assertThat(lecture().duCentre(CENTRE_B, 0, 20).items()).isEmpty();
    }
}
