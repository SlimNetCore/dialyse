package com.hemodialyse.backend.domain.organisation.service;

import com.hemodialyse.backend.domain.organisation.model.Centre;
import com.hemodialyse.backend.domain.organisation.model.Societe;
import com.hemodialyse.backend.domain.organisation.port.SocieteRepositoryPort;
import com.hemodialyse.backend.domain.organisation.port.SocieteUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.port.TransactionRunner;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain Service — orchestration des sociétés et de leurs centres. Les invariants (au moins un centre actif,
 * codes valides) vivent dans l'agrégat {@link Societe} ; ce service ajoute l'unicité des codes, qui exige la
 * persistance, et l'atomicité du transfert d'un centre.
 * <p>
 * Pure domain class (no Spring/JPA dependency). Wired as a bean in {@code DomainServiceConfig}.
 */
public class SocieteDomainService implements SocieteUseCase {

    private final SocieteRepositoryPort repo;
    private final TransactionRunner tx;

    public SocieteDomainService(SocieteRepositoryPort repo, TransactionRunner tx) {
        this.repo = repo;
        this.tx = tx;
    }

    @Override
    public Societe creer(SocieteData data, CentreData premierCentre) {
        if (premierCentre == null) {
            throw new BusinessException("SOCIETE_SANS_CENTRE", "Une société doit avoir au moins un centre");
        }
        Centre centre = Centre.creer(premierCentre.code(), premierCentre.nom(), premierCentre.coordonnees());
        Societe societe = Societe.creer(data.code(), data.raisonSociale(), data.nif(), data.nis(), data.rc(),
                data.coordonnees(), centre);
        societe.definirPiedDePage(data.piedDePage());
        garantirCodeSocieteLibre(societe.code(), null);
        garantirCodeCentreLibre(centre.code(), centre.id());
        return repo.save(societe);
    }

    @Override
    public Societe modifier(UUID societeId, SocieteData data) {
        Societe societe = requise(societeId);
        societe.modifier(data.code(), data.raisonSociale(), data.nif(), data.nis(), data.rc(), data.coordonnees());
        societe.definirPiedDePage(data.piedDePage());
        garantirCodeSocieteLibre(societe.code(), societe.id());
        return repo.save(societe);
    }

    @Override
    public Societe activer(UUID societeId) {
        Societe societe = requise(societeId);
        societe.activer();
        return repo.save(societe);
    }

    @Override
    public Societe desactiver(UUID societeId) {
        Societe societe = requise(societeId);
        societe.desactiver();
        return repo.save(societe);
    }

    @Override
    public Societe ajouterCentre(UUID societeId, CentreData data) {
        Societe societe = requise(societeId);
        Centre centre = Centre.creer(data.code(), data.nom(), data.coordonnees());
        garantirCodeCentreLibre(centre.code(), centre.id());
        societe.ajouterCentre(centre);
        return repo.save(societe);
    }

    @Override
    public Societe modifierCentre(UUID societeId, UUID centreId, CentreData data) {
        Societe societe = requise(societeId);
        societe.modifierCentre(centreId, data.code(), data.nom(), data.coordonnees());
        garantirCodeCentreLibre(societe.centre(centreId).orElseThrow().code(), centreId);
        return repo.save(societe);
    }

    @Override
    public Societe activerCentre(UUID societeId, UUID centreId) {
        Societe societe = requise(societeId);
        societe.activerCentre(centreId);
        return repo.save(societe);
    }

    @Override
    public Societe desactiverCentre(UUID societeId, UUID centreId) {
        Societe societe = requise(societeId);
        societe.desactiverCentre(centreId);
        return repo.save(societe);
    }

    @Override
    public void transfererCentre(UUID societeSourceId, UUID centreId, UUID societeCibleId) {
        if (societeSourceId.equals(societeCibleId)) {
            throw new BusinessException("CENTRE_TRANSFERT_MEME_SOCIETE", "Le centre appartient déjà à cette société");
        }
        Societe source = requise(societeSourceId);
        Societe cible = requise(societeCibleId);
        if (!cible.actif()) {
            throw new BusinessException("SOCIETE_INACTIVE", "La société de destination est désactivée");
        }
        Centre centre = source.retirerCentre(centreId);
        cible.ajouterCentre(centre);
        tx.run(() -> {
            repo.save(cible);
            repo.save(source);
        });
    }

    @Override
    public Optional<Societe> trouver(UUID societeId) {
        return repo.findById(societeId);
    }

    @Override
    public PagedResult<Societe> lister(String recherche, int page, int size) {
        return repo.findPaged(recherche, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private Societe requise(UUID societeId) {
        return repo.findById(societeId)
                .orElseThrow(() -> new BusinessException("SOCIETE_INTROUVABLE", "Société introuvable"));
    }

    private void garantirCodeSocieteLibre(String code, UUID excludedId) {
        if (repo.existsSocieteCode(code, excludedId)) {
            throw new BusinessException("SOCIETE_CODE_DEJA_UTILISE", "Ce code de société est déjà utilisé");
        }
    }

    private void garantirCodeCentreLibre(String code, UUID excludedCentreId) {
        if (repo.existsCentreCode(code, excludedCentreId)) {
            throw new BusinessException("CENTRE_CODE_DEJA_UTILISE", "Ce code de centre est déjà utilisé");
        }
    }
}
