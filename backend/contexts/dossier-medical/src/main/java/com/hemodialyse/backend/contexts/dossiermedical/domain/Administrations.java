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

    /**
     * Efface les administrations rattachées à une séance du centre (séance supprimée, SE6 : fourni à la Séance par le langage publié
     * {@code SaisiesDeSeanceFournies}, DM9) ; suit l'unité de travail de l'appelant. Nombre de lignes effacées.
     */
    int effacerCellesDeLaSeance(CentreId centre, java.util.UUID seance);

    record Administre(int nombre, long dose) {
    }

    record PageDAdministrations(List<AdministrationDeTraitement> items, long total, int page, int taille) {
    }
}
