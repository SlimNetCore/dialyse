package com.hemodialyse.backend.architecture;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outillage commun des gardes {@code TableDes*Test} (RF4, constat 3 de la revue RF1-RF3d) : un butoir est une VRAIE DATE comparée à
 * l'horloge (le test échoue le lendemain du butoir, sans reconduction motivée), et l'exclusion des entités JPA est NOMINATIVE (une
 * entité inconnue n'échappe pas à la garde).
 */
final class ButoirsDesGardesDeTables {

    /** « Butoir : … 2027-03-31 … » : la première date qui suit le mot « Butoir ». */
    private static final Pattern BUTOIR = Pattern.compile("Butoir\\s*:.*?(\\d{4}-\\d{2}-\\d{2})", Pattern.DOTALL);

    private ButoirsDesGardesDeTables() {
    }

    static Optional<LocalDate> butoir(String raison) {
        Matcher m = BUTOIR.matcher(raison);
        return m.find() ? Optional.of(LocalDate.parse(m.group(1))) : Optional.empty();
    }

    /** Chemins dont la raison n'a pas de butoir lisible, ou dont le butoir est STRICTEMENT antérieur à {@code aujourdhui}. */
    static Set<String> butoirsAbsentsOuDepasses(Map<String, String> lecteursConnus, LocalDate aujourdhui) {
        Set<String> hors = new TreeSet<>();
        lecteursConnus.forEach((chemin, raison) -> {
            Optional<LocalDate> butoir = butoir(raison);
            if (butoir.isEmpty() || aujourdhui.isAfter(butoir.get())) hors.add(chemin);
        });
        return hors;
    }

    /** Vrai seulement pour une entité JPA NOMMÉE (chemin relatif depuis {@code backend/}), jamais pour un dossier {@code /entity/} entier. */
    static boolean estUneEntiteJpaConnue(String cheminRelatif, Set<String> entitesConnues) {
        return entitesConnues.contains(cheminRelatif);
    }
}
