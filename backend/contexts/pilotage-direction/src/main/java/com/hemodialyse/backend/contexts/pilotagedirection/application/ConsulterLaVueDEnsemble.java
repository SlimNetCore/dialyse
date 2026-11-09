package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.ActiviteDeLaPeriodePrecedente;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.VueDEnsembleDeLaSociete;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consulter la vue d'ensemble de la direction : par centre et consolidée, sur une période (défaut : depuis le 1er janvier jusqu'à
 * aujourd'hui). La période est validée AVANT toute lecture. Le périmètre est la société fournie par l'appelant (le jeton, revérifié).
 */
public final class ConsulterLaVueDEnsemble {

    private final SocietesSuivies societes;
    private final EffectifsDesPatients effectifs;
    private final SeancesRealiseesDesCentres seances;
    private final FacturationDesCentres facturation;
    private final HorlogeDuPilotage horloge;

    public ConsulterLaVueDEnsemble(SocietesSuivies societes, EffectifsDesPatients effectifs, SeancesRealiseesDesCentres seances,
                                   FacturationDesCentres facturation, HorlogeDuPilotage horloge) {
        this.societes = societes;
        this.effectifs = effectifs;
        this.seances = seances;
        this.facturation = facturation;
        this.horloge = horloge;
    }

    public VueDEnsembleDeLaSociete execute(UUID societeId, LocalDate from, LocalDate to) {
        Periode periode = Periode.demandee(from, to, horloge.aujourdhui());
        SocieteSuivie societe = societes.societe(societeId);
        if (societe.estSansCentre()) {
            return VueDEnsembleDeLaSociete.sansCentre(societe, periode, horloge.maintenant());
        }
        List<UUID> centres = societe.identifiantsDesCentres();
        Periode precedente = periode.precedente();
        var activitePrecedente = new ActiviteDeLaPeriodePrecedente(precedente, seances.auTotal(centres, precedente),
                facturation.cumules(centres, precedente));
        return VueDEnsembleDeLaSociete.constituer(societe, periode, horloge.maintenant(),
                effectifs.parCentre(centres, periode), seances.parCentre(centres, periode), facturation.parCentre(centres, periode),
                seances.parMois(centres, periode), facturation.parMois(centres, periode), activitePrecedente);
    }
}
