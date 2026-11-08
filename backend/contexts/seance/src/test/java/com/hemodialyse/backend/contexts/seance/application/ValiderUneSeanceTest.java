package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE2 : validation de la séance par l'infirmier, ports en mémoire. */
class ValiderUneSeanceTest {

    private static final PlaceDeSeance PLACE = new PlaceDeSeance(new UUID(3, 1), new UUID(4, 1), new UUID(5, 1));

    private Banc banc;
    private PatientId patient;
    private Seance creee;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        patient = banc.patient(CENTRE_A, 1, "P001", "A1");
        creee = banc.ligne(CENTRE_A, patient, 1, StatutDeSeance.CREE);
        banc.seances.semer(creee);
    }

    private Seance relue() {
        return banc.seances.trouver(CENTRE_A, creee.id()).orElseThrow();
    }

    @Test
    void should_validate_a_created_session_as_the_signatory_of_the_body_and_broadcast() {
        banc.places.put(Banc.cle(CENTRE_A, patient), PLACE);

        var resultat = banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null);

        assertThat(resultat.seance().statut()).isEqualTo(StatutDeSeance.VALIDEE);
        var lue = relue();
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(lue.traces().infirmier()).isEqualTo("inf-01");
        assertThat(lue.jalons().valideeLe()).isEqualTo(MAINTENANT);
        assertThat(lue.traces().place()).isEqualTo(PLACE);
        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_VALIDATED:Nom1", "SEANCE_VALIDEE:u-infirmier");
        assertThat(banc.seances.verrousDeLigneDemandes).isEqualTo(1);
    }

    @Test
    void should_default_the_signatory_to_system_and_refuse_more_than_100_characters_without_writing() {
        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), null, List.of());
        assertThat(relue().traces().infirmier()).isEqualTo("system");

        var autre = banc.ligne(CENTRE_A, patient, 2, StatutDeSeance.CREE);
        banc.seances.semer(autre);
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), autre.id().valeur(), "x".repeat(101), null))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(banc.seances.trouver(CENTRE_A, autre.id()).orElseThrow().statut()).isEqualTo(StatutDeSeance.CREE);
    }

    @Test
    void should_keep_the_first_validation_but_broadcast_again_when_validated_twice() {
        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null);
        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-02", null);

        assertThat(relue().traces().infirmier()).isEqualTo("inf-01");
        assertThat(banc.diffusionsRecues).hasSize(4);
    }

    @Test
    void should_memorize_a_place_that_appears_between_two_validations_without_changing_the_status() {
        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null);
        assertThat(relue().traces().place()).isNull();

        banc.places.put(Banc.cle(CENTRE_A, patient), PLACE);
        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null);

        assertThat(relue().traces().place()).isEqualTo(PLACE);
    }

    @Test
    void should_refuse_a_nurse_on_a_past_day_until_the_administrator_unlocks_it_but_never_an_administrator() {
        banc.horlogeA("2026-06-20T10:00:00Z");

        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_REGULARISATION_NON_DEVERROUILLEE"));
        assertThat(banc.diffusionsRecues).isEmpty();

        banc.session.administrateur();
        banc.deverrouiller().executer(CENTRE_A.valeur(), creee.id().valeur());
        banc.session.administrateur = false;
        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null);

        assertThat(relue().statut()).isEqualTo(StatutDeSeance.VALIDEE);
    }

    @Test
    void should_evaluate_the_past_day_lock_before_the_status() {
        banc.horlogeA("2026-06-20T10:00:00Z");
        var signee = banc.ligne(CENTRE_A, patient, 2, StatutDeSeance.SIGNEE);
        banc.seances.semer(signee);

        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), signee.id().valeur(), "inf-01", null))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_REGULARISATION_NON_DEVERROUILLEE"));
        banc.session.administrateur();
        banc.valider().executer(CENTRE_A.valeur(), signee.id().valeur(), "adm", null);
        assertThat(banc.seances.trouver(CENTRE_A, signee.id()).orElseThrow().statut()).isEqualTo(StatutDeSeance.SIGNEE);
    }

    @Test
    void should_use_the_day_of_the_centre_for_the_lock_of_past_days() {
        banc.horlogeA("2026-06-15T23:30:00Z"); // 00h30 le 16 à Alger : la séance du 15 est déjà passée
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null))
                .isInstanceOf(SeanceRefusee.class);
    }

    @Test
    void should_validate_a_future_session_without_limit() {
        banc.horlogeA("2026-06-01T10:00:00Z");
        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null);
        assertThat(relue().statut()).isEqualTo(StatutDeSeance.VALIDEE);
    }

    @Test
    void should_refuse_a_unknown_session_a_session_of_another_centre_and_a_missing_patient() {
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), new UUID(8, 8), "inf-01", null))
                .isInstanceOf(SeanceIntrouvable.class);
        var autreCentre = banc.ligne(CENTRE_B, patient, 3, StatutDeSeance.CREE);
        banc.seances.semer(autreCentre);
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), autreCentre.id().valeur(), "inf-01", null))
                .isInstanceOf(SeanceIntrouvable.class);
        banc.patients.fiches.clear();
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", null))
                .isInstanceOf(PatientIntrouvable.class);
        assertThat(relue().statut()).isEqualTo(StatutDeSeance.CREE);
    }

    @Test
    void should_refuse_a_centre_that_is_not_the_one_of_the_session_before_any_other_check() {
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_B.valeur(), new UUID(8, 8), "inf-01", null))
                .isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_record_the_consumables_and_add_them_again_on_a_repeated_validation() {
        UUID article = new UUID(6, 1);
        banc.articles.put(article, new ConsommationsALaValidation.Article(article, "ART-1", true));
        var lignes = List.of(new ValiderUneSeance.Ligne(article, new BigDecimal("3")));

        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", lignes);
        banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01", List.of(new ValiderUneSeance.Ligne(article, new BigDecimal("2"))));

        assertThat(banc.consommationsEnregistrees).containsExactly(
                creee.id().valeur() + "/" + article + "/3/inf-01", creee.id().valeur() + "/" + article + "/2/inf-01");
    }

    @Test
    void should_refuse_an_unknown_an_inactive_and_a_null_article_without_validating() {
        UUID inactif = new UUID(6, 2);
        banc.articles.put(inactif, new ConsommationsALaValidation.Article(inactif, "ART-OFF", false));

        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "i",
                List.of(new ValiderUneSeance.Ligne(new UUID(6, 9), BigDecimal.ONE))))
                .isInstanceOf(SaisieInvalide.class).hasMessageStartingWith("Article introuvable");
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "i",
                List.of(new ValiderUneSeance.Ligne(inactif, BigDecimal.ONE))))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Article inactif: ART-OFF");
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "i", java.util.Arrays.asList(
                (ValiderUneSeance.Ligne) null)))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(relue().statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_undo_the_validation_and_the_place_when_the_stock_refuses_and_broadcast_nothing() {
        UUID article = new UUID(6, 1);
        banc.articles.put(article, new ConsommationsALaValidation.Article(article, "ART-1", true));
        banc.places.put(Banc.cle(CENTRE_A, patient), PLACE);
        banc.refusDuStock = "SEANCE_FACTUREE_NON_MODIFIABLE";

        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), creee.id().valeur(), "inf-01",
                List.of(new ValiderUneSeance.Ligne(article, BigDecimal.ONE))))
                .isInstanceOfSatisfying(ConsommationRefusee.class, e -> assertThat(e.code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE"));

        var lue = relue();
        assertThat(lue.statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(lue.traces().place()).isNull();
        assertThat(lue.jalons().valideeLe()).isNull();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_refuse_a_billed_session_and_the_legacy_absent_status() {
        var facturee = banc.ligne(CENTRE_A, patient, 2, StatutDeSeance.FACTUREE);
        var absente = banc.ligne(CENTRE_A, patient, 3, StatutDeSeance.ABSENT);
        banc.seances.semer(facturee);
        banc.seances.semer(absente);

        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), facturee.id().valeur(), "i", null))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE"));
        assertThatThrownBy(() -> banc.valider().executer(CENTRE_A.valeur(), absente.id().valeur(), "i", null))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.getMessage()).isEqualTo("La seance n'est pas en statut CREE"));
        assertThat(banc.diffusionsRecues).isEmpty();
    }
}
