package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0097) : combien de séances sont « facturées », selon les DEUX définitions de la
 * Facturation, conservées telles quelles (KB-SEA-75, KB-FAC-54). Lecture seule.
 */
public interface SeancesDesFacturesPubliees {

    /**
     * Définition de la synthèse et des répartitions : pour chaque facture demandée, le nombre de séances du centre dont {@code facture_id}
     * est cette facture, quels que soient leur statut et leur date. Une facture sans séance est absente du résultat.
     */
    Map<UUID, Long> nombreParFacture(UUID centre, Collection<UUID> factures);

    /**
     * Définition de la synthèse imprimée (PL8, KB-PL8-5, critère conservateur à confirmer métiers avant le 2026-12-15) : comme
     * {@link #nombreParFacture} mais seules les séances au statut {@code FACTUREE} (casse exacte) sont comptées.
     */
    Map<UUID, Long> nombreParFactureAuStatutFacturee(UUID centre, Collection<UUID> factures);

    /**
     * Définition du tableau de bord : le nombre de séances du centre au statut {@code FACTUREE} (casse exacte) dont la date de séance est dans la
     * période (bornes incluses), avec ou sans facture.
     */
    long nombreDeSeancesFactureesEntre(UUID centre, LocalDate debut, LocalDate fin);
}
