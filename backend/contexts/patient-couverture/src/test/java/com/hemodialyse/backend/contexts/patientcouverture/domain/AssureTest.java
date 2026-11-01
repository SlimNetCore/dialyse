package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AssureTest {
    @Test
    void should_reject_assure_sans_nom() {
        assertThrows(IllegalArgumentException.class, () ->
                Assure.creer(new AssureId("N1"), new CentreId(UUID.randomUUID()), " ", "P", "M",
                        LocalDate.of(1980, 1, 1), null, null, null, null, null, TypeAssure.ASSURE_LUI_MEME));
    }

    @Test
    void should_modifier_assure() {
        Assure a = Assure.creer(new AssureId("N1"), new CentreId(UUID.randomUUID()), "Doe", "John", "M",
                LocalDate.of(1980, 1, 1), null, null, null, null, null, TypeAssure.ASSURE_LUI_MEME);
        a.modifier("Smith", null, null, null, "0123", null, null, "addr", null, TypeAssure.CONJOINT);
        assertEquals("Smith", a.nom());
        assertEquals("0123", a.telPersonnel());
        assertEquals("addr", a.adresse());
        assertEquals(TypeAssure.CONJOINT, a.typeAssure());
    }

    @Test
    void should_reject_deux_assures_principaux_via_assignment() {
        UUID p = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        PatientId pid = new PatientId(p);
        CentreId cid = new CentreId(c);
        AssurePatientAssignment a1 = new AssurePatientAssignment(UUID.randomUUID(), pid, cid, new AssureId("N1"), true, LocalDate.now(), null);
        assertTrue(a1.isActif());
        a1.terminer(LocalDate.now());
        assertFalse(a1.isActif());
    }
}