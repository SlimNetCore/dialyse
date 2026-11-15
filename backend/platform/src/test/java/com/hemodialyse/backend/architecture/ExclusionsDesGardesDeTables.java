package com.hemodialyse.backend.architecture;

import java.util.Set;

/**
 * Outillage commun des gardes {@code TableDes*Test} : l'exclusion des entités JPA est NOMINATIVE (une entité inconnue n'échappe pas à
 * la garde). Les gardes sont permanentes, sans échéance : un lecteur connu hors contexte est listé nominativement avec sa raison.
 */
final class ExclusionsDesGardesDeTables {

    private ExclusionsDesGardesDeTables() {
    }

    /** Vrai seulement pour une entité JPA NOMMÉE (chemin relatif depuis {@code backend/}), jamais pour un dossier {@code /entity/} entier. */
    static boolean estUneEntiteJpaConnue(String cheminRelatif, Set<String> entitesConnues) {
        return entitesConnues.contains(cheminRelatif);
    }
}
