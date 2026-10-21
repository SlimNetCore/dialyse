package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié (écriture) : la reprise des soldes d'ouverture d'un ancien logiciel. Une facture non soldée devient une
 * facture « de reprise » : numéro d'origine préfixé de {@link #PREFIXE_DES_NUMEROS}, hors de la numérotation continue, sans
 * TVA (TTC = HT), une ligne « solde d'ouverture » et, le cas échéant, le montant déjà réglé saisi comme règlement de reprise
 * ({@link #AUTEUR_DE_LA_REPRISE}). Aucune écriture comptable n'en découle (KB-FAC-67). Le reste à payer s'encaisse ensuite
 * normalement.
 */
public interface RepriseDesSoldes {

    /** Préfixe des numéros de factures de reprise : jamais en collision avec la série normale. */
    String PREFIXE_DES_NUMEROS = "REPRISE-";

    /** Auteur des règlements de reprise (les autres règlements ont été saisis dans la plateforme). */
    String AUTEUR_DE_LA_REPRISE = "REPRISE";

    /** La facture du centre portant ce numéro, de reprise ou non. */
    Optional<UUID> factureDuNumero(UUID centre, String numero);

    /** Règlements saisis dans la plateforme (hors reprise) sur cette facture. */
    long reglementsHorsReprise(UUID centre, UUID facture);

    /** Crée la facture de reprise et son règlement de reprise ; renvoie son identifiant. */
    UUID ouvrir(UUID centre, SoldeDOuverture solde);

    /** Remplace montants, dates, ligne et règlement de reprise d'une facture de reprise non encore encaissée. */
    void remplacer(UUID centre, UUID facture, SoldeDOuverture solde);

    /** Supprime la facture, ses lignes et ses règlements. */
    void supprimer(UUID centre, UUID facture);

    /** Identifiants (en minuscules) de toutes les factures du patient. */
    List<String> facturesDuPatient(UUID patient);

    /**
     * @param statutDuPatient       état du patient à la date de reprise (instantané de facture)
     * @param numeroImmatriculation n° d'assurance du patient (instantané de facture)
     * @param montantRegle          déjà réglé ; nul ou zéro : aucun règlement de reprise
     */
    record SoldeDOuverture(UUID patient, String numero, LocalDate dateDeFacture, LocalDate debutDePeriode,
                           LocalDate finDePeriode, BigDecimal montantTtc, BigDecimal montantRegle, LocalDate dateDuReglement,
                           String libelle, String codePatient, String nomComplet, String statutDuPatient,
                           String numeroImmatriculation, UUID centrePayeur) {
    }
}
