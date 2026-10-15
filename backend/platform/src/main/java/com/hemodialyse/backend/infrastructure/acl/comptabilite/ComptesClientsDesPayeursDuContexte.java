package com.hemodialyse.backend.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ComptesClientsDesPayeurs;
import com.hemodialyse.backend.domain.comptabilite.port.ComptePayeurRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * ACL temporaire (retirée avec les slices C2 à C7) : le compte client de chaque payeur est tenu par le contexte
 * {@code comptabilite} (C1) ; la génération des écritures legacy le lit par le langage publié.
 */
@Component
public class ComptesClientsDesPayeursDuContexte implements ComptePayeurRepositoryPort {

    private final ComptesClientsDesPayeurs comptes;

    public ComptesClientsDesPayeursDuContexte(ComptesClientsDesPayeurs comptes) {
        this.comptes = comptes;
    }

    @Override
    public Optional<String> find(UUID centerId, UUID payeurId) {
        return comptes.compteDuPayeur(centerId, payeurId);
    }
}
