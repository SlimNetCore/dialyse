package com.hemodialyse.backend.domain.organisation;

import com.hemodialyse.backend.domain.organisation.model.Centre;
import com.hemodialyse.backend.domain.organisation.model.Coordonnees;
import com.hemodialyse.backend.domain.organisation.model.Societe;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SocieteTest {

    private static Centre centre(String code) {
        return Centre.creer(code, "Centre " + code, Coordonnees.VIDES);
    }

    private static Societe societe(Centre premier) {
        return Societe.creer("grp-est", "Groupe Est", null, null, null, Coordonnees.VIDES, premier);
    }

    @Test
    void uneSocieteNePeutPasEtreCreeeSansCentre() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> Societe.creer("SOC", "Société", null, null, null, null, null));
        assertEquals("SOCIETE_SANS_CENTRE", e.getCode());
    }

    @Test
    void laReconstitutionSansCentreEstRefusee() {
        assertThrows(BusinessException.class, () -> new Societe(java.util.UUID.randomUUID(), "SOC", "Société",
                null, null, null, null, true, null, List.of()));
    }

    @Test
    void lesCodesSontNormalisesEnMajuscules() {
        Societe s = societe(centre("c1"));
        assertEquals("GRP-EST", s.code());
        assertEquals("C1", s.centres().get(0).code());
    }

    @Test
    void unCodeOuUnNomInvalideEstRefuse() {
        assertThrows(BusinessException.class, () -> Societe.creer("!", "Société", null, null, null, null, centre("C1")));
        assertThrows(BusinessException.class, () -> Societe.creer("SOC", "  ", null, null, null, null, centre("C1")));
        assertThrows(BusinessException.class, () -> Centre.creer("X", "Centre", null));
        assertThrows(BusinessException.class, () -> Centre.creer("CTR", "", null));
    }

    @Test
    void ledernierCentreActifNePeutPasEtreDesactive() {
        Centre unique = centre("C1");
        Societe s = societe(unique);
        BusinessException e = assertThrows(BusinessException.class, () -> s.desactiverCentre(unique.id()));
        assertEquals("SOCIETE_DERNIER_CENTRE", e.getCode());
        assertTrue(unique.actif());
    }

    @Test
    void ledernierCentreActifNePeutPasEtreRetire() {
        Centre unique = centre("C1");
        Societe s = societe(unique);
        assertThrows(BusinessException.class, () -> s.retirerCentre(unique.id()));
        assertEquals(1, s.centres().size());
    }

    @Test
    void ondesactiveUnCentreTantQuUnAutreResteActif() {
        Centre a = centre("C1");
        Centre b = centre("C2");
        Societe s = societe(a);
        s.ajouterCentre(b);
        s.desactiverCentre(a.id());
        assertFalse(a.actif());
        // b devient le dernier actif : il est protégé
        assertThrows(BusinessException.class, () -> s.desactiverCentre(b.id()));
        assertThrows(BusinessException.class, () -> s.retirerCentre(b.id()));
        // un centre inactif peut être retiré (transféré) sans violer la règle
        assertEquals(a, s.retirerCentre(a.id()));
    }

    @Test
    void reactiverUnCentreLibereLaProtection() {
        Centre a = centre("C1");
        Centre b = centre("C2");
        Societe s = societe(a);
        s.ajouterCentre(b);
        s.desactiverCentre(a.id());
        s.activerCentre(a.id());
        s.desactiverCentre(b.id());
        assertTrue(a.actif());
        assertFalse(b.actif());
    }

    @Test
    void unCentreDejaRattacheNePeutPasEtreAjouteDeuxFois() {
        Centre a = centre("C1");
        Societe s = societe(a);
        assertThrows(BusinessException.class, () -> s.ajouterCentre(a));
    }

    @Test
    void lesCoordonneesSontValideesEtNormalisees() {
        Coordonnees c = new Coordonnees(" 12 rue X ", "", null, "0555 12 34 56", "  Contact@Exemple.DZ ", "https://exemple.dz");
        assertEquals("12 rue X", c.adresse());
        assertNull(c.ville());
        assertEquals("contact@exemple.dz", c.email());
        assertThrows(BusinessException.class, () -> new Coordonnees(null, null, null, "abc", null, null));
        assertThrows(BusinessException.class, () -> new Coordonnees(null, null, null, null, "pas-un-email", null));
        assertThrows(BusinessException.class, () -> new Coordonnees(null, null, null, null, null, "http://"));
    }
}
