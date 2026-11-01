package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Implémentation des use cases {@code TableauDeBordUseCases}.
 * Orchestre l'entité et le port de sortie ; génère l'instant.
 */
public class TableauDeBordService implements TableauDeBordUseCases {

    private final TableauDeBordRepository tableaux;

    public TableauDeBordService(TableauDeBordRepository tableaux) {
        this.tableaux = tableaux;
    }

    @Override
    public TableauDeBord consulter(CentreId centreId) {
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        return tableaux.trouver(centreId)
                .orElseThrow(() -> new IllegalArgumentException("Tableau de bord introuvable"));
    }

    @Override
    public TableauDeBord actualiser(ActualiserTableauDeBordCommande commande, Instant maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        OffsetDateTime maintenantOd = maintenant.atOffset(ZoneOffset.UTC);
        TableauDeBord tableau = TableauDeBord.actualiser(commande.centreId(), commande.indicateurs(),
                maintenantOd);
        tableaux.save(tableau);
        return tableau;
    }
}
