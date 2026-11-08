package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.ForfaitDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE5 : imposition d'un forfait à une séance (surcharge), ports en mémoire. */
class ImposerUnForfaitTest {

    private static final UUID F1 = new UUID(6, 1);
    private static final UUID F2 = new UUID(6, 2);
    private static final UUID F_AILLEURS = new UUID(6, 3);
    private static final UUID F_SANS_PRIX = new UUID(6, 4);
    private static final UUID F_DE_LA_PEC = new UUID(6, 9);

    private Banc banc;
    private PatientId patient;
    private final Map<String, ForfaitDuCentre> referentiel = new HashMap<>();
    private int lecturesDuForfaitCourant;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        banc.session.administrateur();
        patient = banc.patient(CENTRE_A, 1, "P001", "A1");
        referentiel.put(CENTRE_A.valeur() + "/" + F1, new ForfaitDuCentre(new ForfaitId(F1), "F1", "Forfait 1", new BigDecimal("250.50")));
        referentiel.put(CENTRE_A.valeur() + "/" + F2, new ForfaitDuCentre(new ForfaitId(F2), "F2", "Forfait 2", new BigDecimal("0.00")));
        referentiel.put(CENTRE_A.valeur() + "/" + F_SANS_PRIX, new ForfaitDuCentre(new ForfaitId(F_SANS_PRIX), "SP", "Sans prix", null));
        referentiel.put(CENTRE_B.valeur() + "/" + F_AILLEURS, new ForfaitDuCentre(new ForfaitId(F_AILLEURS), "FB", "Forfait B", new BigDecimal("9.00")));
    }

    private ImposerUnForfait cas() {
        ForfaitsDuCentre catalogue = (centre, forfait) -> Optional.ofNullable(referentiel.get(centre.valeur() + "/" + forfait.valeur()));
        ForfaitsDeSeance courants = (centre, seance, pat, jour) -> {
            lecturesDuForfaitCourant++;
            return banc.seances.trouver(centre, seance).flatMap(Seance::forfaitImpose)
                    .map(f -> new ForfaitsDeSeance.ForfaitCourant(f.forfait().valeur(), f.code(), f.nom(), f.prix(), null,
                            OffsetDateTime.ofInstant(f.imposeLe(), ZoneOffset.UTC), f.auteur(), true))
                    .or(() -> Optional.of(new ForfaitsDeSeance.ForfaitCourant(F_DE_LA_PEC, "PEC", "De la PEC", BigDecimal.TEN, 0, null, null,
                            false)));
        };
        return new ImposerUnForfait(banc.session, banc.seances, catalogue, courants, banc.unite(), banc.horloge());
    }

    private Seance semer(int n, StatutDeSeance statut) {
        var s = banc.ligne(CENTRE_A, patient, n, statut);
        banc.seances.semer(s);
        return s;
    }

    private Seance relue(Seance s) {
        return banc.seances.trouver(CENTRE_A, s.id()).orElseThrow();
    }

    @Test
    void should_impose_the_forfait_as_a_snapshot_dated_by_the_clock_of_the_centre_and_return_it_as_the_current_forfait() {
        var s = semer(1, StatutDeSeance.CREE);

        var resultat = cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, "compta-01");

        var impose = relue(s).forfaitImpose().orElseThrow();
        assertThat(impose.forfait().valeur()).isEqualTo(F1);
        assertThat(impose.code()).isEqualTo("F1");
        assertThat(impose.nom()).isEqualTo("Forfait 1");
        assertThat(impose.prix()).isEqualByComparingTo("250.50");
        assertThat(impose.imposeLe()).isEqualTo(MAINTENANT);
        assertThat(impose.auteur()).isEqualTo("compta-01");
        assertThat(resultat.seance().statut()).isEqualTo(StatutDeSeance.CREE);
        var courant = resultat.forfait().orElseThrow();
        assertThat(courant.surcharge()).isTrue();
        assertThat(courant.code()).isEqualTo("F1");
        assertThat(courant.misAJourPar()).isEqualTo("compta-01");
        assertThat(banc.diffusionsRecues).isEmpty();
        assertThat(banc.seances.verrousDeLigneDemandes).isEqualTo(1);
    }

    @Test
    void should_take_the_author_of_the_body_trimmed_and_not_the_one_of_the_session() {
        var s = semer(1, StatutDeSeance.CREE);

        cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, "   quelqu-un-d-autre   ");

        assertThat(banc.session.utilisateur()).isEqualTo("u-admin");
        assertThat(relue(s).forfaitImpose().orElseThrow().auteur()).isEqualTo("quelqu-un-d-autre");
    }

    @Test
    void should_replace_the_previous_forfait_without_history() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, "compta-01");
        banc.horlogeA("2026-06-15T11:00:00Z");

        cas().executer(CENTRE_A.valeur(), s.id().valeur(), F2, "compta-02");

        var impose = relue(s).forfaitImpose().orElseThrow();
        assertThat(impose.code()).isEqualTo("F2");
        assertThat(impose.auteur()).isEqualTo("compta-02");
        assertThat(impose.imposeLe()).isAfter(MAINTENANT);
    }

    @Test
    void should_copy_a_missing_or_zero_price_as_it_is_without_any_alert() {
        var a = semer(1, StatutDeSeance.CREE);
        var b = semer(2, StatutDeSeance.CREE);

        cas().executer(CENTRE_A.valeur(), a.id().valeur(), F_SANS_PRIX, "compta-01");
        cas().executer(CENTRE_A.valeur(), b.id().valeur(), F2, "compta-01");

        assertThat(relue(a).forfaitImpose().orElseThrow().prix()).isNull();
        assertThat(relue(b).forfaitImpose().orElseThrow().prix()).isEqualByComparingTo("0.00");
    }

    @Test
    void should_accept_every_status_but_the_invoiced_one() {
        int n = 1;
        for (StatutDeSeance statut : StatutDeSeance.values()) {
            if (statut == StatutDeSeance.FACTUREE) continue;
            var s = semer(n++, statut);
            cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, "compta-01");
            assertThat(relue(s).forfaitImpose()).as(statut.name()).isPresent();
            assertThat(relue(s).statut()).isEqualTo(statut);
        }
    }

    @Test
    void should_refuse_an_invoiced_session_with_422_and_write_nothing() {
        var s = semer(1, StatutDeSeance.FACTUREE);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, "compta-01"))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE"));
        assertThat(relue(s).forfaitImpose()).isEmpty();
        assertThat(lecturesDuForfaitCourant).isZero();
    }

    @Test
    void should_refuse_a_centre_other_than_the_session_one_before_any_other_check() {
        var s = semer(1, StatutDeSeance.CREE);

        assertThatThrownBy(() -> cas().executer(CENTRE_B.valeur(), s.id().valeur(), F_AILLEURS, "compta-01"))
                .isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> cas().executer(CENTRE_B.valeur(), s.id().valeur(), null, ""))
                .isInstanceOf(AccesRefuse.class);
        assertThat(relue(s).forfaitImpose()).isEmpty();
    }

    @Test
    void should_report_an_unknown_session_or_a_session_of_another_centre_as_unknown() {
        var s = semer(1, StatutDeSeance.CREE);
        banc.session.centre = CENTRE_B;

        assertThatThrownBy(() -> cas().executer(CENTRE_B.valeur(), s.id().valeur(), F_AILLEURS, "compta-01"))
                .isInstanceOf(SeanceIntrouvable.class);
        assertThatThrownBy(() -> cas().executer(CENTRE_B.valeur(), new UUID(5, 99), F_AILLEURS, "compta-01"))
                .isInstanceOf(SeanceIntrouvable.class);
    }

    @Test
    void should_refuse_an_unknown_forfait_or_a_forfait_of_another_centre_with_the_message_of_the_legacy() {
        var s = semer(1, StatutDeSeance.CREE);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), new UUID(6, 99), "compta-01"))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Forfait introuvable pour le centre actif");
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), F_AILLEURS, "compta-01"))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Forfait introuvable pour le centre actif");
        assertThat(relue(s).forfaitImpose()).isEmpty();
    }

    @Test
    void should_refuse_a_missing_forfait_and_a_blank_or_too_long_author_with_400_and_write_nothing() {
        var s = semer(1, StatutDeSeance.CREE);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), null, "compta-01")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, "   ")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, "y".repeat(101)))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(relue(s).forfaitImpose()).isEmpty();
    }

    @Test
    void should_refuse_an_invoiced_session_with_422_before_checking_the_author_as_the_legacy_did() {
        var facturee = semer(1, StatutDeSeance.FACTUREE);
        var creee = semer(2, StatutDeSeance.CREE);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), facturee.id().valeur(), F1, "y".repeat(101)))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE"));
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), creee.id().valeur(), F1, "y".repeat(101)))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(relue(facturee).forfaitImpose()).isEmpty();
        assertThat(relue(creee).forfaitImpose()).isEmpty();
    }

    @Test
    void should_accept_an_author_of_exactly_one_hundred_characters() {
        var s = semer(1, StatutDeSeance.CREE);

        cas().executer(CENTRE_A.valeur(), s.id().valeur(), F1, "x".repeat(100));

        assertThat(relue(s).forfaitImpose().orElseThrow().auteur()).hasSize(100);
    }

    @Test
    void should_report_an_unreadable_session_without_a_technical_class_name() {
        banc.seances.semerUneLigneIllisible(CENTRE_A, new com.hemodialyse.backend.contexts.seance.domain.SeanceId(new UUID(5, 7)));

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), new UUID(5, 7), F1, "compta-01"))
                .isInstanceOfSatisfying(SeanceIllisible.class, e -> assertThat(e.getMessage()).doesNotContain("com.hemodialyse"));
    }

    @Test
    void should_keep_the_imposition_when_only_the_reading_of_the_current_forfait_fails() {
        var s = semer(1, StatutDeSeance.CREE);
        // l'unité de travail est terminée quand le forfait courant est relu : un échec de relecture ne défait pas l'imposition (comme le legacy)
        ForfaitsDeSeance cassee = (centre, seance, pat, jour) -> {
            throw new IllegalStateException("lecture impossible");
        };
        ForfaitsDuCentre catalogue = (centre, forfait) -> Optional.ofNullable(referentiel.get(centre.valeur() + "/" + forfait.valeur()));
        var cas = new ImposerUnForfait(banc.session, banc.seances, catalogue, cassee, banc.unite(), banc.horloge());

        assertThatThrownBy(() -> cas.executer(CENTRE_A.valeur(), s.id().valeur(), F1, "compta-01")).isInstanceOf(IllegalStateException.class);
        assertThat(relue(s).forfaitImpose()).isPresent();
    }
}
