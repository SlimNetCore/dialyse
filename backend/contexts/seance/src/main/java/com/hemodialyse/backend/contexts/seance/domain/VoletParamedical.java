package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * Volet paramédical d'une séance : les constantes relevées par l'infirmier, un seul par séance (agrégat à une entité, identité propre,
 * séance référencée par identité ; {@code UNIQUE (seance_id)} en base, Flyway V1). {@link #remplacerLesMesures} REMPLACE tout, sans
 * version ni historique : une mesure absente efface la valeur déjà saisie (DM1, reproduit). La règle d'accès selon le statut de la séance
 * est portée par {@link Seance#exigerUnVoletParamedical()} ; le centre du volet est celui de la séance saisie (KB-SEA-94 : un volet posé
 * sous un autre centre reste invisible et bloque la saisie, reproduit).
 */
public final class VoletParamedical {

    private final UUID id;
    private final CentreId centre;
    private final SeanceId seance;
    private MesuresDeSeance mesures;
    private final Instant creeLe;
    private Instant modifieLe;

    private VoletParamedical(UUID id, CentreId centre, SeanceId seance, MesuresDeSeance mesures, Instant creeLe, Instant modifieLe) {
        if (id == null) throw new SaisieInvalide("Identifiant du volet requis");
        if (centre == null) throw new SaisieInvalide("Centre requis");
        if (seance == null) throw new SaisieInvalide("Séance requise");
        if (mesures == null) throw new SaisieInvalide("Mesures requises");
        this.id = id;
        this.centre = centre;
        this.seance = seance;
        this.mesures = mesures;
        this.creeLe = creeLe;
        this.modifieLe = modifieLe;
    }

    /** Premier volet de la séance : création et modification datées du même instant. */
    public static VoletParamedical ouvrir(UUID id, CentreId centre, SeanceId seance, MesuresDeSeance mesures, Instant maintenant) {
        if (maintenant == null) throw new SaisieInvalide("Instant requis");
        return new VoletParamedical(id, centre, seance, mesures, maintenant, maintenant);
    }

    /** Restitue un volet existant (adapter de persistance) ; les dates peuvent être nulles sur une ligne historique. */
    public static VoletParamedical restituer(UUID id, CentreId centre, SeanceId seance, MesuresDeSeance mesures, Instant creeLe,
                                             Instant modifieLe) {
        return new VoletParamedical(id, centre, seance, mesures, creeLe, modifieLe);
    }

    /** Remplace les dix mesures en entier ; la date de création est conservée. */
    public void remplacerLesMesures(MesuresDeSeance nouvelles, Instant maintenant) {
        if (nouvelles == null) throw new SaisieInvalide("Mesures requises");
        if (maintenant == null) throw new SaisieInvalide("Instant requis");
        this.mesures = nouvelles;
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

    public MesuresDeSeance mesures() {
        return mesures;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
