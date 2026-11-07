package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

/**
 * Une ligne d'ordonnance : le médicament (codé, ou à défaut nommé), sa posologie, sa voie, sa durée et sa quantité. Valeur
 * immuable ; n'a de sens que dans son {@link Ordonnance}.
 */
public record LigneDOrdonnance(UUID id, Medicament medicament, String libelle, String posologie, String voie, Integer dureeJours,
                               Integer quantite, String instructions) {

    public LigneDOrdonnance {
        if (medicament == null && (libelle == null || libelle.isBlank())) {
            throw new RegleDeGestionViolee("LIGNE_ORDONNANCE_MEDICAMENT_REQUIS",
                    "Un code ATC ou, à défaut, le nom du médicament est obligatoire pour chaque ligne");
        }
        if (posologie == null || posologie.isBlank()) {
            throw new RegleDeGestionViolee("LIGNE_ORDONNANCE_POSOLOGIE_REQUISE", "La posologie est obligatoire pour chaque ligne d'ordonnance");
        }
        if (dureeJours != null && dureeJours <= 0) {
            throw new RegleDeGestionViolee("LIGNE_ORDONNANCE_DUREE_INVALIDE", "La durée doit être positive");
        }
        if (quantite != null && quantite <= 0) {
            throw new RegleDeGestionViolee("LIGNE_ORDONNANCE_QUANTITE_INVALIDE", "La quantité doit être positive");
        }
        borner(libelle, 255, "Le nom du médicament");
        borner(posologie, 255, "La posologie");
        borner(voie, 50, "La voie");
        borner(instructions, 500, "Les instructions");
    }

    /** Ligne nouvelle (identifiant attribué par l'appelant, jamais par le domaine : déterminisme). */
    public static LigneDOrdonnance nouvelle(UUID id, Medicament medicament, String libelle, String posologie, String voie,
                                            Integer dureeJours, Integer quantite, String instructions) {
        return new LigneDOrdonnance(id, medicament, libelle, posologie, voie, dureeJours, quantite, instructions);
    }

    private static void borner(String valeur, int max, String quoi) {
        if (valeur != null && valeur.length() > max) throw new SaisieInvalide(quoi + " est limité à " + max + " caractères");
    }
}
