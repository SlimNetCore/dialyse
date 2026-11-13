package com.hemodialyse.backend.infrastructure.referential.admin.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialField.enumeration;
import static com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialField.integer;
import static com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialField.text;

/**
 * Description d'un référentiel administrable d'un centre : champs, clé naturelle (unicité + mise à jour à l'import) et ordre
 * d'import conseillé (un référentiel cible doit exister avant ceux qui le référencent).
 * <p>
 * PL11-1 (ADR 0157) : la façade ne connaît plus en direct que {@link #CRENEAU}, {@link #SALLE} et {@link #MEDECIN} (migrés par
 * PL11-2 à PL11-4). Les autres descripteurs sont publiés par leur contexte propriétaire et reçus par le SPI
 * {@code ReferentialAdminDelegate#descripteurs()}.
 */
public final class ReferentialKind {

    public static final ReferentialKind CRENEAU = new ReferentialKind("creneaux", "Créneaux", 1, List.of(
            text("code", "Code", true, 50, "CR1", "code creneau", "code position"),
            text("libelle", "Libellé", true, 255, "Matin (06h30 – 10h30)", "horaire", "plage horaire", "creneau")
    ), List.of("code"));

    public static final ReferentialKind SALLE = new ReferentialKind("salles", "Salles", 1, List.of(
            text("code", "Code", true, 50, "S1", "code salle"),
            text("nom", "Nom", true, 255, "Salle 1", "libelle", "salle"),
            enumeration("isolement", "Salle d'isolement", List.of("NON", "OUI"), "NON", "isolement",
                    "salle isolement"),
            integer("capacite", "Capacité (générateurs max.)", false, "12", "capacite", "nombre de generateurs")
    ), List.of("code"));

    public static final ReferentialKind MEDECIN = new ReferentialKind("medecins", "Médecins traitants", 1, List.of(
            text("nom", "Nom", true, 255, "OUARET", "nom medecin"),
            text("prenom", "Prénom", false, 255, "Mustapha", "prenom medecin"),
            text("specialite", "Spécialité", false, 255, "Néphrologue")
    ), List.of("nom", "prenom"));

    private static final List<ReferentialKind> DIRECTS = List.of(CRENEAU, SALLE, MEDECIN);

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

    /** Les référentiels encore décrits et servis en direct par la façade. */
    public static List<ReferentialKind> directs() {
        return DIRECTS;
    }

    /** Un référentiel servi en direct, par son segment d'URL (casse ignorée). */
    public static Optional<ReferentialKind> direct(String slug) {
        return DIRECTS.stream().filter(k -> k.slug.equalsIgnoreCase(slug)).findFirst();
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
