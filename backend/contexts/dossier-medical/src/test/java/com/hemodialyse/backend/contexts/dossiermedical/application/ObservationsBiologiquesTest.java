package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.ObservationsBiologiquesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SourceDObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDObservation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des observations biologiques, avec les doubles en mémoire des ports. */
class ObservationsBiologiquesTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID DEMANDE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 10);

    private final ObservationsBiologiquesEnMemoire observations = new ObservationsBiologiquesEnMemoire();
    private final Set<UUID> demandesDuPatient = new HashSet<>(Set.of(DEMANDE));
    private final DemandesDuPatient demandes = (centre, patient, demande) -> patient.valeur().equals(PATIENT) && demandesDuPatient.contains(demande);
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
    private final EnregistrerUneObservationBiologique enregistrer = new EnregistrerUneObservationBiologique(session, patients, observations, unite,
            () -> new UUID(0, identifiants.incrementAndGet()), horloge);
    private final CorrigerUneObservationBiologique corriger = new CorrigerUneObservationBiologique(session, observations, unite, horloge);
    private final SupprimerUneObservationBiologique supprimer = new SupprimerUneObservationBiologique(session, observations, unite);
    private final ConsulterLesObservationsBiologiquesDuPatient consulter = new ConsulterLesObservationsBiologiquesDuPatient(session, observations,
            demandes);

    private ObservationBiologique numerique(UUID patient, UUID demande, String valeur, LocalDate date) {
        return enregistrer.executer(null, patient, demande, "LOINC", "718-7", "Hb", new BigDecimal(valeur), "g/dL", null, date, null);
    }

    @Test
    void une_observation_enregistree_est_finale_et_saisie_directement_et_relue_dans_la_liste() {
        var o = numerique(PATIENT, null, "12.5", JOUR);
        assertThat(o.statut()).isEqualTo(StatutDObservation.FINAL);
        assertThat(o.source()).isEqualTo(SourceDObservation.SAISIE_DIRECTE);
        assertThat(consulter.liste(null, PATIENT, null, null, null, 0, 20).items()).extracting(ObservationBiologique::id).containsExactly(o.id());
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_refuse_sans_rien_ecrire() {
        assertThatThrownBy(() -> numerique(INCONNU, null, "1", JOUR)).isInstanceOf(PatientIntrouvable.class);
        assertThat(consulter.liste(null, INCONNU, null, null, null, 0, 20).total()).isZero();
    }

    @Test
    void le_systeme_le_code_l_unite_et_le_statut_sont_controles_avant_toute_ecriture() {
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, null, null, "718-7", null, BigDecimal.ONE, "u", null, JOUR, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, null, "SNOMED", "718-7", null, BigDecimal.ONE, "u", null, JOUR, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, null, "LOINC", " ", null, BigDecimal.ONE, "u", null, JOUR, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("CONCEPT_CODE_REQUIRED"));
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, null, "LOINC", "718-7", null, BigDecimal.ONE, " ", null, JOUR, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("VALEUR_MESUREE_UNITE_REQUISE"));
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, null, "LOINC", "718-7", null, BigDecimal.ONE, "u", null, JOUR, "ANNULE"))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(consulter.liste(null, PATIENT, null, null, null, 0, 20).total()).isZero();
    }

    @Test
    void les_regles_de_l_observation_sont_appliquees_a_l_enregistrement() {
        assertThatThrownBy(() -> numerique(PATIENT, null, "1", JOUR.plusDays(1))).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("OBSERVATION_DATE_FUTURE"));
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, null, "LOINC", "718-7", null, null, null, null, JOUR, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("OBSERVATION_VALEUR_INVALIDE"));
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, null, "CIM10", "A00", null, BigDecimal.ONE, "u", null, JOUR, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("OBSERVATION_ANALYTE_NON_LOINC"));
    }

    @Test
    void aucune_regle_de_valeur_deux_envois_identiques_et_demande_non_controlee_a_l_ecriture_sont_reproduits() {
        numerique(PATIENT, null, "-5", JOUR);
        numerique(PATIENT, null, "-5", JOUR);
        numerique(PATIENT, UUID.randomUUID(), "99999", JOUR);
        assertThat(consulter.liste(null, PATIENT, null, null, null, 0, 20).total()).isEqualTo(3);
    }

    @Test
    void la_correction_remplace_la_valeur_et_passe_toujours_le_statut_a_corrige_et_refuse_une_observation_introuvable() {
        var o = numerique(PATIENT, null, "12.5", JOUR);
        var corrigee = corriger.executer(null, PATIENT, o.id(), null, null, "Indosable");
        assertThat(corrigee.statut()).isEqualTo(StatutDObservation.CORRIGE);
        assertThat(corrigee.valeurNumerique()).isNull();
        assertThat(corrigee.valeurTexte()).isEqualTo("Indosable");
        assertThat(corriger.executer(null, PATIENT, o.id(), null, null, "Indosable").statut()).isEqualTo(StatutDObservation.CORRIGE);
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, UUID.randomUUID(), null, null, "x")).isInstanceOf(ObservationIntrouvable.class);
        assertThatThrownBy(() -> corriger.executer(null, INCONNU, o.id(), null, null, "x")).isInstanceOf(ObservationIntrouvable.class);
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, o.id(), BigDecimal.ONE, "u", "x")).isInstanceOf(RegleDeGestionViolee.class);
    }

    @Test
    void une_observation_derivee_d_un_bilan_est_immuable_mais_supprimable() {
        var derivee = ObservationBiologique.reconstituer(new UUID(1, 1), new com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId(PATIENT),
                CENTRE, null, com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique.saisir(
                        com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage.LOINC, "718-7", null),
                new com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurMesuree(BigDecimal.ONE, "u"), null, JOUR, StatutDObservation.FINAL,
                SourceDObservation.DERIVEE_BILAN, Instant.parse("2026-01-10T10:00:00Z"), Instant.parse("2026-01-10T10:00:00Z"));
        observations.ajouter(derivee);
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, derivee.id(), BigDecimal.TEN, "u", null)).isInstanceOfSatisfying(
                RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("OBSERVATION_DERIVEE_IMMUABLE"));
        assertThat(supprimer.executer(null, PATIENT, derivee.id())).isTrue();
    }

    @Test
    void la_suppression_dit_si_elle_a_supprime_quelque_chose() {
        var o = numerique(PATIENT, null, "1", JOUR);
        assertThat(supprimer.executer(null, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(supprimer.executer(null, INCONNU, o.id())).isFalse();
        assertThat(supprimer.executer(null, PATIENT, o.id())).isTrue();
        assertThat(supprimer.executer(null, PATIENT, o.id())).isFalse();
    }

    @Test
    void les_bornes_de_pagination_sont_reproduites_du_legacy_et_le_patient_inconnu_donne_une_liste_vide() {
        assertThat(consulter.liste(null, PATIENT, null, null, null, -1, 0).page()).isZero();
        assertThat(consulter.liste(null, PATIENT, null, null, null, -1, 0).taille()).isEqualTo(20);
        assertThat(consulter.liste(null, PATIENT, null, null, null, 0, -5).taille()).isEqualTo(20);
        assertThat(consulter.liste(null, PATIENT, null, null, null, 0, 1000).taille()).isEqualTo(200);
        assertThat(consulter.liste(null, INCONNU, null, null, null, 0, 20).total()).isZero();
    }

    @Test
    void la_lecture_par_demande_exige_que_la_demande_appartienne_au_patient_de_l_url() {
        numerique(PATIENT, DEMANDE, "1", JOUR);
        assertThat(consulter.deLaDemande(null, PATIENT, DEMANDE)).hasSize(1);
        assertThat(consulter.deLaDemande(null, INCONNU, DEMANDE)).isEmpty();
        assertThat(consulter.deLaDemande(null, PATIENT, UUID.randomUUID())).isEmpty();
    }
}
