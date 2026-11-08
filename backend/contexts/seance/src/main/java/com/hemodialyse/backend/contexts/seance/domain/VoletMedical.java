package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * Volet médical d'une séance : les observations du médecin, un seul par séance (agrégat à une entité, identité propre, séance
 * référencée par identité). {@link #remplacerLesObservations} REMPLACE tout, sans version ni historique : une observation absente efface
 * la valeur déjà saisie (KB-SEA-59, reproduit avec alerte : intégrité médicale, ADR 0094). La règle d'accès selon le statut de la séance
 * est portée par {@link Seance#exigerUnVoletMedical()}.
 */
public final class VoletMedical {

    private final UUID id;
    private final CentreId centre;
    private final SeanceId seance;
    private ObservationsMedicales observations;
    private final Instant creeLe;
    private Instant modifieLe;

    private VoletMedical(UUID id, CentreId centre, SeanceId seance, ObservationsMedicales observations, Instant creeLe, Instant modifieLe) {
        if (id == null) throw new SaisieInvalide("Identifiant du volet requis");
        if (centre == null) throw new SaisieInvalide("Centre requis");
        if (seance == null) throw new SaisieInvalide("Séance requise");
        if (observations == null) throw new SaisieInvalide("Observations requises");
        this.id = id;
        this.centre = centre;
        this.seance = seance;
        this.observations = observations;
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
    }

    /** Premier volet de la séance. */
    public static VoletMedical ouvrir(UUID id, CentreId centre, SeanceId seance, ObservationsMedicales observations, Instant maintenant) {
        if (maintenant == null) throw new SaisieInvalide("Instant requis");
        return new VoletMedical(id, centre, seance, observations, maintenant, maintenant);
    }

    /** Restitue un volet existant (adapter de persistance) ; les dates peuvent être nulles sur une ligne historique. */
    public static VoletMedical restituer(UUID id, CentreId centre, SeanceId seance, ObservationsMedicales observations, Instant creeLe,
                                         Instant modifieLe) {
        return new VoletMedical(id, centre, seance, observations, creeLe, modifieLe);
    }

    /** Remplace les six observations en entier ; la date de création est conservée. */
    public void remplacerLesObservations(ObservationsMedicales nouvelles, Instant maintenant) {
        if (nouvelles == null) throw new SaisieInvalide("Observations requises");
        if (maintenant == null) throw new SaisieInvalide("Instant requis");
        this.observations = nouvelles;
        this.modifieLe = maintenant;
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public SeanceId seance() {
        return seance;
    }

    public ObservationsMedicales observations() {
        return observations;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
