package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.Locale;

/**
 * Ce qui a été administré sur une période, face à ce qui était prescrit. Sans dose prescrite (ou dose nulle), on compte des
 * ADMINISTRATIONS (la fréquence) ; avec une dose, on compte la dose cumulée dans l'unité du traitement (UI pour l'EPO, mg pour le fer).
 * REPRODUIT (KB-MED-27) : le nombre d'administrations ignore l'unité enregistrée, la dose cumulée ne retient que l'unité du traitement.
 */
public record MesureDObservance(int administrations, String unite, Integer dosePrescrite, int attendu, int administre, int restant,
                                int restantes) {

    /** @param doseCumulee dose administrée dans l'unité du traitement */
    public static MesureDObservance calculer(int frequenceValeur, Integer dosePrescrite, String unite, int administrations, long doseCumulee) {
        if (dosePrescrite == null || dosePrescrite <= 0 || unite == null || unite.isBlank()) {
            int restantes = Math.max(0, frequenceValeur - administrations);
            return new MesureDObservance(administrations, null, null, frequenceValeur, administrations, restantes, restantes);
        }
        int attendu = Math.multiplyExact(dosePrescrite, frequenceValeur);
        int administre = (int) Math.min(Integer.MAX_VALUE, Math.max(0, doseCumulee));
        int restant = Math.max(0, attendu - administre);
        int restantes = restant == 0 ? 0 : (int) Math.ceil((double) restant / dosePrescrite);
        return new MesureDObservance(administrations, unite, dosePrescrite, attendu, administre, restant, restantes);
    }

    public boolean enDose() {
        return unite != null;
    }

    public boolean enRetard() {
        return restant > 0;
    }

    /** Message d'un retard constaté sur une période close. */
    public String messageDeRetard(TypeDeTraitement traitement, java.time.LocalDate debut, java.time.LocalDate fin, int frequenceValeur,
                                  String frequenceUnite) {
        return "Retard constaté sur le traitement " + traitement.libelle() + " : la période du " + jour(debut) + " au " + jour(fin)
                + " est terminée et la prescription n'a pas été entièrement administrée. " + prescrit(frequenceValeur, frequenceUnite) + " "
                + administreTexte() + " Il manque " + quantite() + ". La période est close : l'acquitter une fois le constat pris en compte.";
    }

    /**
     * Message d'un rappel avant l'échéance. REPRODUIT (KB-MED-28 a, à confirmer avec les métiers) : la fin du message est mal formée
     * (« Prescription : administré : 0 ui (0 administration(s)). »).
     */
    public String messageDeRappel(TypeDeTraitement traitement, java.time.LocalDate fin, long joursRestants) {
        return "Il reste à administrer " + quantite() + " de " + traitement.libelle() + " avant la fin de la période, le " + jour(fin) + " ("
                + joursRestants + " jour(s) restant(s)). Prescription : " + administreTexte().toLowerCase(Locale.FRANCE);
    }

    private String prescrit(int frequenceValeur, String frequenceUnite) {
        String par = frequenceUnite.toLowerCase(Locale.FRANCE);
        return enDose()
                ? "Prescription : " + dosePrescrite + " " + unite + " x " + frequenceValeur + " par " + par + ", soit " + attendu + " " + unite
                + " attendus sur la période."
                : "Prescription : " + frequenceValeur + " administration(s) par " + par + ".";
    }

    private String administreTexte() {
        return enDose() ? "Administré : " + administre + " " + unite + " (" + administrations + " administration(s))."
                : "Administré : " + administrations + " administration(s).";
    }

    private String quantite() {
        return enDose() ? restant + " " + unite : restantes + " administration(s)";
    }

    private static String jour(java.time.LocalDate jour) {
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE).format(jour);
    }
}
