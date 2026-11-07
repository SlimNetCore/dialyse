package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.DossiersMedicauxDeBaseEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage du dossier médical de base, avec le double en mémoire du port. */
class DossierMedicalDeBaseTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c002"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private final DossiersMedicauxDeBaseEnMemoire dossiers = new DossiersMedicauxDeBaseEnMemoire();
    private final PatientsDuCentre patients = (centre, patient) -> patient.valeur().equals(PATIENT) && centre.equals(CENTRE);
    private final AtomicInteger numero = new AtomicInteger();
    private final GenerateurDIdentifiants identifiants = () -> new UUID(0, numero.incrementAndGet());
    private final AtomicInteger secondes = new AtomicInteger();
    private final HorlogeDuCentre horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC) {
        @Override
        public Instant maintenant() {
            return Instant.parse("2026-11-07T10:00:00Z").plusSeconds(secondes.getAndIncrement());
        }
    };
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

    private EnregistrerLeDossierMedicalDeBase enregistrer(CentreId centre) {
        return new EnregistrerLeDossierMedicalDeBase(demande -> centre, patients, dossiers, unite, identifiants, horloge);
    }

    private ConsulterLeDossierMedicalDeBase consulter(CentreId centre) {
        return new ConsulterLeDossierMedicalDeBase(demande -> centre, dossiers);
    }

    @Test
    void should_creer_le_dossier_au_premier_enregistrement_puis_le_remplacer_en_entier_en_conservant_l_identifiant_et_la_creation() {
        var premier = enregistrer(CENTRE).executer(null, PATIENT, "Nephropathie", LocalDate.of(2026, 1, 11), "NEGATIF", "POSITIF", "Obs", "Concl");
        var second = enregistrer(CENTRE).executer(null, PATIENT, "Seul champ", null, null, null, null, null);
        assertThat(second.id()).isEqualTo(premier.id());
        assertThat(second.creeLe()).isEqualTo(premier.creeLe());
        assertThat(second.modifieLe()).isAfter(premier.modifieLe());
        var relu = consulter(CENTRE).executer(null, PATIENT).orElseThrow();
        assertThat(relu.nephropathieInitiale()).isEqualTo("Seul champ");
        assertThat(relu.hepatiteBStatut()).isNull();
        assertThat(relu.conclusionMedicale()).isNull();
    }

    @Test
    void should_refuser_un_patient_inconnu_ou_d_un_autre_centre_sans_rien_ecrire() {
        assertThatThrownBy(() -> enregistrer(CENTRE).executer(null, INCONNU, "x", null, null, null, null, null)).isInstanceOf(PatientIntrouvable.class);
        assertThatThrownBy(() -> enregistrer(AUTRE_CENTRE).executer(null, PATIENT, "x", null, null, null, null, null)).isInstanceOf(PatientIntrouvable.class);
        assertThat(dossiers.duPatient(CENTRE, new PatientId(INCONNU))).isEmpty();
        assertThat(dossiers.duPatient(AUTRE_CENTRE, new PatientId(PATIENT))).isEmpty();
    }

    @Test
    void should_refuser_une_valeur_trop_longue_sans_rien_ecrire() {
        assertThatThrownBy(() -> enregistrer(CENTRE).executer(null, PATIENT, "n".repeat(256), null, null, null, null, null)).isInstanceOf(ValeurHorsBorne.class);
        assertThat(consulter(CENTRE).executer(null, PATIENT)).isEmpty();
    }

    @Test
    void should_signaler_une_ecriture_concurrente_quand_un_dossier_tient_deja_le_patient_dans_un_autre_centre() {
        dossiers.ajouter(com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase.ouvrir(UUID.randomUUID(), new PatientId(PATIENT),
                AUTRE_CENTRE, "Ailleurs", null, null, null, null, null, Instant.parse("2026-01-01T10:00:00Z")));
        assertThatThrownBy(() -> enregistrer(CENTRE).executer(null, PATIENT, "Ici", null, null, null, null, null))
                .isInstanceOf(EcritureConcurrenteSurUnDossierMedical.class);
    }

    @Test
    void should_ne_rien_creer_en_lecture_et_rendre_vide_un_dossier_absent() {
        assertThat(consulter(CENTRE).executer(null, PATIENT)).isEmpty();
        assertThat(dossiers.duPatient(CENTRE, new PatientId(PATIENT))).isEmpty();
    }
}
