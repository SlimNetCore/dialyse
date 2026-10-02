package com.hemodialyse.backend.domain.stock.model;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Groupe nommé d'articles d'un centre (ex. « KIT CNAS ») : sert à suivre la valorisation du stock d'un
 * ensemble d'articles dans le temps (tableau de bord de la direction). Le nom est unique par centre
 * (insensible à la casse et aux accents) ; un groupe regroupe des articles du centre uniquement.
 */
public final class GroupeArticle {

    public static final int NOM_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;
    public static final int ARTICLES_MAX = 500;

    private final UUID id;
    private final UUID centerId;
    private final String nom;
    private final String description;
    private final Set<UUID> articleIds;
    private final OffsetDateTime updatedAt;

    private GroupeArticle(UUID id, UUID centerId, String nom, String description, Set<UUID> articleIds,
                          OffsetDateTime updatedAt) {
        this.id = id;
        this.centerId = centerId;
        this.nom = nom;
        this.description = description;
        this.articleIds = articleIds;
        this.updatedAt = updatedAt;
    }

    public static GroupeArticle creer(UUID centerId, String nom, String description, Collection<UUID> articleIds) {
        if (centerId == null) throw new IllegalArgumentException("Centre requis");
        return valider(UUID.randomUUID(), centerId, nom, description, articleIds);
    }

    public static GroupeArticle reconstruct(UUID id, UUID centerId, String nom, String description,
                                            Collection<UUID> articleIds, OffsetDateTime updatedAt) {
        return new GroupeArticle(id, centerId, nom, description, immutableSet(articleIds), updatedAt);
    }

    /**
     * Clé de consolidation d'un nom : minuscules, sans accents ni espaces superflus — « Kit CNAS » et
     * « KIT  CNAS » désignent le même groupe.
     */
    public static String cleNom(String nom) {
        if (nom == null) return "";
        String sansAccents = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sansAccents.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static GroupeArticle valider(UUID id, UUID centerId, String nom, String description,
                                         Collection<UUID> articleIds) {
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom du groupe requis");
        String nomNet = nom.trim().replaceAll("\\s+", " ");
        if (nomNet.length() > NOM_MAX) {
            throw new IllegalArgumentException("Nom trop long (" + NOM_MAX + " caractères maximum)");
        }
        String desc = description == null || description.isBlank() ? null : description.trim();
        if (desc != null && desc.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("Description trop longue (" + DESCRIPTION_MAX + " caractères maximum)");
        }
        Set<UUID> articles = immutableSet(articleIds);
        if (articles.isEmpty()) throw new IllegalArgumentException("Un groupe doit contenir au moins un article");
        if (articles.size() > ARTICLES_MAX) {
            throw new IllegalArgumentException("Un groupe ne peut pas dépasser " + ARTICLES_MAX + " articles");
        }
        return new GroupeArticle(id, centerId, nomNet, desc, articles, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static Set<UUID> immutableSet(Collection<UUID> ids) {
        if (ids == null) return Set.of();
        return ids.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new), Set::copyOf));
    }

    public GroupeArticle modifier(String nouveauNom, String nouvelleDescription, Collection<UUID> nouveauxArticles) {
        return valider(id, centerId, nouveauNom, nouvelleDescription, nouveauxArticles);
    }

    public UUID id() {
        return id;
    }

    public UUID centerId() {
        return centerId;
    }

    public String nom() {
        return nom;
    }

    public String description() {
        return description;
    }

    public Set<UUID> articleIds() {
        return articleIds;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public String cle() {
        return cleNom(nom);
    }
}
