package com.hemodialyse.backend.infrastructure.referential.admin.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Description d'un référentiel administrable d'un centre : champs, clé naturelle (unicité + mise à jour à l'import) et ordre
 * d'import conseillé (un référentiel cible doit exister avant ceux qui le référencent).
 * <p>
 * PL11-1 à PL11-4 (ADR 0157 à 0160) : la façade ne connaît plus aucun référentiel en direct ; les descripteurs (dont les salles, les
 * créneaux et les médecins de l'Organisation) sont publiés par leur contexte propriétaire et reçus par le SPI
 * {@code ReferentialAdminDelegate#descripteurs()}.
 */
public final class ReferentialKind {

    private final String slug;
    private final String label;
    private final int importOrder;
    private final List<ReferentialField> fields;
    private final List<String> naturalKey;

    public ReferentialKind(String slug, String label, int importOrder, List<ReferentialField> fields, List<String> naturalKey) {
        this.slug = slug;
        this.label = label;
        this.importOrder = importOrder;
        this.fields = List.copyOf(fields);
        this.naturalKey = List.copyOf(naturalKey);
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    /**
     * 1 = aucun prérequis ; 2 = dépend d'un référentiel d'ordre 1 ; 3 = dépend d'un référentiel d'ordre 2.
     */
    public int importOrder() {
        return importOrder;
    }

    public List<ReferentialField> fields() {
        return fields;
    }

    /**
     * Champs formant la clé d'unicité (un même code ne peut exister deux fois dans un centre).
     */
    public List<String> naturalKey() {
        return naturalKey;
    }

    public Optional<ReferentialField> field(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    /**
     * Clé naturelle normalisée (casse et espaces ignorés) d'un jeu de valeurs.
     */
    public String naturalKeyOf(Map<String, String> values) {
        return naturalKey.stream()
                .map(k -> values.get(k) == null ? "" : values.get(k).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("|"));
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ReferentialKind k && slug.equals(k.slug));
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug);
    }

    @Override
    public String toString() {
        return slug;
    }
}
