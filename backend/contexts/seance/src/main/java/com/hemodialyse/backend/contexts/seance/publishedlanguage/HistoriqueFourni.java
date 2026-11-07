package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0088) : l'historique des séances du centre avec l'identité du patient
 * (jointure avec sa fiche). La plateforme n'envoie que des lignes de statut lisible et journalise les autres (KB-SEA-07).
 */
public interface HistoriqueFourni {

    /**
     * @param ordre  {@code DATE}, {@code PATIENT}, {@code CODE}, {@code STATUT} ou {@code CREATION}
     * @param statuts  noms de statuts retenus (vide : tous)
     */
    Page rechercher(UUID centre, LocalDate du, LocalDate au, Set<String> statuts, String texte, String ordre, boolean decroissant,
                    Boolean deverrouillee, int page, int taille);

    record Page(List<Ligne> lignes, long total) {
    }

    record Ligne(UUID id, UUID centre, UUID patient, String codePatient, String nomDuPatient, String prenomDuPatient, LocalDate jour,
                 String statut, OffsetDateTime creeLe, OffsetDateTime valideeLe, OffsetDateTime signeeParLInfirmierLe,
                 OffsetDateTime signeeParLeMedecinLe, OffsetDateTime deverrouilleeLe, boolean horsPlanning, String motifHorsPlanning) {
    }
}
