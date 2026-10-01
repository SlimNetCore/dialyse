package com.hemodialyse.backend.domain.gmao.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Value Object : Tâche d'intervention
 * Détail d'une tâche effectuée lors d'une intervention
 */
public class TacheIntervention {

    private UUID id;
    private String description;
    private StatutTache statut;
    private OffsetDateTime dateCreation;
    private OffsetDateTime dateCompletion;

    private TacheIntervention() {
    }

    /**
     * Crée une nouvelle tâche
     */
    public static TacheIntervention creer(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description requise");
        }

        TacheIntervention tache = new TacheIntervention();
        tache.id = UUID.randomUUID();
        tache.description = description;
        tache.statut = StatutTache.A_FAIRE;
        tache.dateCreation = OffsetDateTime.now(ZoneOffset.UTC);

        return tache;
    }

    /**
     * Marque la tâche comme en cours
     */
    public void demarrer() {
        if (this.statut != StatutTache.A_FAIRE) {
            throw new IllegalStateException("Seule une tâche 'À faire' peut être démarrée");
        }
        this.statut = StatutTache.EN_COURS;
    }

    /**
     * Marque la tâche comme complétée
     */
    public void completer() {
        if (this.statut != StatutTache.EN_COURS) {
            throw new IllegalStateException("Seule une tâche 'En cours' peut être complétée");
        }
        this.statut = StatutTache.COMPLETEE;
        this.dateCompletion = OffsetDateTime.now(ZoneOffset.UTC);
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public StatutTache getStatut() {
        return statut;
    }

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }

    public OffsetDateTime getDateCompletion() {
        return dateCompletion;
    }
}

