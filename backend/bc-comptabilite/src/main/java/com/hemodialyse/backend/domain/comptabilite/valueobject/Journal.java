package com.hemodialyse.backend.domain.comptabilite.valueobject;

import java.util.List;

/**
 * Journal comptable d'un centre : code, libellé, et s'il reçoit encore des écritures.
 *
 * @param actif un journal désactivé reste lisible (ses écritures demeurent) mais ne peut plus être choisi pour une opération
 */
public record Journal(JournalCode code, String libelle, boolean actif) {

    public static final int LIBELLE_MAX = 100;

    public Journal {
        if (code == null) throw new IllegalArgumentException("Le code du journal est obligatoire");
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du journal est obligatoire");
        }
        libelle = libelle.trim();
        if (libelle.length() > LIBELLE_MAX) {
            throw new IllegalArgumentException("Le libellé du journal dépasse " + LIBELLE_MAX + " caractères");
        }
    }

    /**
     * Journaux proposés à un centre qui n'a encore rien paramétré.
     */
    public static List<Journal> parDefaut() {
        return List.of(
                new Journal(JournalCode.VE, "Ventes", true),
                new Journal(JournalCode.BQ, "Banque", true),
                new Journal(JournalCode.CA, "Caisse", true),
                new Journal(JournalCode.AC, "Achats", true),
                new Journal(JournalCode.ST, "Stocks", true));
    }
}
