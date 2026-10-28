package com.hemodialyse.backend.contexts.facturation.domain.seance;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;

import java.time.LocalDate;
import java.util.UUID;

/**
 * La séance vue par la Facturation, tant qu'elle n'est pas facturée : ce qui décide si on peut l'exclure de la facturation
 * (RG-FAC-023), la réinclure ou changer son forfait. Seule une séance validée ou signée, non facturée, se modifie.
 * <p>
 * L'exclusion passe la séance à {@code ABSENT} (KB-FAC-13) : le statut d'avant (validée ou signée) et l'absence déclarée par
 * l'exclusion sont mémorisés, ce qui permet de réinclure la séance tant qu'elle n'est pas facturée. Une séance déjà
 * {@code ABSENT} sans cette mémoire (absence réelle ou exclusion antérieure à la slice) ne se réinclut pas.
 */
public final class SeanceDeFacturation {

    private final UUID id;
    private final CentreId centre;
    private final UUID patientId;
    private final LocalDate date;
    private final StatutDeSeance statut;
    private final boolean facturee;
    private final StatutDeSeance statutAvantExclusion;
    private final UUID absenceDeLExclusion;

    public SeanceDeFacturation(UUID id, CentreId centre, UUID patientId, LocalDate date, StatutDeSeance statut, boolean facturee,
                               StatutDeSeance statutAvantExclusion, UUID absenceDeLExclusion) {
        if (id == null || centre == null || patientId == null || date == null || statut == null) {
            throw new IllegalArgumentException("Séance incomplète");
        }
        this.id = id;
        this.centre = centre;
        this.patientId = patientId;
        this.date = date;
        this.statut = statut;
        this.facturee = facturee;
        this.statutAvantExclusion = statutAvantExclusion;
        this.absenceDeLExclusion = absenceDeLExclusion;
    }

    /**
     * Exclut la séance de la facturation, avec le motif de l'absence que cela déclare. Refusée (422) si la séance n'est pas une
     * séance validée ou signée non facturée ; le motif est obligatoire et {@code AUTRE} exige un commentaire (400).
     */
    public Exclusion exclure(MotifDAbsence motif, String commentaire) {
        if (!modifiable()) {
            throw new IllegalStateException("Seance introuvable ou non modifiable pour exclusion");
        }
        if (motif == null) {
            throw new IllegalArgumentException("Le motif d'exclusion est obligatoire");
        }
        String commentaireNettoye = commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
        if (motif.commentaireRequis() && commentaireNettoye == null) {
            throw new IllegalArgumentException("Un commentaire est obligatoire pour le motif " + motif);
        }
        return new Exclusion(statut, motif, commentaireNettoye);
    }

    /**
     * Réinclut une séance exclue par la Facturation : refusée (422) si elle est facturée, n'est pas {@code ABSENT} ou n'a pas
     * gardé le statut d'avant l'exclusion.
     */
    public Reinclusion reinclure() {
        if (facturee || !statut.equals(StatutDeSeance.ABSENT) || statutAvantExclusion == null
                || !statutAvantExclusion.estFacturable()) {
            throw new IllegalStateException("Seance introuvable ou non reinclusable");
        }
        return new Reinclusion(statutAvantExclusion, absenceDeLExclusion);
    }

    /**
     * Vérifie qu'on peut imposer un autre forfait à la séance (422 sinon).
     */
    public void verifierQueLeForfaitPeutChanger() {
        if (!modifiable()) {
            throw new IllegalStateException("Seance introuvable ou non modifiable pour changement forfait");
        }
    }

    private boolean modifiable() {
        return !facturee && statut.estFacturable();
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public UUID patientId() {
        return patientId;
    }

    public LocalDate date() {
        return date;
    }

    public StatutDeSeance statut() {
        return statut;
    }

    public boolean facturee() {
        return facturee;
    }

    public StatutDeSeance statutAvantExclusion() {
        return statutAvantExclusion;
    }

    public UUID absenceDeLExclusion() {
        return absenceDeLExclusion;
    }
}
