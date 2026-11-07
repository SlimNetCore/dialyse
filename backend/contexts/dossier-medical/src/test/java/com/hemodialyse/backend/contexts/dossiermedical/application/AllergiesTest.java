package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.AllergiesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CriticiteDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDeVerification;
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

/** Cas d'usage des allergies, avec les doubles en mémoire des ports. */
class AllergiesTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 10);

    private final AllergiesEnMemoire allergies = new AllergiesEnMemoire();
    private final Set<UUID> patientsConnus = Set.of(PATIENT);
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
    private final DeclarerUneAllergie declarer = new DeclarerUneAllergie(session, patients, allergies, unite,
            () -> new UUID(0, identifiants.incrementAndGet()), horloge);
    private final CorrigerUneAllergie corriger = new CorrigerUneAllergie(session, allergies, unite, horloge);
    private final SupprimerUneAllergie supprimer = new SupprimerUneAllergie(session, allergies, unite);
    private final ConsulterLesAllergiesDuPatient consulter = new ConsulterLesAllergiesDuPatient(session, allergies);

    private Allergie declarerUne(String code, String criticite, String manifestations, String statut) {
        return declarer.executer(null, PATIENT, "LOCAL", code, null, "ALIMENT", criticite, "ALLERGIE", manifestations, null, statut);
    }

    @Test
    void une_allergie_declaree_prend_le_jour_du_centre_et_le_statut_suspecte_par_defaut() {
        var a = declarerUne("arachide", "BASSE", null, null);
        assertThat(a.dateDeConstatation()).isEqualTo(JOUR);
        assertThat(a.statut()).isEqualTo(StatutDeVerification.SUSPECTEE);
        assertThat(a.substance().code()).isEqualTo("ARACHIDE");
        assertThat(consulter.liste(null, PATIENT, 0, 20).items()).extracting(Allergie::id).containsExactly(a.id());
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_refuse_sans_rien_ecrire() {
        assertThatThrownBy(() -> declarer.executer(null, INCONNU, "LOCAL", "x", null, "ALIMENT", "BASSE", "ALLERGIE", null, null, null))
                .isInstanceOf(PatientIntrouvable.class);
        assertThat(consulter.liste(null, INCONNU, 0, 20).total()).isZero();
    }

    @Test
    void les_listes_fermees_et_le_systeme_sont_controles_avant_toute_ecriture() {
        assertThatThrownBy(() -> declarer.executer(null, PATIENT, null, "x", null, "ALIMENT", "BASSE", "ALLERGIE", null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> declarer.executer(null, PATIENT, "LOCAL", "x", null, "AUTRE", "BASSE", "ALLERGIE", null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> declarer.executer(null, PATIENT, "LOCAL", "x", null, "ALIMENT", null, "ALLERGIE", null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> declarer.executer(null, PATIENT, "LOCAL", "x", null, "ALIMENT", "BASSE", null, null, null, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> declarer.executer(null, PATIENT, "LOCAL", "x", null, "ALIMENT", "BASSE", "ALLERGIE", null, null, "PROBABLE"))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> declarer.executer(null, PATIENT, "LOCAL", " ", null, "ALIMENT", "BASSE", "ALLERGIE", null, null, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("CONCEPT_CODE_REQUIRED"));
        assertThat(consulter.liste(null, PATIENT, 0, 20).total()).isZero();
    }

    @Test
    void une_criticite_haute_sans_manifestations_et_une_date_future_sont_refusees() {
        assertThatThrownBy(() -> declarerUne("x", "HAUTE", " ", null)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("ALLERGIE_MANIFESTATIONS_REQUISES"));
        assertThatThrownBy(() -> declarer.executer(null, PATIENT, "LOCAL", "x", null, "ALIMENT", "BASSE", "ALLERGIE", null, JOUR.plusDays(1), null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("ALLERGIE_DATE_FUTURE"));
    }

    @Test
    void la_meme_substance_peut_etre_declaree_plusieurs_fois() {
        declarerUne("latex", "BASSE", null, null);
        declarerUne("latex", "BASSE", null, null);
        assertThat(consulter.liste(null, PATIENT, 0, 20).total()).isEqualTo(2);
    }

    @Test
    void le_bandeau_des_critiques_garde_une_allergie_refutee() {
        var haute = declarerUne("a", "HAUTE", "Choc", null);
        var refutee = declarerUne("b", "HAUTE", "Choc", "REFUTEE");
        declarerUne("c", "BASSE", null, null);
        assertThat(consulter.critiques(null, PATIENT)).extracting(Allergie::id).containsExactlyInAnyOrder(haute.id(), refutee.id());
        corriger.executer(null, PATIENT, haute.id(), "BASSE", null, null);
        assertThat(consulter.critiques(null, PATIENT)).extracting(Allergie::id).containsExactly(refutee.id());
    }

    @Test
    void la_correction_remplace_les_manifestations_conserve_le_statut_absent_et_refuse_une_allergie_introuvable() {
        var a = declarerUne("a", "HAUTE", "Choc", "CONFIRMEE");
        var corrigee = corriger.executer(null, PATIENT, a.id(), "BASSE", null, null);
        assertThat(corrigee.manifestations()).isNull();
        assertThat(corrigee.statut()).isEqualTo(StatutDeVerification.CONFIRMEE);
        assertThat(corrigee.criticite()).isEqualTo(CriticiteDAllergie.BASSE);
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, a.id(), "HAUTE", null, null)).isInstanceOf(RegleDeGestionViolee.class);
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, UUID.randomUUID(), "BASSE", null, null)).isInstanceOf(AllergieIntrouvable.class);
        assertThatThrownBy(() -> corriger.executer(null, INCONNU, a.id(), "BASSE", null, null)).isInstanceOf(AllergieIntrouvable.class);
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, a.id(), null, null, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_suppression_dit_si_elle_a_supprime_quelque_chose() {
        var a = declarerUne("a", "BASSE", null, null);
        assertThat(supprimer.executer(null, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(supprimer.executer(null, INCONNU, a.id())).isFalse();
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
