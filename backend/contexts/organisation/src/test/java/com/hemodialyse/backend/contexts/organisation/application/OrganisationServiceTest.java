package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.CentreRepository;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.organisation.domain.SocieteRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires du service {@link OrganisationService} pour les lectures
 * ajoutées par la bascule O5 (consulter une société, lister les centres d'une société).
 */
class OrganisationServiceTest {

    private static final SocieteId SOCIETE = new SocieteId(UUID.fromString("99997300-0000-0000-0000-0000000000a1"));

    private OrganisationService nouveauService(FakeSocieteRepository societes, FakeCentreRepository centres) {
        return new OrganisationService(societes, centres,
                new FakeSalleRepository(), new FakeCreneauRepository());
    }

    @Test
    void consulter_societe_existante() {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(SOCIETE, "RENADIAL", "Renadial", true));
        OrganisationService service = nouveauService(societes, new FakeCentreRepository());

        Societe trouvee = service.consulterSociete(SOCIETE);

        assertEquals(SOCIETE, trouvee.id());
        assertEquals("RENADIAL", trouvee.code());
    }

    @Test
    void consulter_societe_inconnue_rejette() {
        OrganisationService service = nouveauService(new FakeSocieteRepository(), new FakeCentreRepository());

        assertThrows(IllegalArgumentException.class, () -> service.consulterSociete(SOCIETE));
    }

    @Test
    void lister_centres_de_la_societe() {
        FakeCentreRepository centres = new FakeCentreRepository();
        CentreId id1 = new CentreId(UUID.randomUUID());
        CentreId id2 = new CentreId(UUID.randomUUID());
        centres.sauvegarder(new Centre(id1, SOCIETE, "C1", "Centre 1", "Africa/Algiers", 10, true));
        centres.sauvegarder(new Centre(id2, SOCIETE, "C2", "Centre 2", "Africa/Algiers", 12, false));
        OrganisationService service = nouveauService(new FakeSocieteRepository(), centres);

        List<Centre> resultat = service.listerCentres(SOCIETE);

        assertEquals(2, resultat.size());
    }

    @Test
    void lister_centres_societe_sans_centre_est_vide() {
        OrganisationService service = nouveauService(new FakeSocieteRepository(), new FakeCentreRepository());

        assertEquals(0, service.listerCentres(SOCIETE).size());
    }

    private static class FakeSocieteRepository implements SocieteRepository {
        private final Map<SocieteId, Societe> stock = new HashMap<>();

        @Override
        public Optional<Societe> trouver(SocieteId id) {
            return Optional.ofNullable(stock.get(id));
        }

        @Override
        public void sauvegarder(Societe societe) {
            stock.put(societe.id(), societe);
        }

        @Override
        public List<Societe> lister() {
            return new ArrayList<>(stock.values());
        }
    }

    private static class FakeCentreRepository implements CentreRepository {
        private final Map<CentreId, Centre> stock = new HashMap<>();

        @Override
        public Optional<Centre> trouver(CentreId id) {
            return Optional.ofNullable(stock.get(id));
        }

        @Override
        public void sauvegarder(Centre centre) {
            stock.put(centre.id(), centre);
        }

        @Override
        public List<Centre> listerActifs(
                com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId societeId) {
            return stock.values().stream()
                    .filter(c -> c.societeId().equals(societeId) && c.actif()).toList();
        }

        @Override
        public List<Centre> listerParSociete(
                com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId societeId) {
            return stock.values().stream()
                    .filter(c -> c.societeId().equals(societeId)).toList();
        }
    }

    private static class FakeSalleRepository implements SalleRepository {
        @Override
        public Optional<com.hemodialyse.backend.contexts.organisation.domain.Salle> trouver(
                com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centreId,
                com.hemodialyse.backend.contexts.organisation.domain.SalleId salleId) {
            return Optional.empty();
        }

        @Override
        public void sauvegarder(com.hemodialyse.backend.contexts.organisation.domain.Salle salle) {
        }

        @Override
        public List<com.hemodialyse.backend.contexts.organisation.domain.Salle> listerParCentre(
                com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centreId) {
            return List.of();
        }
    }

    private static class FakeCreneauRepository implements CreneauRepository {
        @Override
        public Optional<com.hemodialyse.backend.contexts.organisation.domain.Creneau> trouver(
                com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centreId,
                com.hemodialyse.backend.contexts.organisation.domain.CreneauId creneauId) {
            return Optional.empty();
        }

        @Override
        public void sauvegarder(com.hemodialyse.backend.contexts.organisation.domain.Creneau creneau) {
        }

        @Override
        public List<com.hemodialyse.backend.contexts.organisation.domain.Creneau> listerParCentre(
                com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centreId) {
            return List.of();
        }
    }
}
