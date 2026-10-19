package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé des règles de TVA d'un centre : exécuté contre le double en mémoire, l'adaptateur JDBC sur H2 et l'adaptateur
 * sur PostgreSQL 16 (où l'identifiant venu de la règle rend l'ajout possible, KB-CPT-21).
 */
public abstract class RegleTvaContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996500-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996500-0000-0000-0000-00000000000b"));

    protected static LocalDate jour(String date) {
        return LocalDate.parse(date);
    }

    protected static RegleTvaId nouvelId() {
        return new RegleTvaId(UUID.randomUUID());
    }

    protected static RegleTva regle(CentreId centre, String type, String taux, String debut, String fin) {
        return RegleTva.definir(nouvelId(), centre, type, new BigDecimal(taux), false, jour(debut),
                fin == null ? null : jour(fin), null);
    }

    protected abstract RegleTvaRepository repository();

    /**
     * Une règle déjà en base mais désactivée.
     */
    protected abstract void regleDesactivee(CentreId centre, String type, String debut);

    @Test
    void should_return_no_rule_for_a_centre_that_added_none() {
        assertThat(repository().duCentre(CENTRE_A)).isEmpty();
    }

    @Test
    void should_add_a_rule_and_read_back_all_its_values() {
        RegleTvaId id = nouvelId();
        repository().ajouter(RegleTva.definir(id, CENTRE_A, "HEMODIALYSE", new BigDecimal("9.50"), true, jour("2026-01-01"),
                jour("2026-12-31"), "Article 13"));

        RegleTva relue = repository().duCentre(CENTRE_A).getFirst();

        assertThat(relue.id()).isEqualTo(id);
        assertThat(relue.centre()).isEqualTo(CENTRE_A);
        assertThat(relue.libelle()).isEqualTo("HEMODIALYSE 9.50%");
        assertThat(relue.typePrestation()).isEqualTo("HEMODIALYSE");
        assertThat(relue.taux().valeur()).isEqualByComparingTo("9.50");
        assertThat(relue.exonere()).isTrue();
        assertThat(relue.debutValidite()).isEqualTo(jour("2026-01-01"));
        assertThat(relue.finValidite()).isEqualTo(jour("2026-12-31"));
        assertThat(relue.texteReference()).isEqualTo("Article 13");
        assertThat(relue.active()).isTrue();
    }

    @Test
    void should_add_an_open_rule_without_reference_text() {
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "19", "2026-01-01", null));

        RegleTva relue = repository().duCentre(CENTRE_A).getFirst();

        assertThat(relue.finValidite()).isNull();
        assertThat(relue.texteReference()).isNull();
        assertThat(relue.exonere()).isFalse();
    }

    @Test
    void should_add_several_rules_without_replacing_the_previous_ones() {
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "19", "2026-01-01", null));
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "19", "2026-01-01", null));
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "9", "2027-01-01", null));

        assertThat(repository().duCentre(CENTRE_A)).hasSize(3);
    }

    @Test
    void should_list_rules_from_the_most_recent_start_to_the_oldest_all_types_and_states_together() {
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "19", "2024-01-01", "2025-12-31"));
        repository().ajouter(regle(CENTRE_A, "MEDICAMENT", "5", "2025-06-01", null));
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "9", "2026-01-01", null));
        regleDesactivee(CENTRE_A, "HEMODIALYSE", "2023-01-01");

        assertThat(repository().duCentre(CENTRE_A)).extracting(RegleTva::debutValidite)
                .containsExactly(jour("2026-01-01"), jour("2025-06-01"), jour("2024-01-01"), jour("2023-01-01"));
        assertThat(repository().duCentre(CENTRE_A)).extracting(RegleTva::active).containsExactly(true, true, true, false);
    }

    @Test
    void should_keep_the_rules_of_two_centres_apart() {
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "19", "2026-01-01", null));
        repository().ajouter(regle(CENTRE_B, "HEMODIALYSE", "9", "2026-01-01", null));

        assertThat(repository().duCentre(CENTRE_A)).singleElement().extracting(r -> r.taux().valeur())
                .satisfies(t -> assertThat(t).isEqualByComparingTo("19"));
        assertThat(repository().duCentre(CENTRE_B)).singleElement().extracting(r -> r.taux().valeur())
                .satisfies(t -> assertThat(t).isEqualByComparingTo("9"));
    }

    @Test
    void should_round_the_rate_to_two_decimals() {
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "19.999", "2026-01-01", "2026-01-31"));
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "19.005", "2026-02-01", "2026-02-28"));
        repository().ajouter(regle(CENTRE_A, "HEMODIALYSE", "5.5", "2026-03-01", "2026-03-31"));

        assertThat(repository().duCentre(CENTRE_A)).extracting(r -> r.taux().valeur().toPlainString())
                .containsExactly("5.50", "19.01", "20.00");
    }

    @Test
    void should_keep_the_type_exactly_as_entered() {
        repository().ajouter(regle(CENTRE_A, " HEMODIALYSE ", "19", "2026-01-01", "2026-01-31"));
        repository().ajouter(regle(CENTRE_A, "Médicament  5%", "5", "2026-02-01", "2026-02-28"));

        assertThat(repository().duCentre(CENTRE_A)).extracting(RegleTva::typePrestation)
                .containsExactly("Médicament  5%", " HEMODIALYSE ");
    }

    // ------------------------------------------------------------------ une règle par son identifiant (F1)

    @Test
    void should_find_a_rule_of_the_centre_by_its_id_but_not_from_another_centre() {
        RegleTva regle = regle(CENTRE_A, "HEMODIALYSE", "19", "2026-01-01", null);
        repository().ajouter(regle);

        assertThat(repository().duCentre(CENTRE_A, regle.id())).get().extracting(RegleTva::id).isEqualTo(regle.id());
        assertThat(repository().duCentre(CENTRE_B, regle.id())).isEmpty();
        assertThat(repository().duCentre(CENTRE_A, nouvelId())).isEmpty();
    }

    @Test
    void should_keep_an_entered_label() {
        RegleTva regle = RegleTva.definir(nouvelId(), CENTRE_A, "TVA hémodialyse", "HEMODIALYSE", new BigDecimal("19"), false,
                jour("2026-01-01"), null, null);
        repository().ajouter(regle, "admin");

        assertThat(repository().duCentre(CENTRE_A, regle.id()).orElseThrow().libelle()).isEqualTo("TVA hémodialyse");
    }

    @Test
    void should_replace_the_entered_values_but_keep_the_state() {
        RegleTva regle = regle(CENTRE_A, "HEMODIALYSE", "19", "2026-01-01", null);
        repository().ajouter(regle);
        repository().desactiver(CENTRE_A, regle.id());
        RegleTva desactivee = repository().duCentre(CENTRE_A, regle.id()).orElseThrow();

        repository().remplacer(desactivee.modifier("Modifié", "MEDICAMENT", new BigDecimal("5.5"), true, jour("2025-01-01"),
                jour("2025-12-31"), "Décret"));

        RegleTva relue = repository().duCentre(CENTRE_A, regle.id()).orElseThrow();
        assertThat(relue.libelle()).isEqualTo("Modifié");
        assertThat(relue.typePrestation()).isEqualTo("MEDICAMENT");
        assertThat(relue.taux().valeur()).isEqualByComparingTo("5.50");
        assertThat(relue.exonere()).isTrue();
        assertThat(relue.debutValidite()).isEqualTo(jour("2025-01-01"));
        assertThat(relue.finValidite()).isEqualTo(jour("2025-12-31"));
        assertThat(relue.texteReference()).isEqualTo("Décret");
        assertThat(relue.active()).isFalse();
    }

    @Test
    void should_not_replace_a_rule_of_another_centre() {
        RegleTva regle = regle(CENTRE_A, "HEMODIALYSE", "19", "2026-01-01", null);
        repository().ajouter(regle);

        repository().remplacer(RegleTva.reconstituee(regle.id(), CENTRE_B, "Pris par B", "HEMODIALYSE", new BigDecimal("5"),
                false, jour("2026-01-01"), null, null, true));

        assertThat(repository().duCentre(CENTRE_A, regle.id()).orElseThrow().taux().valeur()).isEqualByComparingTo("19");
        assertThat(repository().duCentre(CENTRE_B)).isEmpty();
    }

    @Test
    void should_deactivate_idempotently_and_only_in_its_centre() {
        RegleTva regle = regle(CENTRE_A, "HEMODIALYSE", "19", "2026-01-01", null);
        repository().ajouter(regle);

        repository().desactiver(CENTRE_B, regle.id());
        assertThat(repository().duCentre(CENTRE_A, regle.id()).orElseThrow().active()).isTrue();

        repository().desactiver(CENTRE_A, regle.id());
        repository().desactiver(CENTRE_A, regle.id());
        repository().desactiver(CENTRE_A, nouvelId());
        assertThat(repository().duCentre(CENTRE_A)).singleElement().extracting(RegleTva::active).isEqualTo(false);
    }
}
