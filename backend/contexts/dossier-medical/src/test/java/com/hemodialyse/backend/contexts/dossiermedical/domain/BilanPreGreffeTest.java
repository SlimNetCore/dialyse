package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BilanPreGreffeTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final UUID PATIENT = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(
            java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DATE_DEMARRAGE = LocalDate.of(2026, 3, 10);

    private List<EtapeBilan> deuxEtapes() {
        return List.of(EtapeBilan.aFaire("CARDIO", "Bilan cardiaque"),
                EtapeBilan.aFaire("NEPHRO", "Avis néphrologique"));
    }

    private BilanPreGreffe bilanValide() {
        return BilanPreGreffe.demarrer(new BilanPreGreffeId(UUID.randomUUID()), PATIENT, CENTRE,
                deuxEtapes(), DATE_DEMARRAGE, null, MAINTENANT);
    }

    @Test
    void demarre_bilan_en_cours() {
        BilanPreGreffe b = bilanValide();
        assertEquals(StatutBilan.EN_COURS, b.statut());
        assertEquals(2, b.etapes().size());
    }

    @Test
    void rejeter_demarrage_sans_etape() {
        assertThrows(IllegalArgumentException.class, () ->
                BilanPreGreffe.demarrer(new BilanPreGreffeId(UUID.randomUUID()), PATIENT, CENTRE,
                        List.of(), DATE_DEMARRAGE, null, MAINTENANT));
    }

    @Test
    void rejeter_centre_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                BilanPreGreffe.demarrer(new BilanPreGreffeId(UUID.randomUUID()), PATIENT, null,
                        deuxEtapes(), DATE_DEMARRAGE, null, MAINTENANT));
    }

    @Test
    void valider_etape() {
        BilanPreGreffe b = bilanValide().validerEtape("CARDIO", "MED-001", MAINTENANT);
        assertTrue(b.etapes().stream().filter(e -> e.code().equals("CARDIO")).findFirst().orElseThrow().validee());
    }

    @Test
    void rejeter_double_validation_etape() {
        BilanPreGreffe b = bilanValide().validerEtape("CARDIO", "MED-001", MAINTENANT);
        assertThrows(IllegalArgumentException.class, () ->
                b.validerEtape("CARDIO", "MED-001", MAINTENANT));
    }

    @Test
    void rejeter_etape_inconnue() {
        assertThrows(IllegalArgumentException.class, () ->
                bilanValide().validerEtape("INCONNUE", "MED-001", MAINTENANT));
    }

    @Test
    void completer_exige_toutes_etapes_validees() {
        BilanPreGreffe partiel = bilanValide().validerEtape("CARDIO", "MED-001", MAINTENANT);
        assertThrows(IllegalArgumentException.class, () -> partiel.completer(MAINTENANT));
    }

    @Test
    void completer_bilan_complet() {
        BilanPreGreffe b = bilanValide()
                .validerEtape("CARDIO", "MED-001", MAINTENANT)
                .validerEtape("NEPHRO", "MED-002", MAINTENANT)
                .completer(MAINTENANT);
        assertTrue(b.estComplet());
    }

    @Test
    void rejeter_modification_apres_completion() {
        BilanPreGreffe complet = bilanValide()
                .validerEtape("CARDIO", "MED-001", MAINTENANT)
                .validerEtape("NEPHRO", "MED-002", MAINTENANT)
                .completer(MAINTENANT);
        assertThrows(IllegalArgumentException.class, () ->
                complet.validerEtape("CARDIO", "MED-001", MAINTENANT));
    }

    @Test
    void annuler_bilan_en_cours() {
        BilanPreGreffe b = bilanValide().annuler(MAINTENANT);
        assertEquals(StatutBilan.ANNULE, b.statut());
    }
}
