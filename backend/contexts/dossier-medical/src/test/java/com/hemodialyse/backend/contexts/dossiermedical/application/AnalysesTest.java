package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.AnalysesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AnalyseIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesuresDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des analyses (bilans à colonnes fixes), avec le double en mémoire du port. */
class AnalysesTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 10);

    private final AnalysesEnMemoire analyses = new AnalysesEnMemoire();
    private final PatientsDuCentre patients = (centre, patient) -> patient.valeur().equals(PATIENT);
    private final AtomicInteger identifiants = new AtomicInteger();
    private final HorlogeDuCentre horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);
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
    private final EnregistrerUneAnalyse enregistrer = new EnregistrerUneAnalyse(session, patients, analyses, unite,
            () -> new UUID(0, identifiants.incrementAndGet()), horloge);
    private final ModifierUneAnalyse modifier = new ModifierUneAnalyse(session, analyses, unite, horloge);
    private final SupprimerUneAnalyse supprimer = new SupprimerUneAnalyse(session, analyses, unite);
    private final ConsulterLesAnalysesDuPatient consulter = new ConsulterLesAnalysesDuPatient(session, analyses);

    private static MesuresDuBilan hemoglobine(String valeur) {
        return new MesuresDuBilan(new BigDecimal(valeur), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void une_analyse_sans_date_prend_le_jour_du_centre_et_aucune_valeur_n_est_controlee() {
        var a = enregistrer.executer(null, PATIENT, null, hemoglobine("-5"));
        assertThat(a.datePrelevement()).isEqualTo(JOUR);
        enregistrer.executer(null, PATIENT, LocalDate.of(2200, 1, 1), hemoglobine("999"));
        enregistrer.executer(null, PATIENT, JOUR, hemoglobine("0"));
        assertThat(consulter.liste(null, PATIENT, null, null, 0, 20).total()).isEqualTo(3);
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_refuse_sans_rien_ecrire() {
        assertThatThrownBy(() -> enregistrer.executer(null, INCONNU, JOUR, hemoglobine("1"))).isInstanceOf(PatientIntrouvable.class);
        assertThat(consulter.liste(null, INCONNU, null, null, 0, 20).total()).isZero();
    }

    @Test
    void une_mesure_qui_ne_tient_pas_dans_sa_colonne_est_refusee_avant_toute_ecriture() {
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, JOUR, hemoglobine("1" + "0".repeat(37)))).isInstanceOf(SaisieInvalide.class);
        assertThat(consulter.liste(null, PATIENT, null, null, 0, 20).total()).isZero();
    }

    @Test
    void la_modification_remplace_le_bilan_en_entier_et_ne_cree_ni_ne_deplace_rien() {
        var a = enregistrer.executer(null, PATIENT, JOUR.minusDays(3), new MesuresDuBilan(new BigDecimal("11"), new BigDecimal("34"), 220, null, null,
                null, null, null, null, null, null, null, null, null, null, null));
        var modifiee = modifier.executer(null, PATIENT, a.id(), null, hemoglobine("9"));
        assertThat(modifiee.datePrelevement()).isEqualTo(JOUR);
        assertThat(modifiee.mesures().hematocrite()).isNull();
        assertThat(modifiee.mesures().plaquettes()).isNull();
        assertThat(modifiee.id()).isEqualTo(a.id());
        assertThatThrownBy(() -> modifier.executer(null, PATIENT, UUID.randomUUID(), JOUR, hemoglobine("9"))).isInstanceOf(AnalyseIntrouvable.class);
        assertThatThrownBy(() -> modifier.executer(null, INCONNU, a.id(), JOUR, hemoglobine("9"))).isInstanceOf(AnalyseIntrouvable.class);
        assertThat(consulter.liste(null, PATIENT, null, null, 0, 20).total()).isEqualTo(1);
    }

    @Test
    void la_suppression_dit_si_elle_a_supprime_quelque_chose() {
        var a = enregistrer.executer(null, PATIENT, JOUR, hemoglobine("1"));
        assertThat(supprimer.executer(null, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(supprimer.executer(null, INCONNU, a.id())).isFalse();
        assertThat(supprimer.executer(null, PATIENT, a.id())).isTrue();
        assertThat(supprimer.executer(null, PATIENT, a.id())).isFalse();
    }

    @Test
    void les_bornes_de_pagination_de_la_liste_sont_reproduites_du_legacy() {
        assertThat(consulter.liste(null, PATIENT, null, null, -1, 0).page()).isZero();
        assertThat(consulter.liste(null, PATIENT, null, null, -1, 0).taille()).isEqualTo(20);
        assertThat(consulter.liste(null, PATIENT, null, null, 0, 1000).taille()).isEqualTo(200);
        assertThatThrownBy(() -> consulter.liste(null, PATIENT, JOUR, JOUR.minusDays(1), 0, 20)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_recherche_refuse_une_page_ou_une_taille_hors_borne_avec_le_message_de_validation_du_legacy() {
        enregistrer.executer(null, PATIENT, JOUR, hemoglobine("1"));
        assertThat(consulter.recherche(CENTRE.valeur(), PATIENT, 0, 200, null, null).total()).isEqualTo(1);
        for (int[] pageEtTaille : new int[][]{{0, 201}, {0, 0}, {-1, 10}}) {
            assertThatThrownBy(() -> consulter.recherche(CENTRE.valeur(), PATIENT, pageEtTaille[0], pageEtTaille[1], null, null))
                    .isInstanceOfSatisfying(ValeurHorsBorne.class, e -> assertThat(e.getMessage()).isEqualTo("Invalid request content."));
        }
        assertThatThrownBy(() -> consulter.recherche(CENTRE.valeur(), null, 0, 10, null, null)).isInstanceOf(ValeurHorsBorne.class);
        assertThatThrownBy(() -> consulter.recherche(null, PATIENT, 0, 10, null, null)).isInstanceOf(ValeurHorsBorne.class);
        assertThatThrownBy(() -> consulter.recherche(CENTRE.valeur(), PATIENT, null, null, null, null)).isInstanceOf(SaisieInvalide.class)
                .isNotInstanceOf(ValeurHorsBorne.class);
    }
}
