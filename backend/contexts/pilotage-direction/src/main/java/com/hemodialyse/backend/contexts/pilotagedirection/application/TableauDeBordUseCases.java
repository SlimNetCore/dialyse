package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBord;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;

/**
 * Port d'entrée (use cases) du read model {@code TableauDeBord} (slice PD1).
 * Cas d'usage : {@code ConsulterTableauDeBord}, {@code FiltrerParCentre} (même lecture,
 * le centre étant la clé d'isolation multi-centre).
 */
public interface TableauDeBordUseCases {

    TableauDeBord consulter(CentreId centreId);

    TableauDeBord actualiser(ActualiserTableauDeBordCommande commande, Instant maintenant);

    record ActualiserTableauDeBordCommande(CentreId centreId, String indicateurs) {
    }
}
