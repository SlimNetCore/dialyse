package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.AntecedentsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

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

/** Cas d'usage des antécédents, avec les doubles en mémoire des ports. */
class AntecedentsTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 10);

    private final AntecedentsEnMemoire antecedents = new AntecedentsEnMemoire();
    private final Set<UUID> patientsConnus = new HashSet<>(Set.of(PATIENT));
    private final PatientsDuCentre patients = (centre, patient) -> patientsConnus.contains(patient.valeur());
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
    private final EnregistrerUnAntecedent enregistrer = new EnregistrerUnAntecedent(session, patients, antecedents, unite,
            () -> new UUID(0, identifiants.incrementAndGet()), horloge);
    private final CorrigerUnAntecedent corriger = new CorrigerUnAntecedent(session, antecedents, unite, horloge);
    private final ResoudreUnAntecedent resoudre = new ResoudreUnAntecedent(session, antecedents, unite, horloge);
    private final SupprimerUnAntecedent supprimer = new SupprimerUnAntecedent(session, antecedents, unite);
    private final ConsulterLesAntecedentsDuPatient consulter = new ConsulterLesAntecedentsDuPatient(session, antecedents);

    private Antecedent coder(String code, LocalDate debut) {
        return enregistrer.executer(null, PATIENT, "MEDICAL", "CIM10", code, null, null, debut, null, null, null);
    }

    private Antecedent libre(String libelle) {
        return enregistrer.executer(null, PATIENT, "MEDICAL", null, null, null, libelle, JOUR.minusDays(5), null, null, null);
    }

    @Test
    void un_antecedent_enregistre_est_actif_et_relu_pour_le_patient_du_centre() {
        var a = coder("n18", JOUR.minusDays(30));
        assertThat(a.statut()).isEqualTo(StatutClinique.ACTIF);
        assertThat(a.diagnostic().code()).isEqualTo("N18");
        assertThat(consulter.liste(null, PATIENT, 0, 20).items()).extracting(Antecedent::id).containsExactly(a.id());
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_refuse_sans_rien_ecrire() {
        assertThatThrownBy(() -> enregistrer.executer(null, INCONNU, "MEDICAL", null, null, null, "Libre", JOUR, null, null, null))
                .isInstanceOf(PatientIntrouvable.class);
        assertThat(consulter.liste(null, INCONNU, 0, 20).total()).isZero();
    }

    @Test
    void le_type_le_systeme_et_le_code_sont_controles_avant_toute_ecriture() {
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, null, null, null, null, "Libre", JOUR, null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, "MEDICAL", null, "B20", null, null, JOUR, null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, "MEDICAL", "CIM10", "ZZZ", null, null, JOUR, null, null, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("CONCEPT_CODE_INVALID_CIM10"));
        assertThat(consulter.liste(null, PATIENT, 0, 20).total()).isZero();
    }

    @Test
    void un_diagnostic_actif_ne_s_enregistre_pas_deux_fois_mais_un_libelle_libre_oui() {
        coder("N18", JOUR.minusDays(30));
        assertThatThrownBy(() -> coder("n18", JOUR.minusDays(20))).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("BUSINESS_RULE_VIOLATION"));
        libre("Même libellé");
        libre("Même libellé");
        assertThat(consulter.liste(null, PATIENT, 0, 20).total()).isEqualTo(3);
    }

    @Test
    void apres_resolution_le_meme_diagnostic_peut_etre_enregistre_a_nouveau() {
        var premier = coder("N18", JOUR.minusDays(30));
        resoudre.executer(null, PATIENT, premier.id(), JOUR.minusDays(10));
        coder("N18", JOUR.minusDays(5));
        assertThat(consulter.liste(null, PATIENT, 0, 20).total()).isEqualTo(2);
    }

    @Test
    void la_resolution_fixe_la_fin_et_se_refuse_au_rejeu() {
        var a = libre("A resoudre");
        var resolu = resoudre.executer(null, PATIENT, a.id(), JOUR);
        assertThat(resolu.statut()).isEqualTo(StatutClinique.RESOLU);
        assertThat(resolu.fin()).isEqualTo(JOUR);
        assertThatThrownBy(() -> resoudre.executer(null, PATIENT, a.id(), JOUR)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("ANTECEDENT_DEJA_RESOLU"));
    }

    @Test
    void un_antecedent_inconnu_ou_d_un_autre_patient_est_introuvable_a_la_correction_et_a_la_resolution() {
        var a = libre("Existant");
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, UUID.randomUUID(), null, null, null, "X", JOUR, null, null, null, null))
                .isInstanceOf(AntecedentIntrouvable.class);
        assertThatThrownBy(() -> corriger.executer(null, INCONNU, a.id(), null, null, null, "X", JOUR, null, null, null, null))
                .isInstanceOf(AntecedentIntrouvable.class);
        assertThatThrownBy(() -> resoudre.executer(null, INCONNU, a.id(), JOUR)).isInstanceOf(AntecedentIntrouvable.class);
    }

    @Test
    void la_correction_remplace_les_champs_et_refuse_le_diagnostic_actif_d_un_autre_antecedent() {
        var n18 = coder("N18", JOUR.minusDays(30));
        var autre = coder("N19", JOUR.minusDays(20));
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, autre.id(), "CIM10", "N18", null, null, JOUR.minusDays(20), null, null, null,
                null)).isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("BUSINESS_RULE_VIOLATION"));
        var corrige = corriger.executer(null, PATIENT, n18.id(), "CIM10", "N18", null, "Nouveau libellé", JOUR.minusDays(31), null, null, "FORTE",
                "note");
        assertThat(corrige.libelleLibre()).isEqualTo("Nouveau libellé");
        assertThat(corrige.severite()).isEqualTo("FORTE");
        assertThat(corrige.debut()).isEqualTo(JOUR.minusDays(31));
        assertThat(corrige.statut()).isEqualTo(StatutClinique.ACTIF);
    }

    @Test
    void un_statut_inconnu_a_la_correction_est_une_saisie_invalide() {
        var a = libre("Statut");
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, a.id(), null, null, null, "Statut", JOUR, null, "GUERI", null, null))
                .isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_suppression_dit_si_elle_a_supprime_quelque_chose() {
        var a = libre("A supprimer");
        assertThat(supprimer.executer(null, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(supprimer.executer(null, INCONNU, a.id())).isFalse();
        assertThat(supprimer.executer(null, PATIENT, a.id())).isTrue();
        assertThat(supprimer.executer(null, PATIENT, a.id())).isFalse();
        assertThat(consulter.liste(null, PATIENT, 0, 20).total()).isZero();
    }

    @Test
    void les_bornes_de_pagination_sont_reproduites_du_legacy() {
        for (int i = 1; i <= 3; i++) libre("Antécédent " + i);
        assertThat(consulter.liste(null, PATIENT, -1, 0).page()).isZero();
        assertThat(consulter.liste(null, PATIENT, -1, 0).taille()).isEqualTo(20);
        assertThat(consulter.liste(null, PATIENT, 0, -3).taille()).isEqualTo(20);
        assertThat(consulter.liste(null, PATIENT, 0, 1000).taille()).isEqualTo(200);
        assertThat(consulter.liste(null, PATIENT, 9, 2).items()).isEmpty();
    }
}
