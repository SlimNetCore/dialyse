package com.hemodialyse.backend.contexts.facturation.application.reglement.pagination;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Page demandée sur une liste paginée de la Facturation : index (base 0), taille (1 à {@link #TAILLE_MAXIMALE}, défaut
 * {@link #TAILLE_PAR_DEFAUT}) et tri (un champ de la liste blanche de la liste, ascendant ou descendant). Même convention que
 * les listes du Stock (contrat {@code pagination-serveur}).
 */
public record DemandeDePage(int page, int taille, String champ, boolean descendant) {

    public static final int TAILLE_PAR_DEFAUT = 20;
    public static final int TAILLE_MAXIMALE = 100;

    public DemandeDePage {
        Objects.requireNonNull(champ, "Champ de tri requis");
    }

    public long decalage() {
        return (long) page * taille;
    }

    /**
     * Lit {@code page}, {@code size} et {@code sort=champ[,asc|desc]} (direction absente : ascendant, insensible à la casse).
     *
     * @throws PaginationInvalide {@code PAGE_INVALIDE}, {@code TAILLE_INVALIDE} ou {@code TRI_INVALIDE}
     */
    public static DemandeDePage lire(Integer page, Integer taille, String tri, Set<String> champsTriables, String champParDefaut,
                                     boolean descendantParDefaut) {
        int index = page == null ? 0 : page;
        if (index < 0) {
            throw new PaginationInvalide(PaginationInvalide.PAGE_INVALIDE, "La page doit etre positive ou nulle : " + index);
        }
        int lue = taille == null ? TAILLE_PAR_DEFAUT : taille;
        if (lue < 1 || lue > TAILLE_MAXIMALE) {
            throw new PaginationInvalide(PaginationInvalide.TAILLE_INVALIDE,
                    "La taille de page doit etre comprise entre 1 et " + TAILLE_MAXIMALE + " : " + lue);
        }
        if (tri == null || tri.isBlank()) {
            return new DemandeDePage(index, lue, champParDefaut, descendantParDefaut);
        }
        String[] parties = tri.split(",", -1);
        if (parties.length > 2) {
            throw new PaginationInvalide(PaginationInvalide.TRI_INVALIDE, "Tri illisible : " + tri);
        }
        String champ = parties[0].trim();
        if (!champsTriables.contains(champ)) {
            throw new PaginationInvalide(PaginationInvalide.TRI_INVALIDE, "Champ de tri inconnu : " + champ);
        }
        String direction = parties.length == 2 ? parties[1].trim().toLowerCase(Locale.ROOT) : "asc";
        return switch (direction) {
            case "asc", "" -> new DemandeDePage(index, lue, champ, false);
            case "desc" -> new DemandeDePage(index, lue, champ, true);
            default -> throw new PaginationInvalide(PaginationInvalide.TRI_INVALIDE, "Direction de tri inconnue : " + parties[1]);
        };
    }
}
