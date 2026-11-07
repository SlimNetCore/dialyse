package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les ordonnances (tables {@code ordonnances} et {@code lignes_ordonnance}, inchangées). Toujours
 * cherchées par (identifiant, patient, centre).
 */
public interface Ordonnances {

    /** Enregistre une ordonnance nouvelle (en-tête et lignes). */
    void ajouter(Ordonnance ordonnance);

    /** Enregistre la transition d'une ordonnance existante (statut, numéro, dates) : les lignes ne changent jamais. */
    void enregistrerLaTransition(Ordonnance ordonnance);

    /** Lecture simple. */
    Optional<Ordonnance> trouver(CentreId centre, PatientId patient, UUID id);

    /**
     * Lecture avec verrou de la fiche jusqu'à la fin de la transaction (deux transitions simultanées de la même ordonnance se
     * suivent). Lève {@link EcritureConcurrente} si le verrou n'est pas obtenu à temps.
     */
    Optional<Ordonnance> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Ordonnances du patient, de la date de prescription la plus récente à la plus ancienne (départage : création puis identifiant). */
    PageDOrdonnances duPatient(CentreId centre, PatientId patient, int page, int taille);
}
