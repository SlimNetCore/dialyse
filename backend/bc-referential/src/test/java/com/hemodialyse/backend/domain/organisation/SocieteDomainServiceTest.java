package com.hemodialyse.backend.domain.organisation;

import com.hemodialyse.backend.domain.organisation.model.Centre;
import com.hemodialyse.backend.domain.organisation.model.Coordonnees;
import com.hemodialyse.backend.domain.organisation.model.Societe;
import com.hemodialyse.backend.domain.organisation.port.SocieteRepositoryPort;
import com.hemodialyse.backend.domain.organisation.port.SocieteUseCase.CentreData;
import com.hemodialyse.backend.domain.organisation.port.SocieteUseCase.SocieteData;
import com.hemodialyse.backend.domain.organisation.service.SocieteDomainService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocieteDomainServiceTest {

    private InMemoryRepo repo;
    private SocieteDomainService service;
    private int txRuns;

    @BeforeEach
    void setup() {
        repo = new InMemoryRepo();
        txRuns = 0;
        service = new SocieteDomainService(repo, work -> {
            txRuns++;
            work.run();
        });
    }

    private Societe creer(String societeCode, String centreCode) {
        return service.creer(
                new SocieteData(societeCode, "Société " + societeCode, null, null, null, Coordonnees.VIDES, null),
                new CentreData(centreCode, "Centre " + centreCode, Coordonnees.VIDES));
    }

    @Test
    void laCreationEnregistreLaSocieteAvecSonPremierCentre() {
        Societe s = creer("SOC1", "CTR1");
        assertEquals(1, s.centres().size());
        assertTrue(s.actif());
        assertEquals(1, repo.saves);
    }

    @Test
    void laCreationSansPremierCentreEstRefusee() {
        assertThrows(BusinessException.class, () -> service.creer(
                new SocieteData("SOC1", "Société", null, null, null, null, null), null));
        assertEquals(0, repo.saves);
    }

    @Test
    void lesCodesSocieteEtCentreSontUniques() {
        creer("SOC1", "CTR1");
        assertEquals("SOCIETE_CODE_DEJA_UTILISE",
                assertThrows(BusinessException.class, () -> creer("SOC1", "CTR2")).getCode());
        assertEquals("CENTRE_CODE_DEJA_UTILISE",
                assertThrows(BusinessException.class, () -> creer("SOC2", "CTR1")).getCode());
    }

    @Test
    void ajouterPuisDesactiverUnCentreRespecteLInvariant() {
        Societe s = creer("SOC1", "CTR1");
        UUID premier = s.centres().get(0).id();
        assertThrows(BusinessException.class, () -> service.desactiverCentre(s.id(), premier));

        Societe apres = service.ajouterCentre(s.id(), new CentreData("CTR2", "Deuxième", Coordonnees.VIDES));
        assertEquals(2, apres.centres().size());
        service.desactiverCentre(s.id(), premier);
        UUID second = apres.centres().get(1).id();
        assertThrows(BusinessException.class, () -> service.desactiverCentre(s.id(), second));
    }

    @Test
    void transfererUnCentreDeplaceLeCentreDansLaMemeTransaction() {
        Societe a = creer("SOC-A", "CTR-A1");
        service.ajouterCentre(a.id(), new CentreData("CTR-A2", "A2", Coordonnees.VIDES));
        Societe b = creer("SOC-B", "CTR-B1");
        UUID aTransferer = a.centres().get(1).id();

        service.transfererCentre(a.id(), aTransferer, b.id());

        assertEquals(1, a.centres().size());
        assertEquals(2, b.centres().size());
        assertEquals(1, txRuns);
    }

    @Test
    void transfererLeDernierCentreActifEstRefuseSansRienEnregistrer() {
        Societe a = creer("SOC-A", "CTR-A1");
        Societe b = creer("SOC-B", "CTR-B1");
        int savesAvant = repo.saves;
        UUID unique = a.centres().get(0).id();

        assertThrows(BusinessException.class, () -> service.transfererCentre(a.id(), unique, b.id()));

        assertEquals(1, a.centres().size());
        assertEquals(1, b.centres().size());
        assertEquals(savesAvant, repo.saves);
        assertEquals(0, txRuns);
    }

    @Test
    void transfererVersUneSocieteInactiveOuLaMemeSocieteEstRefuse() {
        Societe a = creer("SOC-A", "CTR-A1");
        service.ajouterCentre(a.id(), new CentreData("CTR-A2", "A2", Coordonnees.VIDES));
        Societe b = creer("SOC-B", "CTR-B1");
        service.desactiver(b.id());
        UUID centre = a.centres().get(1).id();

        assertEquals("SOCIETE_INACTIVE",
                assertThrows(BusinessException.class, () -> service.transfererCentre(a.id(), centre, b.id())).getCode());
        assertEquals("CENTRE_TRANSFERT_MEME_SOCIETE",
                assertThrows(BusinessException.class, () -> service.transfererCentre(a.id(), centre, a.id())).getCode());
    }

    @Test
    void uneSocieteInconnueEstSignalee() {
        assertEquals("SOCIETE_INTROUVABLE",
                assertThrows(BusinessException.class, () -> service.desactiver(UUID.randomUUID())).getCode());
    }

    @Test
    void modifierUnCentreVerifieAussiLUnicite() {
        Societe a = creer("SOC-A", "CTR-A1");
        creer("SOC-B", "CTR-B1");
        Centre c = a.centres().get(0);
        assertThrows(BusinessException.class,
                () -> service.modifierCentre(a.id(), c.id(), new CentreData("CTR-B1", "Renommé", Coordonnees.VIDES)));
    }

    /**
     * Dépôt en mémoire : une copie profonde n'est pas nécessaire pour ces scénarios.
     */
    static class InMemoryRepo implements SocieteRepositoryPort {
        final Map<UUID, Societe> data = new LinkedHashMap<>();
        int saves;

        @Override
        public Optional<Societe> findById(UUID id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public Societe save(Societe societe) {
            saves++;
            data.put(societe.id(), societe);
            return societe;
        }

        @Override
        public boolean existsSocieteCode(String code, UUID excluded) {
            return data.values().stream().anyMatch(s -> s.code().equals(code) && !s.id().equals(excluded));
        }

        @Override
        public boolean existsCentreCode(String code, UUID excluded) {
            return data.values().stream().flatMap(s -> s.centres().stream())
                    .anyMatch(c -> c.code().equals(code) && !c.id().equals(excluded));
        }

        @Override
        public PagedResult<Societe> findPaged(String search, int page, int size) {
            return PagedResult.of(List.copyOf(data.values()), data.size(), page, size);
        }
    }
}
