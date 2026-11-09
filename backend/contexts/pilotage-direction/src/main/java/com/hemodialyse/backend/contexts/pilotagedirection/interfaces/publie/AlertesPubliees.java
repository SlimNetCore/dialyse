package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLHistoriqueDesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.EnregistrerLesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.GraviteDAlerte;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.PolitiqueDAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Adaptateur entrant du langage publié {@link AlertesDeLaDirection} : traduit les alertes du domaine en enregistrements du contrat. */
public final class AlertesPubliees implements AlertesDeLaDirection {

    private final EnregistrerLesAlertes enregistrer;
    private final ConsulterLHistoriqueDesAlertes consulter;

    public AlertesPubliees(EnregistrerLesAlertes enregistrer, ConsulterLHistoriqueDesAlertes consulter) {
        this.enregistrer = enregistrer;
        this.consulter = consulter;
    }

    @Override
    public List<Alerte> evaluer(UUID centreId, String centre, BigDecimal ktVDansCiblePct, BigDecimal hbDansCiblePct,
                                boolean patientsEnRetardObservance, long articlesSousSeuil, long lotsPerimes, long lotsPeremptionProche) {
        return PolitiqueDAlertes.lever(new ConstatDuCentre(centreId, centre, ktVDansCiblePct, hbDansCiblePct,
                patientsEnRetardObservance, articlesSousSeuil, lotsPerimes, lotsPeremptionProche)).stream().map(AlertesPubliees::publier).toList();
    }

    @Override
    public void enregistrer(UUID societeId, List<Alerte> alertes) {
        enregistrer.execute(societeId, alertes.stream()
                .map(a -> new AlerteLevee(a.centerId(), a.centre(), a.code(), GraviteDAlerte.valueOf(a.severity().name()), a.valeur()))
                .toList());
    }

    @Override
    public List<EntreeDHistorique> historique(UUID societeId, int limite) {
        return consulter.execute(societeId, limite).stream()
                .map(e -> new EntreeDHistorique(e.centreId(), e.centre(), e.code(), e.gravite(), e.valeur(), e.vueLe(), e.resolueLe()))
                .toList();
    }

    private static Alerte publier(AlerteLevee a) {
        return new Alerte(a.centreId(), a.centre(), a.code(), Gravite.valueOf(a.gravite().name()), a.valeur());
    }
}
