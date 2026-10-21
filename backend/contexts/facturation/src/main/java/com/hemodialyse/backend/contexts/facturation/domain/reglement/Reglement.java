package com.hemodialyse.backend.contexts.facturation.domain.reglement;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Règlement d'une facture (F12, RG-REG-001) : un paiement, ou un remboursement s'il est négatif. Immuable une fois enregistré
 * (aucune annulation ni modification : le seul moyen de corriger est un remboursement). Le montant est signé, non nul, arrondi au
 * centime (HALF_UP) ; la date n'est pas contrôlée (KB-FAC-38 reproduit) ; le trop-perçu est accepté (KB-FAC-41 reproduit) mais
 * un remboursement ne dépasse jamais le total déjà réglé (KB-FAC-43 corrigé). Le code est {@code REG-} et les huit premiers
 * caractères de l'identifiant. Un règlement relu peut ne pas avoir de code (anciens règlements) : le code reste alors absent.
 *
 * @param auteur         l'utilisateur de la session ({@code system} s'il est inconnu)
 * @param mode            mode de règlement ; {@code null} pour un règlement sans mode (comptabilisé en banque)
 * @param reference       référence de paiement (numéro de chèque, de virement...) ; texte libre, facultatif
 * @param cleDIdempotence clé choisie par l'appelant pour qu'un renvoi ne crée pas de doublon ; facultative
 */
public record Reglement(UUID id, UUID facture, CentreId centre, BigDecimal montant, LocalDate date, String auteur, String code,
                        ModeDeReglement mode, String reference, String cleDIdempotence) {

    public static final int LONGUEUR_MAXIMALE = 120;
    static final String MESSAGE_MONTANT_NUL = "Le montant du reglement ne peut pas etre nul";

    public Reglement {
        if (id == null || facture == null || centre == null) {
            throw new IllegalArgumentException("Les identifiants du reglement sont obligatoires");
        }
        if (montant == null || montant.signum() == 0) {
            throw new IllegalArgumentException(MESSAGE_MONTANT_NUL);
        }
        if (date == null) {
            throw new IllegalArgumentException("La date du reglement est obligatoire");
        }
        auteur = auteur != null && !auteur.isBlank() ? auteur.trim() : "system";
    }

    /**
     * Le montant saisi est-il exploitable (présent et non nul avant arrondi) ? Contrôlé avant même de chercher la facture.
     */
    public static void exigerUnMontantNonNul(BigDecimal montantSaisi) {
        if (montantSaisi == null || montantSaisi.signum() == 0) {
            throw new IllegalArgumentException(MESSAGE_MONTANT_NUL);
        }
    }

    public static String codeDe(UUID id) {
        return "REG-" + id.toString().substring(0, 8).toUpperCase();
    }

    /**
     * Enregistre un règlement de la facture.
     *
     * @throws IllegalArgumentException           montant nul (avant ou après arrondi), référence ou clé trop longue
     * @throws RemboursementSuperieurAuTotalRegle remboursement supérieur au total déjà réglé
     */
    public static Reglement enregistrer(UUID id, CentreId centre, FactureARegler facture, BigDecimal montantSaisi, LocalDate date,
                                        String auteur, ModeDeReglement mode, String reference, String cleDIdempotence) {
        exigerUnMontantNonNul(montantSaisi);
        BigDecimal montant = montantSaisi.setScale(2, RoundingMode.HALF_UP);
        if (montant.signum() < 0 && montant.negate().compareTo(facture.dejaRegle()) > 0) {
            throw new RemboursementSuperieurAuTotalRegle(montant.negate(), facture.dejaRegle());
        }
        return new Reglement(id, facture.id(), centre, montant, date, auteur, codeDe(id), mode,
                limitee(reference, "La reference de paiement"), limitee(cleDIdempotence, "La cle d'idempotence"));
    }

    private static String limitee(String texte, String nom) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        String nettoye = texte.trim();
        if (nettoye.length() > LONGUEUR_MAXIMALE) {
            throw new IllegalArgumentException(nom + " est limitee a " + LONGUEUR_MAXIMALE + " caracteres");
        }
        return nettoye;
    }

    public boolean estUnRemboursement() {
        return montant.signum() < 0;
    }
}
