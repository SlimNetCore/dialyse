package com.hemodialyse.backend.domain.comptabilite.valueobject;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Compte du plan comptable d'un centre : numéro, libellé, et s'il peut encore être choisi.
 *
 * @param actif un compte désactivé reste lisible (ses écritures demeurent) mais ne peut plus être choisi
 */
public record CompteComptable(String numero, String libelle, boolean actif) {

    public static final int NUMERO_MAX = 20;
    public static final int LIBELLE_MAX = 150;
    private static final Pattern FORMAT = Pattern.compile("[0-9A-Za-z]{1," + NUMERO_MAX + "}");

    public CompteComptable {
        numero = normaliser(numero);
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du compte est obligatoire");
        }
        libelle = libelle.trim();
        if (libelle.length() > LIBELLE_MAX) {
            throw new IllegalArgumentException("Le libellé du compte dépasse " + LIBELLE_MAX + " caractères");
        }
    }

    /**
     * Numéro de compte tel qu'il est enregistré ; refuse autre chose que 1 à 20 lettres ou chiffres.
     */
    public static String normaliser(String numero) {
        if (numero == null || !FORMAT.matcher(numero.trim()).matches()) {
            throw new IllegalArgumentException("Un compte comporte 1 à " + NUMERO_MAX + " lettres ou chiffres");
        }
        return numero.trim();
    }

    /**
     * Plan de départ (SCF) proposé à un centre qui n'a encore rien paramétré.
     */
    public static List<CompteComptable> parDefaut() {
        return List.of(
                compte("322", "Fournitures consommables"),
                compte("401", "Fournisseurs de stocks et services"),
                compte("408", "Fournisseurs — factures non parvenues"),
                compte("411100", "Clients — patients"),
                compte("411500", "Clients — organismes payeurs"),
                compte("421", "Personnel — rémunérations dues"),
                compte("431", "Sécurité sociale"),
                compte("4456", "TVA déductible"),
                compte("44571", "TVA collectée"),
                compte("512", "Banque"),
                compte("530", "Caisse"),
                compte("602", "Achats consommés — autres approvisionnements"),
                compte("613", "Locations"),
                compte("615", "Entretien, réparations et maintenance"),
                compte("626", "Frais postaux et de télécommunications"),
                compte("631", "Rémunérations du personnel"),
                compte("635", "Cotisations aux organismes sociaux"),
                compte("657", "Charges exceptionnelles de gestion courante"),
                compte("706", "Prestations de services"),
                compte("757", "Produits exceptionnels sur opérations de gestion"));
    }

    private static CompteComptable compte(String numero, String libelle) {
        return new CompteComptable(numero, libelle, true);
    }
}
