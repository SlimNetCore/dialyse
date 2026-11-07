package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.SerologiesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieIntrouvable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des sérologies, avec les doubles en mémoire des ports. */
class SerologiesTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 1);

    private final SerologiesEnMemoire serologies = new SerologiesEnMemoire();
    private final List<String> appels = new ArrayList<>();
    private RuntimeException refusDeLaPlanification;
    private final IsolementDesPatients isolement = new IsolementDesPatients() {
        @Override
        public void verrouillerLesPlaces(CentreId centre, PatientId patient) {
            appels.add("verrou");
        }

        @Override
        public void reevaluer(CentreId centre, PatientId patient) {
            appels.add("reevaluation avec " + serologies.toutesDuPatient(centre, patient).size() + " serologie(s)");
            if (refusDeLaPlanification != null) throw refusDeLaPlanification;
        }
    };
    private final AtomicInteger identifiants = new AtomicInteger();
    private final HorlogeDuCentre horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);
    private final SessionCourante session = demande -> CENTRE;
    private final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T independante(Supplier<T> action) {
            return action.get();
        }
    };
    private final EnregistrerUneSerologie enregistrer = new EnregistrerUneSerologie(session, serologies, isolement, unite,
            () -> new UUID(0, identifiants.incrementAndGet()), horloge);
    private final CorrigerUneSerologie corriger = new CorrigerUneSerologie(session, serologies, isolement, unite, horloge);
    private final SupprimerUneSerologie supprimer = new SupprimerUneSerologie(session, serologies, unite);
    private final ConsulterLesSerologiesDuPatient consulter = new ConsulterLesSerologiesDuPatient(session, serologies);

    private Serologie enregistrerUne(String marqueur, String resultat, LocalDate date, String conduite) {
        return enregistrer.executer(null, PATIENT, marqueur, resultat, null, null, date, null, null, conduite);
    }

    @Test
    void la_date_absente_est_le_jour_de_l_horloge() {
        assertThat(enregistrerUne("TPHA", "NEGATIF", null, null).datePrelevement()).isEqualTo(JOUR);
    }

    @Test
    void la_date_future_est_refusee_sans_rien_ecrire() {
        assertThatThrownBy(() -> enregistrerUne("TPHA", "NEGATIF", JOUR.plusDays(1), null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("SEROLOGIE_DATE_FUTURE"));
        assertThat(serologies.toutesDuPatient(CENTRE, new PatientId(PATIENT))).isEmpty();
    }

    @Test
    void le_centre_verrouille_les_places_avant_l_ecriture_puis_la_planification_reevalue_apres() {
        enregistrerUne("VIH_AC", "POSITIF", JOUR, "Isoler");
        assertThat(appels).containsExactly("verrou", "reevaluation avec 1 serologie(s)");
    }

    @Test
    void un_resultat_positif_sans_conduite_est_refuse_et_la_planification_n_est_pas_reevaluee() {
        assertThatThrownBy(() -> enregistrerUne("VIH_AC", "POSITIF", JOUR, "  ")).isInstanceOf(RegleDeGestionViolee.class);
        assertThat(appels).containsExactly("verrou");
    }

    @Test
    void un_doublon_marqueur_et_date_est_refuse_avec_le_code_du_legacy() {
        enregistrerUne("AG_HBS", "NEGATIF", JOUR, null);
        assertThatThrownBy(() -> enregistrerUne("AG_HBS", "POSITIF", JOUR, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> {
                    assertThat(e.code()).isEqualTo("BUSINESS_RULE_VIOLATION");
                    assertThat(e.getMessage()).isEqualTo("Un résultat existe déjà pour ce marqueur à cette date de prélèvement");
                });
        enregistrerUne("AG_HBS", "NEGATIF", JOUR.minusDays(1), null);
        enregistrerUne("TPHA", "NEGATIF", JOUR, null);
    }

    @Test
    void un_marqueur_ou_un_resultat_inconnu_ou_absent_est_une_saisie_invalide_avant_tout_verrou() {
        assertThatThrownBy(() -> enregistrerUne("HIV", "NEGATIF", JOUR, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrerUne(null, "NEGATIF", JOUR, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrerUne("TPHA", "negatif", JOUR, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrerUne("TPHA", null, JOUR, null)).isInstanceOf(SaisieInvalide.class);
        assertThat(appels).isEmpty();
    }

    @Test
    void le_refus_de_la_planification_traverse_le_cas_d_usage_pour_que_la_transaction_soit_annulee() {
        refusDeLaPlanification = new IllegalStateException("PLACEMENT_PATIENT_INTROUVABLE");
        assertThatThrownBy(() -> enregistrerUne("TPHA", "NEGATIF", JOUR, null)).isSameAs(refusDeLaPlanification);
    }

    @Test
    void la_correction_remplace_le_resultat_et_la_conduite_meme_absente_puis_la_planification_reevalue() {
        var s = enregistrerUne("AG_HBS", "POSITIF", JOUR.minusDays(3), "Isoler");
        appels.clear();
        var corrigee = corriger.executer(null, PATIENT, s.id(), "NEGATIF", null);
        assertThat(corrigee.resultat()).isEqualTo(ResultatSerologique.NEGATIF);
        assertThat(corrigee.conduiteATenir()).isNull();
        assertThat(corrigee.datePrelevement()).isEqualTo(JOUR.minusDays(3));
        assertThat(appels).containsExactly("verrou", "reevaluation avec 1 serologie(s)");
    }

    @Test
    void la_correction_vers_positif_sans_conduite_est_refusee_et_ne_change_rien() {
        var s = enregistrerUne("AG_HBS", "NEGATIF", JOUR, null);
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, s.id(), "POSITIF", null)).isInstanceOf(RegleDeGestionViolee.class);
        assertThat(serologies.trouverEnVerrouillant(CENTRE, new PatientId(PATIENT), s.id()).orElseThrow().resultat())
                .isEqualTo(ResultatSerologique.NEGATIF);
    }

    @Test
    void corriger_une_serologie_inconnue_ou_d_un_autre_patient_est_refuse_comme_introuvable() {
        var s = enregistrerUne("AG_HBS", "NEGATIF", JOUR, null);
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, UUID.randomUUID(), "NEGATIF", null)).isInstanceOf(SerologieIntrouvable.class);
        assertThatThrownBy(() -> corriger.executer(null, UUID.randomUUID(), s.id(), "NEGATIF", null)).isInstanceOf(SerologieIntrouvable.class);
    }

    @Test
    void un_resultat_illisible_a_la_correction_est_refuse_avant_la_recherche() {
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, UUID.randomUUID(), "?", null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_suppression_dit_si_elle_a_supprime_et_n_appelle_pas_la_planification() {
        var s = enregistrerUne("TPHA", "NEGATIF", JOUR, null);
        appels.clear();
        assertThat(supprimer.executer(null, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(supprimer.executer(null, UUID.randomUUID(), s.id())).isFalse();
        assertThat(supprimer.executer(null, PATIENT, s.id())).isTrue();
        assertThat(supprimer.executer(null, PATIENT, s.id())).isFalse();
        assertThat(appels).isEmpty();
    }

    @Test
    void les_bornes_de_pagination_du_legacy_sont_reproduites_et_un_patient_inconnu_a_une_page_vide() {
        for (int i = 1; i <= 11; i++) enregistrerUne("TPHA", "NEGATIF", JOUR.minusDays(i), null);
        assertThat(consulter.liste(null, PATIENT, -5, 0).taille()).isEqualTo(20);
        assertThat(consulter.liste(null, PATIENT, -5, 0).page()).isZero();
        assertThat(consulter.liste(null, PATIENT, 0, 1000).taille()).isEqualTo(200);
        assertThat(consulter.liste(null, PATIENT, 1, 10).items()).hasSize(1);
        assertThat(consulter.liste(null, UUID.randomUUID(), 0, 20).total()).isZero();
        assertThat(consulter.synthese(null, UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_synthese_rend_le_dernier_resultat_par_marqueur() {
        enregistrerUne("TPHA", "POSITIF", JOUR.minusDays(10), "Traiter");
        enregistrerUne("TPHA", "NEGATIF", JOUR.minusDays(1), null);
        enregistrerUne("VIH_AC", "NEGATIF", JOUR.minusDays(2), null);
        var synthese = consulter.synthese(null, PATIENT);
        assertThat(synthese).extracting(s -> s.marqueur().name(), s -> s.resultat().name())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("VIH_AC", "NEGATIF"), org.assertj.core.groups.Tuple.tuple("TPHA", "NEGATIF"));
    }

    @Test
    void le_titre_est_transmis_au_domaine_pour_arrondi_et_bornes() {
        var s = enregistrer.executer(null, PATIENT, "TPHA", "NEGATIF", new BigDecimal("12.3456"), "UI", JOUR, null, null, null);
        assertThat(s.titre()).isEqualByComparingTo("12.346");
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, "AG_HBS", "NEGATIF", new BigDecimal("10000000"), null, JOUR, null, null, null))
                .isInstanceOf(SaisieInvalide.class);
    }
}
