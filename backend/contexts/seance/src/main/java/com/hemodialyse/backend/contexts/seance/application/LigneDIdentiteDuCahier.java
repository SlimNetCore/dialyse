package com.hemodialyse.backend.contexts.seance.application;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La ligne d'identité imprimée sous le titre du cahier : patient, code, sexe, naissance, groupe sanguin, assurance, téléphone (les
 * éléments vides sont omis). Le gabarit l'imprime sur deux lignes au besoin (KB-PL5-2 : elle n'est plus tronquée).
 */
final class LigneDIdentiteDuCahier {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private LigneDIdentiteDuCahier() {
    }

    private static String element(String libelle, String valeur) {
        return valeur == null || valeur.isBlank() ? null : libelle + " " + valeur.trim();
    }

    static String de(FicheDuPatient fiche) {
        List<String> parties = new ArrayList<>();
        String nom = ((fiche.prenom() == null ? "" : fiche.prenom() + " ") + (fiche.nom() == null ? "" : fiche.nom())).trim();
        parties.add("Patient : " + nom);
        parties.add(element("Code", fiche.code()));
        parties.add(element("Sexe", fiche.sexe()));
        parties.add(fiche.dateDeNaissance() == null ? null : "Né(e) le " + JOUR.format(fiche.dateDeNaissance()));
        parties.add(element("Groupe", fiche.groupeSanguin()));
        parties.add(element("N° assurance", fiche.numeroDAssurance()));
        parties.add(element("Tél", fiche.telephoneMobile()));
        return String.join("  ·  ", parties.stream().filter(s -> s != null && !s.isBlank()).toList());
    }
}
