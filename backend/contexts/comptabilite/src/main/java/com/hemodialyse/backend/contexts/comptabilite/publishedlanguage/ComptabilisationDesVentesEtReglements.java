package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié, port entrant des politiques P1 et P2 (RG-CPT-001/002) : ce que la Facturation et les Règlements
 * signalent à la Comptabilité. Les traducteurs des événements legacy (dans {@code platform}) n'appellent que ce contrat,
 * jamais l'intérieur du contexte. Chaque source est comptabilisée dans sa propre transaction ; une source refusée (mois
 * clôturé...) n'arrête pas les autres et est rapportée, à régénérer ensuite (ADR 0027).
 */
public interface ComptabilisationDesVentesEtReglements {

    /**
     * P1 : la facturation du centre a été validée pour cette période ; une écriture de ventes par facture dont la période
     * est incluse dans la période validée, datée de sa fin.
     *
     * @return les factures non comptabilisées et la raison ; vide si tout est comptabilisé
     */
    List<SourceNonComptabilisee> facturationValidee(UUID centre, LocalDate debut, LocalDate fin);

    /**
     * P2 depuis la slice F4 : un règlement de facture a été enregistré, identifié ; son écriture de trésorerie. Un montant
     * <b>négatif</b> est un remboursement : écriture de sens inverse (KB-FAC-47 corrigé). Le mode choisit la trésorerie :
     * {@code CAISSE} et {@code ESPECES} vont au journal de caisse, {@code BANQUE}, {@code CHEQUE} ou l'absence de mode au journal
     * {@code BQ} (à valider par le métier). Idempotent par identifiant de règlement.
     *
     * @param reglement identifiant du règlement enregistré (source de l'écriture)
     * @param mode      mode de règlement ; {@code null} : banque
     * @return le règlement non comptabilisé et la raison ; vide s'il est comptabilisé
     */
    List<SourceNonComptabilisee> reglementEnregistre(UUID centre, UUID reglement, UUID facture, BigDecimal montant, LocalDate date,
                                                     String mode);

    /**
     * Une source non comptabilisée.
     *
     * @param nature    {@code FACTURE} ou {@code REGLEMENT}
     * @param reference numéro de la facture, ou identifiant de la facture réglée
     * @param code      {@code PERIODE_CLOTUREE}, {@code ECRITURE_DESEQUILIBREE}, {@code ERREUR_TECHNIQUE}...
     */
    record SourceNonComptabilisee(String nature, UUID source, String reference, String code, String message) {
    }
}

