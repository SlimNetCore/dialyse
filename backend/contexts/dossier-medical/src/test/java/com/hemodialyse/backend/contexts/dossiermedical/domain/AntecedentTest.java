package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.dossiermedical.domain.event.AntecedentEvent;
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
 * Tests de caractérisation (pinning) de l'agrégat {@link Antecedent}.
 * Verrouille les invariants métier du slice DM5 (antécédents).
 */
class AntecedentTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997400-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997400-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT = UUID.fromString("99997400-0000-0000-0000-000000000101");
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-03-10T08:00:00Z");
    private static final LocalDate DATE_DEBUT = LocalDate.of(2020, 1, 1);
    private static final LocalDate DATE_FUTUR = LocalDate.of(2030, 1, 1);

    private static ConceptCode cim10() {
        return new ConceptCode("CIM10", "N18.5", "IRC stade 5");
    }

    private static Antecedent creerValide() {
        return Antecedent.creer(new AntecedentId(UUID.fromString("99997400-0000-0000-0000-000000000201")),
                PATIENT, CENTRE_A, TypeAntecedent.MEDICAL, cim10(), null,
                DATE_DEBUT, null, null, null, MAINTENANT);
    }

    @Test
    void creer_avec_cim10_initialise_actif_emette_evenement_ajoute() {
        Antecedent a = creerValide();

        assertThat(a.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(a.getCentreId()).isEqualTo(CENTRE_A);
        assertThat(a.getPatientId()).isEqualTo(PATIENT);
        assertThat(a.getDiagnostic()).isEqualTo(cim10());
        assertThat(a.getPeriode().debut()).isEqualTo(DATE_DEBUT);
        assertThat(a.getPeriode().fin()).isNull();
        assertThat(a.getCreatedAt()).isEqualTo(MAINTENANT);

        List<AntecedentEvent> events = a.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AntecedentEvent.AntecedentAjoute.class);
        // Les événements sont vidés après lecture
        assertThat(a.pullEvents()).isEmpty();
    }

    @Test
    void creer_avec_libelle_libre_sans_diagnostic_est_valide() {
        Antecedent a = Antecedent.creer(new AntecedentId(UUID.randomUUID()), PATIENT, CENTRE_A,
                TypeAntecedent.CHIRURGICAL, null, "Appendicectomie 2018", DATE_DEBUT, null, null, null, MAINTENANT);

        assertThat(a.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(a.getLibelleLibre()).isEqualTo("Appendicectomie 2018");
    }

    @Test
    void rejeter_patient_null() {
        assertThatThrownBy(() -> Antecedent.creer(new AntecedentId(UUID.randomUUID()), null, CENTRE_A,
                TypeAntecedent.MEDICAL, cim10(), null, DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_centre_null() {
        assertThatThrownBy(() -> Antecedent.creer(new AntecedentId(UUID.randomUUID()), PATIENT, null,
                TypeAntecedent.MEDICAL, cim10(), null, DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_type_null() {
        assertThatThrownBy(() -> Antecedent.creer(new AntecedentId(UUID.randomUUID()), PATIENT, CENTRE_A,
                null, cim10(), null, DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_sans_diagnostic_ni_libelle() {
        assertThatThrownBy(() -> Antecedent.creer(new AntecedentId(UUID.randomUUID()), PATIENT, CENTRE_A,
                TypeAntecedent.MEDICAL, null, null, DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_libelle_libre_blanc_sans_diagnostic() {
        assertThatThrownBy(() -> Antecedent.creer(new AntecedentId(UUID.randomUUID()), PATIENT, CENTRE_A,
                TypeAntecedent.MEDICAL, null, "   ", DATE_DEBUT, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeter_date_debut_dans_le_futur() {
        assertThatThrownBy(() -> Antecedent.creer(new AntecedentId(UUID.randomUUID()), PATIENT, CENTRE_A,
                TypeAntecedent.MEDICAL, cim10(), null, DATE_FUTUR, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resoudre_marque_resolu_et_cloture_periode() {
        Antecedent a = creerValide();
        a.pullEvents(); // vide l'événement de création
        LocalDate dateResolution = LocalDate.of(2023, 5, 1);

        a.resoudre(dateResolution, MAINTENANT);

        assertThat(a.getStatutClinique()).isEqualTo(StatutClinique.RESOLU);
        assertThat(a.getPeriode().fin()).isEqualTo(dateResolution);
        assertThat(a.getUpdatedAt()).isEqualTo(MAINTENANT);
        assertThat(a.pullEvents())
                .extracting(e -> e.getClass().getSimpleName())
                .containsExactly("AntecedentResolu");
    }

    @Test
    void resoudre_deja_resolu_echoue() {
        Antecedent a = creerValide();
        a.resoudre(LocalDate.of(2023, 5, 1), MAINTENANT);

        assertThatThrownBy(() -> a.resoudre(LocalDate.of(2024, 1, 1), MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .message().contains("ANTECEDENT_DEJA_RESOLU");
    }

    @Test
    void modifier_met_a_jour_champs() {
        Antecedent a = creerValide();
        ConceptCode nouveau = new ConceptCode("CIM10", "E11.9", "Diabète type 2");

        a.modifier(nouveau, "Diabète équilibré", LocalDate.of(2019, 1, 1), null,
                StatutClinique.ACTIF, "modérée", "suivi", MAINTENANT);

        assertThat(a.getDiagnostic()).isEqualTo(nouveau);
        assertThat(a.getLibelleLibre()).isEqualTo("Diabète équilibré");
        assertThat(a.getPeriode().debut()).isEqualTo(LocalDate.of(2019, 1, 1));
        assertThat(a.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(a.getSeverite()).isEqualTo("modérée");
        assertThat(a.getNote()).isEqualTo("suivi");
        assertThat(a.getUpdatedAt()).isEqualTo(MAINTENANT);
    }

    @Test
    void modifier_sans_diagnostic_ni_libelle_est_rejete() {
        Antecedent a = creerValide();

        assertThatThrownBy(() -> a.modifier(null, null, DATE_DEBUT, null, null, null, null, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstituer_preserve_etat_sans_revalidation() {
        Antecedent originale = creerValide();
        Antecedent reconstituee = Antecedent.reconstituer(
                originale.getId(), originale.getPatientId(), originale.getCentreId(),
                originale.getType(), originale.getDiagnostic(), originale.getLibelleLibre(),
                originale.getPeriode().debut(), originale.getPeriode().fin(),
                originale.getStatutClinique(), originale.getSeverite(), originale.getNote(),
                originale.getCreatedAt(), originale.getUpdatedAt());

        assertThat(reconstituee.getId()).isEqualTo(originale.getId());
        assertThat(reconstituee.getStatutClinique()).isEqualTo(StatutClinique.ACTIF);
        assertThat(reconstituee.getDiagnostic()).isEqualTo(cim10());
    }

    @Test
    void centres_concernees_retourne_le_centre_propre() {
        Antecedent a = creerValide();

        List<CentreId> centres = a.centresConcernees();
        assertThat(centres).containsExactly(CENTRE_A);
        assertThat(centres).doesNotContain(CENTRE_B);
    }
}
