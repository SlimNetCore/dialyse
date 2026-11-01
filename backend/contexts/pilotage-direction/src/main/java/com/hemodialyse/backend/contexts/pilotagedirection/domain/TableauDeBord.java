package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Entité {@code TableauDeBord} — vue agrégée des indicateurs d'un centre (slice PD1, read model dédié).
 * <p>
 * Identité : {@code CentreId}. Les indicateurs sont alimentés par les événements publiés des autres
 * contextes (lecture seule, R4 : aucune écriture cross-context) ; ici via la commande
 * {@code actualiser}, qui représente l'alimentation du read model.
 */
public final class TableauDeBord {

    private final CentreId centreId;
    private final String indicateurs;
    private final OffsetDateTime dateGeneration;

    private TableauDeBord(CentreId centreId, String indicateurs, OffsetDateTime dateGeneration) {
        this.centreId = centreId;
        this.indicateurs = indicateurs;
        this.dateGeneration = dateGeneration;
    }

    /**
     * Actualise (ou crée) le tableau de bord d'un centre.
     */
    public static TableauDeBord actualiser(CentreId centreId, String indicateurs, OffsetDateTime maintenant) {
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (indicateurs == null || indicateurs.isBlank()) throw new IllegalArgumentException("indicateurs requis");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        return new TableauDeBord(centreId, indicateurs, maintenant);
    }

    /**
     * Reconstitue un tableau de bord depuis la persistance.
     */
    public static TableauDeBord reconstituer(CentreId centreId, String indicateurs,
                                             OffsetDateTime dateGeneration) {
        return new TableauDeBord(centreId, indicateurs, dateGeneration);
    }

    public CentreId centreId() {
        return centreId;
    }

    public String indicateurs() {
        return indicateurs;
    }

    public OffsetDateTime dateGeneration() {
        return dateGeneration;
    }

    /**
     * Liste des identifiants de centres concernés par cette entité (ici un seul : centreId).
     * Utilisé pour le contrôle d'isolation multi-centre.
     */
    public List<CentreId> centresConcernees() {
        return Collections.singletonList(centreId);
    }
}
