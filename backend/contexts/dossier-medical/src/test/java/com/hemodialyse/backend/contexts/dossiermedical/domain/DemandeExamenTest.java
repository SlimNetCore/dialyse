package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DemandeExamenTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final Instant MAINTENANT_INSTANT = Instant.parse("2026-03-10T08:00:00Z");
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(MAINTENANT_INSTANT, ZoneOffset.UTC);
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 3, 10);

    private DemandeExamen demandeValide() {
        return DemandeExamen.creer(
                new DemandeExamenId(UUID.randomUUID()), PATIENT, CENTRE, "MED-001",
                LocalDate.of(2026, 3, 10), AUJOURDHUI, CategorieExamen.BIOLOGIE, false, "Bilan de routine",
                List.of(LigneDemandeExamen.creer(UUID.randomUUID(), "LOINC", "1234-5", "Sodium", "Sodium s\u00e9rum", null)),
                MAINTENANT);
    }

    @Test
    void reject_null_centre() {
        assertThrows(IllegalArgumentException.class, () ->
                DemandeExamen.creer(new DemandeExamenId(UUID.randomUUID()), PATIENT, null, "MED-001",
                        LocalDate.of(2026, 3, 10), AUJOURDHUI, CategorieExamen.BIOLOGIE, false, "motif",
                        List.of(LigneDemandeExamen.creer(UUID.randomUUID(), "LOINC", "1234-5", "Sodium", "Sodium", null)),
                        MAINTENANT));
    }

    @Test
    void reject_sans_lignes() {
        assertThrows(IllegalArgumentException.class, () ->
                DemandeExamen.creer(new DemandeExamenId(UUID.randomUUID()), PATIENT, CENTRE, "MED-001",
                        LocalDate.of(2026, 3, 10), AUJOURDHUI, CategorieExamen.BIOLOGIE, false, "motif",
                        List.of(), MAINTENANT));
    }

    @Test
    void reject_date_demande_futur() {
        assertThrows(IllegalArgumentException.class, () ->
                DemandeExamen.creer(new DemandeExamenId(UUID.randomUUID()), PATIENT, CENTRE, "MED-001",
                        LocalDate.of(2026, 12, 31), AUJOURDHUI, CategorieExamen.BIOLOGIE, false, "motif",
                        List.of(LigneDemandeExamen.creer(UUID.randomUUID(), "LOINC", "1234-5", "Sodium", "Sodium", null)),
                        MAINTENANT));
    }

    @Test
    void creer_initialise_statut_demande() {
        DemandeExamen d = demandeValide();
        assertEquals(StatutDemandeExamen.DEMANDE, d.statut());
        assertEquals(CENTRE, d.centreId());
        assertEquals(PATIENT, d.patientId());
        assertEquals(CategorieExamen.BIOLOGIE, d.categorie());
        assertEquals(1, d.lignes().size());
    }

    @Test
    void transition_demande_vers_preleve() {
        DemandeExamen d = demandeValide();
        d.preleve(MAINTENANT);
        assertEquals(StatutDemandeExamen.PRELEVE, d.statut());
    }

    @Test
    void transition_preleve_vers_resultat_disponible() {
        DemandeExamen d = demandeValide();
        d.preleve(MAINTENANT);
        d.marquerResultatDisponible(MAINTENANT_INSTANT);
        assertEquals(StatutDemandeExamen.RESULTAT_DISPONIBLE, d.statut());
    }

    @Test
    void transition_resultat_disponible_vers_valider() {
        DemandeExamen d = demandeValide();
        d.preleve(MAINTENANT);
        d.marquerResultatDisponible(MAINTENANT_INSTANT);
        d.valider("R\u00e9sultats dans les normes", MAINTENANT);
        assertEquals(StatutDemandeExamen.VALIDE, d.statut());
        assertEquals("R\u00e9sultats dans les normes", d.conclusion());
    }

    @Test
    void refuser_transition_valide_vers_annule() {
        DemandeExamen d = demandeValide();
        d.preleve(MAINTENANT);
        d.marquerResultatDisponible(MAINTENANT_INSTANT);
        d.valider("OK", MAINTENANT);
        assertThrows(IllegalStateException.class, () -> d.annuler(MAINTENANT));
    }

    @Test
    void refuser_transition_resultat_disponible_vers_annule() {
        DemandeExamen d = demandeValide();
        d.preleve(MAINTENANT);
        d.marquerResultatDisponible(MAINTENANT_INSTANT);
        assertThrows(IllegalStateException.class, () -> d.annuler(MAINTENANT));
    }

    @Test
    void transition_demande_vers_annule_autorisee() {
        DemandeExamen d = demandeValide();
        d.annuler(MAINTENANT);
        assertEquals(StatutDemandeExamen.ANNULE, d.statut());
    }

    @Test
    void transition_preleve_vers_annule_autorisee() {
        DemandeExamen d = demandeValide();
        d.preleve(MAINTENANT);
        d.annuler(MAINTENANT);
        assertEquals(StatutDemandeExamen.ANNULE, d.statut());
    }

    @Test
    void centre_different_rejete() {
        DemandeExamen d = demandeValide();
        assertEquals(CENTRE, d.centreId());
        assertFalse(d.centreId().equals(CENTRE_B));
    }
}
