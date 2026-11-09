package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consulter les répartitions de la direction par centre : sexe, tranche d'âge, caisse d'assurance, traitement de l'anémie. Même période
 * que la vue d'ensemble (validée avant toute lecture) ; effectifs masqués sous le seuil d'anonymat.
 */
public final class ConsulterLaRepartition {

    private final SocietesSuivies societes;
    private final EffectifsDesPatients effectifs;
    private final SeancesRealiseesDesCentres seances;
    private final FacturationDesCentres facturation;
    private final TraitementsDeLAnemieDesCentres anemie;
    private final HorlogeDuPilotage horloge;

    public ConsulterLaRepartition(SocietesSuivies societes, EffectifsDesPatients effectifs, SeancesRealiseesDesCentres seances,
                                  FacturationDesCentres facturation, TraitementsDeLAnemieDesCentres anemie, HorlogeDuPilotage horloge) {
        this.societes = societes;
        this.effectifs = effectifs;
        this.seances = seances;
        this.facturation = facturation;
        this.anemie = anemie;
        this.horloge = horloge;
    }

    public RepartitionDeLaSociete execute(UUID societeId, LocalDate from, LocalDate to) {
        Periode periode = Periode.demandee(from, to, horloge.aujourdhui());
        SocieteSuivie societe = societes.societe(societeId);
        if (societe.estSansCentre()) {
            return RepartitionDeLaSociete.sansCentre(societe, periode, horloge.maintenant());
        }
        List<UUID> centres = societe.identifiantsDesCentres();
        return RepartitionDeLaSociete.constituer(societe, periode, horloge.aujourdhui(), horloge.maintenant(),
                effectifs.profils(centres, periode), effectifs.parCaisse(centres, periode), seances.parCaisse(centres, periode),
                facturation.parCaisse(centres, periode), anemie.parCentre(centres, periode),
                effectifs.prescriptionsDeLAnemie(centres, periode));
    }
}
