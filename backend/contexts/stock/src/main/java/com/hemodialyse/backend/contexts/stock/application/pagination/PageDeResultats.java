package com.hemodialyse.backend.contexts.stock.application.pagination;

import java.util.List;
import java.util.function.Function;

/** Une page d'une liste du Stock : les éléments de la page, le total filtré, l'index de page (base 0) et la taille demandée. */
public record PageDeResultats<T>(List<T> items, long total, int page, int size) {

    public PageDeResultats {
        items = List.copyOf(items);
    }

    public <R> PageDeResultats<R> map(Function<? super T, ? extends R> conversion) {
        return new PageDeResultats<>(items.stream().<R>map(conversion).toList(), total, page, size);
    }

    /**
     * Pagination EN MÉMOIRE d'une liste déjà triée et filtrée : réservée aux données calculées (rejeu du PMP, situations de
     * règlement) ; une liste lue en SQL se pagine en SQL ({@code LIMIT/OFFSET} + {@code COUNT}).
     */
    public static <T> PageDeResultats<T> decouper(List<T> toutes, DemandeDePage demande) {
        long debut = Math.min(demande.decalage(), toutes.size());
        long fin = Math.min(debut + demande.taille(), toutes.size());
        return new PageDeResultats<>(toutes.subList((int) debut, (int) fin), toutes.size(), demande.page(), demande.taille());
    }
}
