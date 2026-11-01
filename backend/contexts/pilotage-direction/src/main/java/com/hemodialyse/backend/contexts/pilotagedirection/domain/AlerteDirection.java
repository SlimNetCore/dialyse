package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Agrégat {@code AlerteDirection} — alerte de direction d'un centre (slice PD3).
 * Alerte unique par centre et jour (R2). Types : SOUS_EFFECTIF, SUR_CAPACITE,
 * RETARD_VALIDATION, GREFFE_EN_ATTENTE.
 * <p>
 * L'agrégat ne génère jamais d'identifiant ni d'horloge lui-même : l'identifiant et l'instant
 * sont fournis par l'appelant (couche application), conformément à la frontière hexagonale
 * (règle ArchUnit {@code le_domaine_ne_tire_ni_identifiant_ni_heure}).
 */
public final class AlerteDirection {

    private final AlerteDirectionId id;
    private final CentreId centreId;
    private final TypeAlerte type;
    private final String seuil;
    private final String message;
    private final LocalDate date;
    private final boolean lue;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private AlerteDirection(AlerteDirectionId id, CentreId centreId, TypeAlerte type,
                            String seuil, String message, LocalDate date, boolean lue,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.centreId = centreId;
        this.type = type;
        this.seuil = seuil;
        this.message = message;
        this.date = date;
        this.lue = lue;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Génère une nouvelle alerte.
     */
    public static AlerteDirection generer(AlerteDirectionId id, CentreId centreId, TypeAlerte type,
                                          String seuil, String message, LocalDate date,
                                          OffsetDateTime maintenant) {
        if (id == null) throw new IllegalArgumentException("AlerteDirectionId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (type == null) throw new IllegalArgumentException("type requis");
        if (date == null) throw new IllegalArgumentException("date requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        return new AlerteDirection(id, centreId, type, seuil, message, date, false, maintenant, maintenant);
    }

    /**
     * Reconstitue une alerte depuis la persistance.
     */
    public static AlerteDirection reconstituer(AlerteDirectionId id, CentreId centreId, TypeAlerte type,
                                               String seuil, String message, LocalDate date, boolean lue,
                                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new AlerteDirection(id, centreId, type, seuil, message, date, lue, createdAt, updatedAt);
    }

    /**
     * Acquitte l'alerte (marque comme lue).
     */
    public AlerteDirection acquitter(OffsetDateTime maintenant) {
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        if (lue) throw new IllegalArgumentException("ALERTE_DEJA_ACQUITTEE");
        return new AlerteDirection(id, centreId, type, seuil, message, date, true, createdAt, maintenant);
    }

    public AlerteDirectionId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public TypeAlerte type() {
        return type;
    }

    public String seuil() {
        return seuil;
    }

    public String message() {
        return message;
    }

    public LocalDate date() {
        return date;
    }

    public boolean lue() {
        return lue;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    /**
     * Liste des identifiants de centres concernés par cet agrégat (ici un seul : centreId).
     * Utilisé pour le contrôle d'isolation multi-centre.
     */
    public List<CentreId> centresConcernees() {
        return Collections.singletonList(centreId);
    }
}
