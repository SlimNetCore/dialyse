package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Agrégat {@code InstantaneMensuel} — instantané mensuel figé des indicateurs d'un centre
 * (slice PD2, ADR 0009). Un instantané par centre et mois (R1).
 * <p>
 * L'agrégat ne génère jamais d'identifiant ni d'horloge lui-même : l'identifiant et l'instant
 * sont fournis par l'appelant (couche application), conformément à la frontière hexagonale
 * (règle ArchUnit {@code le_domaine_ne_tire_ni_identifiant_ni_heure}).
 */
public final class InstantaneMensuel {

    private final InstantaneMensuelId id;
    private final CentreId centreId;
    private final Mois mois;
    private final String donnees;
    private final boolean fige;
    private final OffsetDateTime createdAt;

    private InstantaneMensuel(InstantaneMensuelId id, CentreId centreId, Mois mois,
                              String donnees, boolean fige, OffsetDateTime createdAt) {
        this.id = id;
        this.centreId = centreId;
        this.mois = mois;
        this.donnees = donnees;
        this.fige = fige;
        this.createdAt = createdAt;
    }

    /**
     * Capture un nouvel instantané mensuel (figé dès sa capture).
     */
    public static InstantaneMensuel capturer(InstantaneMensuelId id, CentreId centreId, Mois mois,
                                             String donnees, OffsetDateTime maintenant) {
        if (id == null) throw new IllegalArgumentException("InstantaneMensuelId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (mois == null) throw new IllegalArgumentException("mois requis");
        if (donnees == null || donnees.isBlank()) throw new IllegalArgumentException("donnees requises");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        return new InstantaneMensuel(id, centreId, mois, donnees, true, maintenant);
    }

    /**
     * Reconstitue un instantané depuis la persistance.
     */
    public static InstantaneMensuel reconstituer(InstantaneMensuelId id, CentreId centreId, Mois mois,
                                                 String donnees, boolean fige, OffsetDateTime createdAt) {
        return new InstantaneMensuel(id, centreId, mois, donnees, fige, createdAt);
    }

    public InstantaneMensuelId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public Mois mois() {
        return mois;
    }

    public String donnees() {
        return donnees;
    }

    public boolean fige() {
        return fige;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    /**
     * Liste des identifiants de centres concernés par cet agrégat (ici un seul : centreId).
     * Utilisé pour le contrôle d'isolation multi-centre.
     */
    public List<CentreId> centresConcernees() {
        return Collections.singletonList(centreId);
    }
}
