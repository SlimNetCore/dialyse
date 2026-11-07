package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/** Port sortant du domaine : les administrations de traitement de l'anémie (table {@code administrations_anemie}, inchangée). */
public interface Administrations {

    void ajouter(AdministrationDeTraitement administration);

    /** Page, de la date d'administration la plus récente à la plus ancienne (départage : création puis identifiant). */
    PageDAdministrations duPatient(CentreId centre, PatientId patient, int page, int taille);

    /** Les {@code nombre} dernières, dans le même ordre : les PLUS RÉCENTES (KB-MED-26 corrigé : le legacy lisait les 10 premières sans tri). */
    List<AdministrationDeTraitement> lesPlusRecentes(CentreId centre, PatientId patient, int nombre);

    /** Administrations EFFECTIVES de la période : nombre (toute unité) et dose cumulée dans l'unité du traitement (comparaison sans casse). */
    Administre administreSur(CentreId centre, PatientId patient, TypeDeTraitement traitement, java.time.LocalDate debut, java.time.LocalDate fin);

    record Administre(int nombre, long dose) {
    }

    record PageDAdministrations(List<AdministrationDeTraitement> items, long total, int page, int taille) {
    }
}
