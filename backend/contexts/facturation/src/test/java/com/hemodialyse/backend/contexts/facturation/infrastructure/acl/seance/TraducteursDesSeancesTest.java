package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ExclusionsDeSeancesFournies;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ExclusionsDeSeancesFournies.SeanceExcluableFournie;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.LiaisonDesSeancesFournie;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies.PatientFourni;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies.SeanceAFacturerFournie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Traducteurs de la Facturation vers les séances fournies (ADR 0097, SE7c2) : traduction pure, politique de forfait, refus qui traversent. */
class TraducteursDesSeancesTest {

    private static final UUID CENTRE = UUID.fromString("99997720-0000-0000-0000-00000000000a");
    private static final UUID PATIENT = UUID.fromString("99997720-0000-0000-0000-00000000a001");
    private static final UUID SEANCE = UUID.fromString("99997720-0000-0000-0000-0000000000e1");
    private static final UUID HD = UUID.fromString("99997720-0000-0000-0000-0000000f0001");
    private static final UUID PREMIUM = UUID.fromString("99997720-0000-0000-0000-0000000f0002");
    private static final PatientFourni UN_PATIENT = new PatientFourni(PATIENT, "P-001", "Amrani", "Lina", "PERMANENT", "ASS-P-001",
            UUID.fromString("99997720-0000-0000-0000-000000000c01"), UUID.fromString("99997720-0000-0000-0000-000000000a61"));

    // ---- séances à facturer et politique de forfait

    private static SeanceAFacturerFournie ligne(UUID id, String jour, UUID imposeId, String prixImpose) {
        return new SeanceAFacturerFournie(id, LocalDate.parse(jour), "VALIDEE", imposeId, imposeId == null ? null : "Forfait HD Premium",
                prixImpose == null ? null : new BigDecimal(prixImpose), UN_PATIENT);
    }

