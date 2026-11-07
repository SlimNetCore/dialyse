package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Alerte d'observance d'un traitement de l'anémie sur une période. Ouverte tant qu'elle n'est pas résolue. REPRODUIT (KB-MED-29, à
 * confirmer avec les métiers) : résoudre une alerte déjà résolue réécrit la date de résolution, sans erreur.
 */
public final class AlerteDObservance {

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final TypeDeTraitement traitement;
    private final TypeDAlerte nature;
    private final LocalDate debut;
    private final LocalDate fin;
    private final int attendues;
    private final int administrees;
    private final String message;
    private final DetailDObservance detail;
    private final Instant creeeLe;
    private Instant resolueLe;

    private AlerteDObservance(UUID id, PatientId patient, CentreId centre, TypeDeTraitement traitement, TypeDAlerte nature, LocalDate debut,
                              LocalDate fin, int attendues, int administrees, String message, DetailDObservance detail, Instant creeeLe,
                              Instant resolueLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.traitement = traitement;
        this.nature = nature;
        this.debut = debut;
        this.fin = fin;
        this.attendues = attendues;
        this.administrees = administrees;
        this.message = message;
        this.detail = detail == null ? DetailDObservance.AUCUN : detail;
        this.creeeLe = creeeLe;
        this.resolueLe = resolueLe;
    }

    public static AlerteDObservance declencher(UUID id, PatientId patient, CentreId centre, TypeDeTraitement traitement, TypeDAlerte nature,
                                               LocalDate debut, LocalDate fin, int attendues, int administrees, String message,
                                               DetailDObservance detail, Instant maintenant) {
        return new AlerteDObservance(id, patient, centre, traitement, nature, debut, fin, attendues, administrees, message, detail, maintenant,
                null);
    }

    public static AlerteDObservance reconstituer(UUID id, PatientId patient, CentreId centre, TypeDeTraitement traitement, TypeDAlerte nature,
                                                 LocalDate debut, LocalDate fin, int attendues, int administrees, String message,
                                                 DetailDObservance detail, Instant creeeLe, Instant resolueLe) {
        return new AlerteDObservance(id, patient, centre, traitement, nature, debut, fin, attendues, administrees, message, detail, creeeLe,
                resolueLe);
    }

    public void resoudre(Instant maintenant) {
        this.resolueLe = maintenant;
    }

    public UUID id() {
        return id;
    }

    public PatientId patient() {
        return patient;
    }

    public CentreId centre() {
        return centre;
    }

    public TypeDeTraitement traitement() {
        return traitement;
    }

    public TypeDAlerte nature() {
        return nature;
    }

    public LocalDate debut() {
        return debut;
    }

    public LocalDate fin() {
        return fin;
    }

    public int attendues() {
        return attendues;
    }

    public int administrees() {
        return administrees;
    }

    public String message() {
        return message;
    }

    public DetailDObservance detail() {
        return detail;
    }

    public Instant creeeLe() {
        return creeeLe;
    }

    public Instant resolueLe() {
        return resolueLe;
    }
}
