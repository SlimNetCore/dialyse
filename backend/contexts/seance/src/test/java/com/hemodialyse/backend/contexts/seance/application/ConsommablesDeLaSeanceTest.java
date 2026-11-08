package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE3 : ajout, modification et retrait d'un consommable déclaré, ports en mémoire (le double du Stock cumule, fixe et retire). */
class ConsommablesDeLaSeanceTest {

    private static final UUID ART_1 = new UUID(6, 1);
    private static final UUID ART_INACTIF = new UUID(6, 2);

    private Banc banc;
    private PatientId patient;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        patient = banc.patient(CENTRE_A, 1, "P001", "A1");
        banc.articles.put(ART_1, new ConsommationsALaValidation.Article(ART_1, "ART-1", true));
        banc.articles.put(ART_INACTIF, new ConsommationsALaValidation.Article(ART_INACTIF, "ART-X", false));
    }

    private Seance semer(int n, StatutDeSeance statut) {
        var s = banc.ligne(CENTRE_A, patient, n, statut);
        banc.seances.semer(s);
        return s;
    }

    private BigDecimal declare(Seance s, UUID article) {
        return banc.declarees.get(s.id().valeur() + "/" + article);
    }

    private static BigDecimal q(String v) {
        return new BigDecimal(v);
    }

    // ---- Ajout

    @Test
    void should_add_a_consumable_to_a_validated_session_and_broadcast_twice_after_the_write() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("2"));

        assertThat(declare(s, ART_1)).isEqualByComparingTo("2");
        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_CONSOMMABLE_CHANGED:" + s.id().valeur(), "CONSOMMABLE_AJOUT:u-infirmier");
        assertThat(banc.auteursTransmis).containsExactly("u-infirmier");
        assertThat(banc.seances.verrousDeLigneDemandes).isEqualTo(1);
    }

    @Test
    void should_accumulate_two_identical_additions_without_idempotency_key_KB_SEA_23_reproduced() {
        var s = semer(1, StatutDeSeance.SIGNEE);

        banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("2"));
        banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("3"));

        assertThat(declare(s, ART_1)).isEqualByComparingTo("5");
    }

    @Test
    void should_refuse_adding_before_the_validation_and_on_a_billed_session_without_writing_or_broadcasting() {
        var creee = semer(1, StatutDeSeance.CREE);
        var absente = semer(2, StatutDeSeance.ABSENT);
        var facturee = semer(3, StatutDeSeance.FACTUREE);

        for (var s : new Seance[]{creee, absente, facturee}) {
            assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("1")))
                    .isInstanceOf(SeanceRefusee.class);
        }
        assertThat(banc.declarees).isEmpty();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_check_the_quantity_before_looking_for_the_session() {
        assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), new UUID(5, 99), ART_1, BigDecimal.ZERO))
                .isInstanceOf(SaisieInvalide.class).hasMessage("La quantite doit etre strictement positive");
        assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), new UUID(5, 99), ART_1, q("1")))
                .isInstanceOf(SeanceIntrouvable.class);
    }

    @Test
    void should_refuse_the_other_centre_before_any_other_check() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(CENTRE_B.valeur(), s.id().valeur(), ART_1, BigDecimal.ZERO))
                .isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(null, s.id().valeur(), ART_1, q("1")))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Centre requis");
    }

    @Test
    void should_not_write_anything_when_the_patient_has_disappeared_KB_SEA_27_corrected() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        banc.patients.fiches.clear();

        assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("2")))
                .isInstanceOf(PatientIntrouvable.class);

        assertThat(banc.declarees).isEmpty();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_report_the_same_refusal_for_an_unknown_or_inactive_article_and_a_stock_refusal() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), new UUID(6, 99), q("1")))
                .isInstanceOf(SaisieInvalide.class).hasMessageStartingWith("Article introuvable: ");
        assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_INACTIF, q("1")))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Article inactif: ART-X");
        banc.refusDuStock = "PIECE_DEJA_RAPPROCHEE";
        assertThatThrownBy(() -> banc.ajouterUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("1")))
                .isInstanceOfSatisfying(ConsommationRefusee.class, e -> assertThat(e.code()).isEqualTo("PIECE_DEJA_RAPPROCHEE"));
        assertThat(banc.declarees).isEmpty();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    // ---- Modification

    @Test
    void should_set_the_total_quantity_and_create_the_line_of_an_article_never_declared_KB_SEA_23_reproduced() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("3"), "inf-01");

        assertThat(declare(s, ART_1)).isEqualByComparingTo("3");
        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("1"), "inf-01");
        assertThat(declare(s, ART_1)).isEqualByComparingTo("1");
    }

    @Test
    void should_accept_the_modification_before_the_validation_and_on_an_absent_session_but_not_a_billed_one_KB_SEA_24_reproduced() {
        var creee = semer(1, StatutDeSeance.CREE);
        var absente = semer(2, StatutDeSeance.ABSENT);
        var facturee = semer(3, StatutDeSeance.FACTUREE);

        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), creee.id().valeur(), ART_1, q("1"), null);
        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), absente.id().valeur(), ART_1, q("1"), null);
        assertThatThrownBy(() -> banc.modifierUnConsommable().executer(CENTRE_A.valeur(), facturee.id().valeur(), ART_1, q("1"), null))
                .isInstanceOf(SeanceRefusee.class);

        assertThat(banc.declarees).hasSize(2);
    }

    @Test
    void should_transmit_the_author_of_the_body_or_system_and_broadcast_the_session_user_KB_SEA_33_reproduced() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("2"), "autre-compte");
        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("2"), null);

        assertThat(banc.auteursTransmis).containsExactly("autre-compte", "system");
        assertThat(banc.diffusionsRecues).contains("CONSOMMABLE_MODIF:u-infirmier").doesNotContain("CONSOMMABLE_MODIF:autre-compte");
    }

    @Test
    void should_rebroadcast_an_identical_quantity_KB_SEA_28_reproduced() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("2"), null);
        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, q("2"), null);

        assertThat(banc.diffusionsRecues).hasSize(4);
    }

    @Test
    void should_refuse_a_quantity_that_is_zero_negative_or_absent_with_the_modification_message() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        for (BigDecimal invalide : new BigDecimal[]{null, BigDecimal.ZERO, q("-1")}) {
            assertThatThrownBy(() -> banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, invalide, null))
                    .isInstanceOf(SaisieInvalide.class).hasMessage("La nouvelle quantite doit etre strictement positive");
        }
    }

    @Test
    void should_only_let_an_inactive_article_be_reduced_or_kept_as_the_stock_decides() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        banc.declarees.put(s.id().valeur() + "/" + ART_INACTIF, q("4"));

        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_INACTIF, q("2"), null);
        banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_INACTIF, q("2"), null);
        assertThatThrownBy(() -> banc.modifierUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_INACTIF, q("5"), null))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Article inactif: ART-X");
        assertThat(declare(s, ART_INACTIF)).isEqualByComparingTo("2");
    }

    // ---- Retrait

    @Test
    void should_remove_the_article_and_broadcast_even_when_it_was_never_declared_KB_SEA_23_and_28_reproduced() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        banc.declarees.put(s.id().valeur() + "/" + ART_1, q("2"));

        banc.retirerUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, null);
        banc.retirerUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), ART_1, "inf-01");

        assertThat(banc.declarees).isEmpty();
        assertThat(banc.auteursTransmis).containsExactly("system", "inf-01");
        assertThat(banc.diffusionsRecues).hasSize(4).contains("CONSOMMABLE_RETRAIT:u-infirmier");
    }

    @Test
    void should_remove_before_the_validation_but_not_on_a_billed_session() {
        var creee = semer(1, StatutDeSeance.CREE);
        var facturee = semer(2, StatutDeSeance.FACTUREE);

        banc.retirerUnConsommable().executer(CENTRE_A.valeur(), creee.id().valeur(), ART_1, null);
        assertThatThrownBy(() -> banc.retirerUnConsommable().executer(CENTRE_A.valeur(), facturee.id().valeur(), ART_1, null))
                .isInstanceOf(SeanceRefusee.class);
    }

    @Test
    void should_refuse_removal_of_an_unknown_article_with_the_same_refusal_as_the_other_routes() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        assertThatThrownBy(() -> banc.retirerUnConsommable().executer(CENTRE_A.valeur(), s.id().valeur(), new UUID(6, 99), null))
                .isInstanceOf(SaisieInvalide.class).hasMessageStartingWith("Article introuvable: ");
        assertThatThrownBy(() -> banc.retirerUnConsommable().executer(null, s.id().valeur(), ART_1, null))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Centre requis");
    }
}