    private static PriseEnChargeTarifee pec(UUID forfait, String libelle, String prix) {
        return new PriseEnChargeTarifee(forfait, libelle, new BigDecimal(prix), true, LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31"), null, null, Instant.parse("2026-01-01T08:00:00Z"));
    }

    @ParameterizedTest(name = "forfait imposé {0} -> {1}")
    @CsvSource({"true,99997720-0000-0000-0000-0000000f0002", "false,99997720-0000-0000-0000-0000000f0001"})
    void should_apply_the_imposed_forfait_before_the_prise_en_charge(boolean impose, UUID attendu) {
        SeancesAFacturerFournies seances = (centre, debut, fin) -> List.of(ligne(SEANCE, "2026-08-02", impose ? PREMIUM : null,
                impose ? "5200.00" : null));
        CouvertureDesPatients couverture = mock(CouvertureDesPatients.class);
        when(couverture.prisesEnChargeTarifees(CENTRE, PATIENT)).thenReturn(List.of(pec(HD, "Forfait HD", "3500.00")));

        List<SeanceFacturable> lues = new SeancesFacturablesPubliees(seances, couverture).dansLaPeriode(new CentreId(CENTRE),
                new PeriodeDeFacturation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertThat(lues).hasSize(1);
        assertThat(lues.getFirst().forfait().id()).isEqualTo(attendu);
        assertThat(lues.getFirst().statut()).isEqualTo(new StatutDeSeance("VALIDEE"));
        assertThat(lues.getFirst().patient().numeroDImmatriculation()).isEqualTo("ASS-P-001");
        assertThat(lues.getFirst().patient().centrePayeur()).isEqualTo(UN_PATIENT.centrePayeur());
        assertThat(lues.getFirst().patient().agence()).isEqualTo(UN_PATIENT.agence());
        if (impose) {
            assertThat(lues.getFirst().forfait().prixHt()).isEqualByComparingTo("5200.00");
        }
    }

    @Test
    void should_read_the_prises_en_charge_of_a_patient_once_and_keep_the_order_and_the_undefined_forfait() {
        UUID deuxieme = UUID.fromString("99997720-0000-0000-0000-0000000000e2");
        SeancesAFacturerFournies seances = (centre, debut, fin) -> List.of(ligne(SEANCE, "2026-08-02", null, null),
                ligne(deuxieme, "2026-08-03", null, null));
        CouvertureDesPatients couverture = mock(CouvertureDesPatients.class);
        when(couverture.prisesEnChargeTarifees(any(), any())).thenReturn(List.of());

        List<SeanceFacturable> lues = new SeancesFacturablesPubliees(seances, couverture).dansLaPeriode(new CentreId(CENTRE),
                new PeriodeDeFacturation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertThat(lues).extracting(SeanceFacturable::id).containsExactly(SEANCE, deuxieme);
        assertThat(lues).extracting(SeanceFacturable::forfait).containsOnly(ForfaitApplique.NON_DEFINI);
        verify(couverture, times(1)).prisesEnChargeTarifees(CENTRE, PATIENT);
    }

    // ---- exclusion, réinclusion, forfait imposé

    /** Port fourni de test : enregistre les appels et lève le refus 422 demandé. */
    private static final class Exclusions implements ExclusionsDeSeancesFournies {
        final List<String> appels = new ArrayList<>();
        Optional<SeanceExcluableFournie> trouvee = Optional.empty();
        boolean refuse;

        private void ecrire(String appel) {
            if (refuse) {
                throw new IllegalStateException("Seance introuvable ou non modifiable pour exclusion");
            }
            appels.add(appel);
        }

        @Override
        public Optional<SeanceExcluableFournie> trouver(UUID centre, UUID seance) {
            return trouvee;
        }

        @Override
        public void exclure(UUID centre, UUID seance, String statutAvant) {
            ecrire("exclure/" + centre + "/" + seance + "/" + statutAvant);
        }

        @Override
        public void lierALAbsence(UUID centre, UUID seance, UUID absence) {
            ecrire("lier/" + centre + "/" + seance + "/" + absence);
        }

        @Override
        public void reinclure(UUID centre, UUID seance, String statutRetabli) {
            ecrire("reinclure/" + centre + "/" + seance + "/" + statutRetabli);
        }

        @Override
        public void poserLeForfait(UUID centre, UUID seance, UUID forfait, String libelle, BigDecimal prixHt, String auteur, Instant le) {
            ecrire("forfait/" + forfait + "/" + libelle + "/" + prixHt.toPlainString() + "/" + auteur + "/" + le);
        }
    }

    @ParameterizedTest(name = "mémoire {0} -> {1}")
    @CsvSource(value = {"SIGNEE,SIGNEE", "validee,validee", "NULL,NULL"}, nullValues = "NULL")
    void should_translate_the_seance_found_with_its_memory_of_the_exclusion(String memoire, String attendu) {
        Exclusions fournies = new Exclusions();
        UUID absence = UUID.randomUUID();
        fournies.trouvee = Optional.of(new SeanceExcluableFournie(SEANCE, PATIENT, LocalDate.of(2026, 8, 2), "ABSENT", false, memoire, absence));

        SeanceDeFacturation s = new SeancesDeFacturationPubliees(fournies).trouver(new CentreId(CENTRE), SEANCE).orElseThrow();

        assertThat(s.id()).isEqualTo(SEANCE);
        assertThat(s.centre()).isEqualTo(new CentreId(CENTRE));
        assertThat(s.patientId()).isEqualTo(PATIENT);
        assertThat(s.statut()).isEqualTo(new StatutDeSeance("ABSENT"));
        assertThat(s.facturee()).isFalse();
        assertThat(s.statutAvantExclusion()).isEqualTo(attendu == null ? null : new StatutDeSeance(attendu));
        assertThat(s.absenceDeLExclusion()).isEqualTo(absence);
    }

    @Test
    void should_not_find_an_unknown_seance_and_pass_the_writes_with_identical_values() {
        Exclusions fournies = new Exclusions();
        SeancesDeFacturationPubliees seances = new SeancesDeFacturationPubliees(fournies);
        CentreId centre = new CentreId(CENTRE);
        UUID absence = UUID.randomUUID();
        Instant le = Instant.parse("2026-10-20T08:00:00Z");

        assertThat(seances.trouver(centre, SEANCE)).isEmpty();
        seances.exclure(centre, SEANCE, new StatutDeSeance("SIGNEE"));
        seances.lierALAbsence(centre, SEANCE, absence);
        seances.reinclure(centre, SEANCE, new StatutDeSeance("validee"));
        seances.poserLeForfait(centre, SEANCE, new ForfaitApplique(PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00")), "medecin", le);

        assertThat(fournies.appels).containsExactly(
                "exclure/" + CENTRE + "/" + SEANCE + "/SIGNEE",
                "lier/" + CENTRE + "/" + SEANCE + "/" + absence,
                "reinclure/" + CENTRE + "/" + SEANCE + "/validee",
                "forfait/" + PREMIUM + "/Forfait HD Premium/5200.00/medecin/" + le);
    }

    @Test
    void should_let_the_refusal_of_the_seance_through_untouched() {
        Exclusions fournies = new Exclusions();
        fournies.refuse = true;
        SeancesDeFacturationPubliees seances = new SeancesDeFacturationPubliees(fournies);

        assertThatThrownBy(() -> seances.exclure(new CentreId(CENTRE), SEANCE, new StatutDeSeance("VALIDEE")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Seance introuvable ou non modifiable pour exclusion");
    }

    // ---- liaison séance -> facture (ARGENT)

    @Test
    void should_pass_the_link_to_the_seance_and_let_its_refusal_cancel_the_whole_emission() {
        List<String> liees = new ArrayList<>();
        UUID facture = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        LiaisonDesSeancesFournie fournie = (centre, f, seances) -> {
            if (seances.contains(autre)) {
                throw new IllegalStateException("Une ou plusieurs seances ne sont plus eligibles a la validation");
            }
            liees.add(centre + "/" + f + "/" + seances);
        };
        LiaisonDesSeancesPubliee liaison = new LiaisonDesSeancesPubliee(fournie);

        liaison.lier(new CentreId(CENTRE), facture, List.of(SEANCE));
        assertThat(liees).containsExactly(CENTRE + "/" + facture + "/[" + SEANCE + "]");
        assertThatThrownBy(() -> liaison.lier(new CentreId(CENTRE), facture, List.of(SEANCE, autre)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Une ou plusieurs seances ne sont plus eligibles a la validation");
    }
}
