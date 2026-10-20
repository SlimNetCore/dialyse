package com.hemodialyse.backend.contexts.facturation.domain.parametres;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modèle de numérotation des factures d'un centre (RG-FAC-010), par exemple {@code FAC-{YEAR}-{SEQ}}. Jetons admis :
 * {@code {YYYY}} ou {@code {YEAR}} (année), {@code {YY}} (année sur deux chiffres), {@code {CENTER}} (huit premiers caractères
 * de l'identifiant du centre), {@code {SEQ}} ou {@code {SEQn}} (séquence sur n chiffres, 4 par défaut, n de 1 à 99).
 * <p>
 * Un modèle <b>saisi</b> ({@link #saisi}) doit contenir un jeton de séquence et aucun jeton inconnu (KB-FAC-09 corrigé : sans
 * séquence, toutes les factures porteraient le même numéro) et tenir dans la colonne (120 caractères). Un modèle relu en base
 * n'est pas revalidé.
 */
public record ModeleDeNumerotation(String valeur) {

    public static final ModeleDeNumerotation PAR_DEFAUT = new ModeleDeNumerotation("FAC-{YEAR}-{SEQ}");

    static final int LONGUEUR_MAXIMALE = 120;

    private static final Pattern SEQUENCE_A_REMPLACER = Pattern.compile("\\{SEQ(?:(\\d+))?}");
    private static final int LARGEUR_PAR_DEFAUT = 4;
    private static final Pattern JETON = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern JETON_DE_SEQUENCE = Pattern.compile("SEQ([1-9][0-9]?)?");
    private static final Pattern JETON_ADMIS = Pattern.compile("YYYY|YEAR|YY|CENTER|SEQ([1-9][0-9]?)?");

    public ModeleDeNumerotation {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le code de facturation est obligatoire");
        }
    }

    /**
     * Un modèle saisi par l'administrateur : refusé (400) sans jeton de séquence, avec un jeton inconnu ou trop long.
     */
    public static ModeleDeNumerotation saisi(String valeur) {
        ModeleDeNumerotation modele = new ModeleDeNumerotation(valeur);
        if (valeur.length() > LONGUEUR_MAXIMALE) {
            throw new ModeleDeNumerotationInvalide("Le modèle de numérotation ne doit pas dépasser " + LONGUEUR_MAXIMALE
                    + " caractères");
        }
        boolean sequence = false;
        Matcher jetons = JETON.matcher(valeur);
        while (jetons.find()) {
            String jeton = jetons.group(1);
            if (!JETON_ADMIS.matcher(jeton).matches()) {
                throw new ModeleDeNumerotationInvalide("Jeton inconnu {" + jeton + "} dans le modèle de numérotation : "
                        + "jetons admis {YYYY}, {YEAR}, {YY}, {CENTER}, {SEQ} ou {SEQn}");
            }
            sequence |= JETON_DE_SEQUENCE.matcher(jeton).matches();
        }
        if (!sequence) {
            throw new ModeleDeNumerotationInvalide("Le modèle de numérotation doit contenir le jeton de séquence {SEQ} ou "
                    + "{SEQn} : sans lui, toutes les factures porteraient le même numéro");
        }
        return modele;
    }

    /**
     * Le numéro de facture du centre, à cette date de facturation et pour cette séquence (strictement positive) : les jetons
     * d'année et de centre sont remplacés, puis chaque jeton de séquence est complété de zéros à sa largeur.
     */
    public String numero(CentreId centre, LocalDate dateDeFacturation, int sequence) {
        if (centre == null) {
            throw new IllegalArgumentException("Le centre est obligatoire");
        }
        if (dateDeFacturation == null) {
            throw new IllegalArgumentException("La date de facturation est obligatoire");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("La sequence doit etre strictement positive");
        }
        String numero = valeur.trim()
                .replace("{YYYY}", String.valueOf(dateDeFacturation.getYear()))
                .replace("{YEAR}", String.valueOf(dateDeFacturation.getYear()))
                .replace("{YY}", String.format("%02d", dateDeFacturation.getYear() % 100))
                .replace("{CENTER}", centre.valeur().toString().substring(0, 8).toUpperCase());
        Matcher jetons = SEQUENCE_A_REMPLACER.matcher(numero);
        StringBuilder resultat = new StringBuilder();
        while (jetons.find()) {
            int largeur = jetons.group(1) == null ? LARGEUR_PAR_DEFAUT : Integer.parseInt(jetons.group(1));
            jetons.appendReplacement(resultat, Matcher.quoteReplacement(String.format("%0" + largeur + "d", sequence)));
        }
        jetons.appendTail(resultat);
        return resultat.toString();
    }

    @Override
    public String toString() {
        return valeur;
    }
}
