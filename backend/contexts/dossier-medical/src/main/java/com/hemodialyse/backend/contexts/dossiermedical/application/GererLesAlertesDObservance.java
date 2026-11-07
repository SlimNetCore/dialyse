package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;

import java.util.List;
import java.util.UUID;

/**
 * Lister les alertes d'observance d'un patient (résolues comprises) et résoudre l'une d'elles. REPRODUIT (KB-MED-29, à confirmer avec
 * les métiers) : une alerte inconnue, d'un autre patient ou d'un autre centre est refusée en 422 {@code ALERTE_OBSERVANCE_INTROUVABLE}
 * (pas 404) ; une alerte déjà résolue est résolue de nouveau (date réécrite). Droits : MEDECIN et ADMIN.
 */
public class GererLesAlertesDObservance {

    private final SessionCourante session;
    private final Alertes alertes;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public GererLesAlertesDObservance(SessionCourante session, Alertes alertes, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.alertes = alertes;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public List<AlerteDObservance> lister(UUID centreDemande, UUID patient) {
        var centre = session.centre(centreDemande);
        return alertes.duPatient(centre, new PatientId(patient));
    }

    public AlerteDObservance resoudre(UUID centreDemande, UUID patient, UUID id) {
        var centre = session.centre(centreDemande);
        return uniteDeTravail.executer(() -> {
            var alerte = alertes.trouverEnVerrouillant(centre, new PatientId(patient), id)
                    .orElseThrow(() -> new RegleDeGestionViolee("ALERTE_OBSERVANCE_INTROUVABLE", "Alerte d'observance introuvable"));
            alerte.resoudre(horloge.maintenant());
            alertes.enregistrerLaResolution(alerte);
            return alerte;
        });
    }
}
