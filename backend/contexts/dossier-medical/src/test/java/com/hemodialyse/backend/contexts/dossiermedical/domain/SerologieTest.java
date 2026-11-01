package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerologieTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final Instant MAINTENANT_INSTANT = Instant.parse("2026-03-10T08:00:00Z");
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(MAINTENANT_INSTANT, ZoneOffset.UTC);
    private static final LocalDate DATE_PRELEVEMENT = LocalDate.of(2026, 3, 10);

    private Serologie serologieValide(TypeSerologie type, ResultatSerologie resultat) {
        return Serologie.enregistrer(
                new SerologieId(UUID.randomUUID()), PATIENT, CENTRE, "MED-001",
                DATE_PRELEVEMENT, type, resultat,
                "45.0", "UI/mL", MAINTENANT_INSTANT,
                "Labo Central", "Bilan de routine", MAINTENANT);
    }

    @Test
    void reject_null_centre() {
        assertThrows(IllegalArgumentException.class, () ->
                Serologie.enregistrer(new SerologieId(UUID.randomUUID()), PATIENT, null, "MED-001",
                        DATE_PRELEVEMENT, TypeSerologie.VHB, ResultatSerologie.NEGATIF,
                        null, null, null, null, null, MAINTENANT));
    }

    @Test
    void reject_null_patient() {
        assertThrows(IllegalArgumentException.class, () ->
                Serologie.enregistrer(new SerologieId(UUID.randomUUID()), null, CENTRE, "MED-001",
                        DATE_PRELEVEMENT, TypeSerologie.VHB, ResultatSerologie.NEGATIF,
                        null, null, null, null, null, MAINTENANT));
    }

    @Test
    void reject_null_type_serologie() {
        assertThrows(IllegalArgumentException.class, () ->
                Serologie.enregistrer(new SerologieId(UUID.randomUUID()), PATIENT, CENTRE, "MED-001",
                        DATE_PRELEVEMENT, null, ResultatSerologie.NEGATIF,
                        null, null, null, null, null, MAINTENANT));
    }

    @Test
    void reject_null_resultat() {
        assertThrows(IllegalArgumentException.class, () ->
                Serologie.enregistrer(new SerologieId(UUID.randomUUID()), PATIENT, CENTRE, "MED-001",
                        DATE_PRELEVEMENT, TypeSerologie.VHB, null,
                        null, null, null, null, null, MAINTENANT));
    }

    @Test
    void reject_date_prelevement_future() {
        assertThrows(IllegalArgumentException.class, () ->
                Serologie.enregistrer(new SerologieId(UUID.randomUUID()), PATIENT, CENTRE, "MED-001",
                        LocalDate.of(2026, 12, 31), TypeSerologie.VHB, ResultatSerologie.NEGATIF,
                        null, null, null, null, null, MAINTENANT));
    }

    @Test
    void reject_resultat_positif_sans_date_resultat() {
        assertThrows(IllegalArgumentException.class, () ->
                Serologie.enregistrer(new SerologieId(UUID.randomUUID()), PATIENT, CENTRE, "MED-001",
                        DATE_PRELEVEMENT, TypeSerologie.VHB, ResultatSerologie.POSITIF,
                        "45.0", "UI/mL", null, null, null, MAINTENANT));
    }

    @Test
    void creer_negatif_initialise_correctement() {
        Serologie s = serologieValide(TypeSerologie.VHB, ResultatSerologie.NEGATIF);
        assertEquals(ResultatSerologie.NEGATIF, s.resultat());
        assertEquals(TypeSerologie.VHB, s.typeSerologie());
        assertEquals(CENTRE, s.centreId());
        assertEquals(PATIENT, s.patientId());
        assertEquals("45.0", s.valeur());
        assertEquals("UI/mL", s.unite());
        assertFalse(s.estPositif());
    }

    @Test
    void creer_positif_est_positif() {
        Serologie s = serologieValide(TypeSerologie.VIH, ResultatSerologie.POSITIF);
        assertTrue(s.estPositif());
        assertEquals(ResultatSerologie.POSITIF, s.resultat());
    }

    @Test
    void attendre_n_est_pas_positif() {
        Serologie s = serologieValide(TypeSerologie.VHC, ResultatSerologie.EN_ATTENTE);
        assertFalse(s.estPositif());
        assertEquals(ResultatSerologie.EN_ATTENTE, s.resultat());
    }

    @Test
    void indecis_n_est_pas_positif() {
        Serologie s = serologieValide(TypeSerologie.SYPHILIS, ResultatSerologie.INDECIS);
        assertFalse(s.estPositif());
        assertEquals(ResultatSerologie.INDECIS, s.resultat());
    }

    @Test
    void reconstituer_preserve_etat() {
        Serologie originale = serologieValide(TypeSerologie.VHB, ResultatSerologie.POSITIF);
        Serologie reconstituee = Serologie.reconstituer(
                originale.id(), originale.patientId(), originale.centreId(),
                originale.prescripteurId(), originale.datePrelevement(), originale.typeSerologie(),
                originale.resultat(), originale.valeur(), originale.unite(), originale.dateResultat(),
                originale.laboratoire(), originale.commentaire(),
                originale.createdAt(), originale.updatedAt());
        assertEquals(originale.id(), reconstituee.id());
        assertEquals(originale.patientId(), reconstituee.patientId());
        assertEquals(originale.centreId(), reconstituee.centreId());
        assertEquals(originale.resultat(), reconstituee.resultat());
        assertEquals(originale.typeSerologie(), reconstituee.typeSerologie());
        assertTrue(reconstituee.estPositif());
    }

    @Test
    void centres_concernees_retourne_centre_own() {
        Serologie s = serologieValide(TypeSerologie.VHB, ResultatSerologie.NEGATIF);
        List<CentreId> centres = s.centresConcernees();
        assertEquals(1, centres.size());
        assertEquals(CENTRE, centres.get(0));
    }

    @Test
    void centre_different_est_distinct() {
        Serologie s = serologieValide(TypeSerologie.VHB, ResultatSerologie.NEGATIF);
        assertEquals(CENTRE, s.centreId());
        assertFalse(s.centreId().equals(CENTRE_B));
    }
}
