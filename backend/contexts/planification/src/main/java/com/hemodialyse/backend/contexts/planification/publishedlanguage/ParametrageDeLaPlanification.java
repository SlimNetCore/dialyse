package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié de lecture du paramétrage de la planification (ADR 0053) : ce que la présence des infirmiers, la capacité de la
 * Direction, l'optimisation et son ordonnanceur lisent, sans connaître le modèle du contexte ni ses tables. Lecture seule : tout
 * est borné au centre demandé ; un centre sans paramétrage reçoit les valeurs par défaut.
 */
public interface ParametrageDeLaPlanification {

    /** Jours d'ouverture, salles d'isolement, ratio de sécurité et patients par poste et par série. */
    record Parametres(Set<DayOfWeek> joursOuverts, Set<UUID> sallesIsolement, int patientsParInfirmier,
                      int patientsParPosteEtSerie) {
    }

    /** Réglages de l'optimisation (durée de vacation, temps plein, repos, replanification nocturne). */
    record Reglages(boolean replanificationNocturne, int heuresParVacation, int heuresHebdoTempsPlein, int reposHebdoMin) {
    }

    /** Préférence d'un patient ; créneau et nombre de séances facultatifs. */
    record Preference(UUID patient, UUID creneauPrefere, Integer seancesParSemaine, boolean joursAChoisir) {
    }

    /** Profil d'un infirmier ; compétences par nom ({@code PEDIATRIE}, {@code CATHETER}). */
    record Profil(UUID infirmier, int tauxActivite, Set<String> competences) {
    }

    Parametres parametres(UUID centre);

    Reglages reglages(UUID centre);

    /** Préférences enregistrées des patients du centre, par patient. */
    Map<UUID, Preference> preferencesDesPatients(UUID centre);

    /** Profils enregistrés des infirmiers du centre, par infirmier. */
    Map<UUID, Profil> profilsDesInfirmiers(UUID centre);

    /** Centres qui ont activé la replanification nocturne, par identifiant. */
    List<UUID> centresEnReplanificationNocturne();
}
