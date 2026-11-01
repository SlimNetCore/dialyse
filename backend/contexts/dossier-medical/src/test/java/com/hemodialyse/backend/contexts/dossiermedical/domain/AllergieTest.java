package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.dossiermedical.domain.event.AllergieEvent;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de caractérisation (pinning) de l'agrégat {@link Allergie}.
 * Verrouille les invariants métier du slice DM5 (allergies).
 */
class AllergieTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT = UUID.fromString("99997500-0000-0000-0000-000000000101");
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-03-10T08:00:00Z");
    private static final LocalDate DATE_DEBUT = LocalDate.of(2020, 1, 1);
    private static final LocalDate DATE_FUTUR = LocalDate.of(2030, 1, 1);

    private static Allergie declarerValide() {
        return Allergie.declarer(new AllergieId(UUID.fromString("99997500-0000-0000-0000-000000000201")),
                PATIENT, CENTRE_A, "Pénicilline", TypeAllergie.MEDICAMENTOSE,
                "Éruption cutanée", DATE_DEBUT, null, "sévère", "Antécédent connu", MAINTENANT);
    }

    @Test
    void declarer_avec_allergene_initialise_actif_emette_evenement_declaree() {
        Allergie a = declarerValide();

        assertThat(a.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(a.getCentreId()).isEqualTo(CENTRE_A);
        assertThat(a.getPatientId()).isEqualTo(PATIENT);
        assertThat(a.getAllergene()).isEqualTo("Pénicilline");
        assertThat(a.getType()).isEqualTo(TypeAllergie.MEDICAMENTOSE);
        assertThat(a.getReaction()).isEqualTo("Éruption cutanée");
        assertThat(a.getSeverite()).isEqualTo("sévère");
        assertThat(a.getCreatedAt()).isEqualTo(MAINTENANT);

        List<AllergieEvent> events = a.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AllergieEvent.AllergieDeclaree.class);
        assertThat(a.pullEvents()).isEmpty();
    }

    @Test
    void rejeter_patient_null() {
        assertThatThrownBy(() -> Allergie.declarer(new AllergieId(UUID.randomUUID()), null, CENTRE_A,
                "Pénicilline", TypeAllergie.MEDICAMENTOSE, null, DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_centre_null() {
        assertThatThrownBy(() -> Allergie.declarer(new AllergieId(UUID.randomUUID()), PATIENT, null,
                "Pénicilline", TypeAllergie.MEDICAMENTOSE, null, DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_allergene_blank() {
        assertThatThrownBy(() -> Allergie.declarer(new AllergieId(UUID.randomUUID()), PATIENT, CENTRE_A,
                "   ", TypeAllergie.MEDICAMENTOSE, null, DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_type_null() {
        assertThatThrownBy(() -> Allergie.declarer(new AllergieId(UUID.randomUUID()), PATIENT, CENTRE_A,
                "Pénicilline", null, null, DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_date_debut_dans_le_futur() {
        assertThatThrownBy(() -> Allergie.declarer(new AllergieId(UUID.randomUUID()), PATIENT, CENTRE_A,
                "Pénicilline", TypeAllergie.MEDICAMENTOSE, null, DATE_FUTUR, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resoudre_marque_resolue_et_cloture_periode() {
        Allergie a = declarerValide();
        a.pullEvents(); // vide l'événement de déclaration
        LocalDate dateResolution = LocalDate.of(2023, 5, 1);

        a.resoudre(dateResolution, MAINTENANT);

        assertThat(a.getStatutClinique()).isEqualTo(StatutClinique.RESOLU);
        assertThat(a.getPeriode().fin()).isEqualTo(dateResolution);
        assertThat(a.getUpdatedAt()).isEqualTo(MAINTENANT);
        assertThat(a.pullEvents())
                .extracting(e -> e.getClass().getSimpleName())
                .containsExactly("AllergieResolue");
    }

    @Test
    void resoudre_deja_resolue_echoue() {
        Allergie a = declarerValide();
        a.resoudre(LocalDate.of(2023, 5, 1), MAINTENANT);

        assertThatThrownBy(() -> a.resoudre(LocalDate.of(2024, 1, 1), MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .message().contains("ALLERGIE_DEJA_RESOLUE");
    }

    @Test
    void modifier_met_a_jour_champs() {
        Allergie a = declarerValide();

        a.modifier("Œdème de Quincke", LocalDate.of(2019, 1, 1), null,
                StatutClinique.ACTIF, "grave", "à éviter", MAINTENANT);

        assertThat(a.getReaction()).isEqualTo("Œdème de Quincke");
        assertThat(a.getPeriode().debut()).isEqualTo(LocalDate.of(2019, 1, 1));
        assertThat(a.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(a.getSeverite()).isEqualTo("grave");
        assertThat(a.getNote()).isEqualTo("à éviter");
        assertThat(a.getUpdatedAt()).isEqualTo(MAINTENANT);
    }

    @Test
    void reconstituer_preserve_etat_sans_revalidation() {
        Allergie originale = declarerValide();
        Allergie reconstituee = Allergie.reconstituer(
                originale.getId(), originale.getPatientId(), originale.getCentreId(),
                originale.getAllergene(), originale.getType(), originale.getReaction(),
                originale.getPeriode().debut(), originale.getPeriode().fin(),
                originale.getStatutClinique(), originale.getSeverite(), originale.getNote(),
                originale.getCreatedAt(), originale.getUpdatedAt());

        assertThat(reconstituee.getId()).isEqualTo(originale.getId());
        assertThat(reconstituee.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(reconstituee.getAllergene()).isEqualTo("Pénicilline");
    }

    @Test
    void centres_concernees_retourne_le_centre_propre() {
        Allergie a = declarerValide();

        List<CentreId> centres = a.centresConcernees();
        assertThat(centres).containsExactly(CENTRE_A);
        assertThat(centres).doesNotContain(CENTRE_B);
    }
}
