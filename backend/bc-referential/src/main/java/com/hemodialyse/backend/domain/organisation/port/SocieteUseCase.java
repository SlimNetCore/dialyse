package com.hemodialyse.backend.domain.organisation.port;

import com.hemodialyse.backend.domain.organisation.model.Coordonnees;
import com.hemodialyse.backend.domain.organisation.model.Societe;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port In — gestion des sociétés et de leurs centres (réservée au SUPERADMIN).
 */
public interface SocieteUseCase {

    /**
     * Crée une société avec son premier centre.
     */
    Societe creer(SocieteData societe, CentreData premierCentre);

    Societe modifier(UUID societeId, SocieteData data);

    Societe activer(UUID societeId);

    Societe desactiver(UUID societeId);

    Societe ajouterCentre(UUID societeId, CentreData centre);

    Societe modifierCentre(UUID societeId, UUID centreId, CentreData centre);

    Societe activerCentre(UUID societeId, UUID centreId);

    /**
     * @throws com.hemodialyse.backend.domain.shared.exception.BusinessException si c'est le dernier centre actif
     */
    Societe desactiverCentre(UUID societeId, UUID centreId);

    /**
     * Rattache un centre à une autre société. La société d'origine doit conserver au moins un centre actif ;
     * les deux sociétés sont mises à jour dans la même transaction.
     */
    void transfererCentre(UUID societeSourceId, UUID centreId, UUID societeCibleId);

    Optional<Societe> trouver(UUID societeId);

    PagedResult<Societe> lister(String recherche, int page, int size);

    record SocieteData(String code, String raisonSociale, String nif, String nis, String rc,
                       Coordonnees coordonnees, String piedDePage) {
    }

    record CentreData(String code, String nom, Coordonnees coordonnees) {
    }
}
