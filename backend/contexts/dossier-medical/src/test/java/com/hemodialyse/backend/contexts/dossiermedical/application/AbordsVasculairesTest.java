package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.AbordsVasculairesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaireIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des abords vasculaires, avec les doubles en mémoire des ports. */
class AbordsVasculairesTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID AUTRE_PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private final AbordsVasculairesEnMemoire abords = new AbordsVasculairesEnMemoire();
    private final Set<UUID> patientsConnus = Set.of(PATIENT, AUTRE_PATIENT);
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
    private final EnregistrerUnAbordVasculaire enregistrer = new EnregistrerUnAbordVasculaire(session, patients, abords, unite,
            () -> new UUID(0, identifiants.incrementAndGet()), horloge);
    private final ModifierUnAbordVasculaire modifier = new ModifierUnAbordVasculaire(session, abords, unite, horloge);
    private final SupprimerUnAbordVasculaire supprimer = new SupprimerUnAbordVasculaire(session, abords, unite);
    private final ConsulterLesAbordsVasculairesDuPatient consulter = new ConsulterLesAbordsVasculairesDuPatient(session, abords);

    private AbordVasculaire enregistrerUn(String type) {
        return enregistrer.executer(null, PATIENT, type, null, null, LocalDate.of(2026, 1, 1), null, null, null);
    }

    @Test
    void un_abord_enregistre_est_actif_par_defaut_et_relu_pour_le_patient_du_centre() {
        var a = enregistrerUn("FAV");
        assertThat(a.actif()).isTrue();
        assertThat(a.enregistreLe()).isEqualTo(Instant.parse("2026-01-10T10:00:00Z"));
        assertThat(consulter.liste(null, PATIENT, 0, 20).items()).extracting(AbordVasculaire::id).containsExactly(a.id());
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_refuse_sans_rien_ecrire() {
        assertThatThrownBy(() -> enregistrer.executer(null, INCONNU, "FAV", null, null, null, null, null, null)).isInstanceOf(PatientIntrouvable.class);
        assertThat(consulter.liste(null, INCONNU, 0, 20).total()).isZero();
    }

    @Test
    void le_type_est_libre_mais_obligatoire_et_la_fin_ne_precede_pas_la_creation() {
        assertThat(enregistrerUn("kt_aigu").type()).isEqualTo("kt_aigu");
        assertThat(enregistrerUn("").type()).isEmpty();
        assertThatThrownBy(() -> enregistrerUn(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrer.executer(null, PATIENT, "FAV", null, null, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 9), null, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("ABORD_DATE_FIN_ANTERIEURE"));
        assertThat(consulter.liste(null, PATIENT, 0, 20).total()).isEqualTo(2);
    }

    @Test
    void le_remplacement_efface_les_champs_absents_et_reactive_l_abord() {
        var a = enregistrer.executer(null, PATIENT, "FAV", "GAUCHE", "Avant-bras", LocalDate.of(2026, 1, 1), null, false, "x");
        var remplace = modifier.executer(null, PATIENT, a.id(), "PTFE", null, null, LocalDate.of(2026, 3, 1), null, null, null);
        assertThat(remplace.type()).isEqualTo("PTFE");
        assertThat(remplace.cote()).isNull();
        assertThat(remplace.complications()).isNull();
        assertThat(remplace.actif()).isTrue();
        assertThat(remplace.id()).isEqualTo(a.id());
    }

    @Test
    void un_remplacement_ne_cree_jamais_un_abord_inconnu_ni_ne_deplace_celui_d_un_autre_patient() {
        var autre = enregistrer.executer(null, AUTRE_PATIENT, "FAV", "DROIT", null, LocalDate.of(2026, 1, 1), null, null, null);
        assertThatThrownBy(() -> modifier.executer(null, PATIENT, UUID.randomUUID(), "PTFE", null, null, null, null, null, null))
                .isInstanceOf(AbordVasculaireIntrouvable.class);
        assertThatThrownBy(() -> modifier.executer(null, PATIENT, autre.id(), "PTFE", null, null, null, null, null, null))
                .isInstanceOf(AbordVasculaireIntrouvable.class);
        assertThat(consulter.liste(null, PATIENT, 0, 20).total()).isZero();
        var intact = consulter.liste(null, AUTRE_PATIENT, 0, 20).items().get(0);
        assertThat(intact.type()).isEqualTo("FAV");
        assertThat(intact.patient()).isEqualTo(new PatientId(AUTRE_PATIENT));
    }

    @Test
    void un_remplacement_refuse_laisse_l_abord_inchange() {
        var a = enregistrerUn("FAV");
        assertThatThrownBy(() -> modifier.executer(null, PATIENT, a.id(), null, null, null, null, null, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> modifier.executer(null, PATIENT, a.id(), "PTFE", null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1), null,
                null)).isInstanceOf(RegleDeGestionViolee.class);
        assertThat(consulter.liste(null, PATIENT, 0, 20).items().get(0).type()).isEqualTo("FAV");
    }

    @Test
    void la_suppression_dit_si_elle_a_supprime_quelque_chose() {
        var a = enregistrerUn("FAV");
        assertThat(supprimer.executer(null, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(supprimer.executer(null, AUTRE_PATIENT, a.id())).isFalse();
        assertThat(supprimer.executer(null, PATIENT, a.id())).isTrue();
        assertThat(supprimer.executer(null, PATIENT, a.id())).isFalse();
    }

    @Test
    void les_bornes_de_pagination_sont_reproduites_du_legacy() {
        assertThat(consulter.liste(null, PATIENT, -1, 0).page()).isZero();
        assertThat(consulter.liste(null, PATIENT, -1, 0).taille()).isEqualTo(20);
        assertThat(consulter.liste(null, PATIENT, 0, 1000).taille()).isEqualTo(200);
    }
}
