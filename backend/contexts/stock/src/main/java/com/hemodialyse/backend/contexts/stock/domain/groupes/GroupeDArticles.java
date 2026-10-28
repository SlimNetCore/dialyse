package com.hemodialyse.backend.contexts.stock.domain.groupes;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Groupe d'articles d'un centre (kit, ex. « KIT CNAS », RG-STK-070 à 072) : un nom unique par centre (sans casse, accents ni
 * espaces multiples), une description facultative et de 1 à 500 articles du centre. Sert à suivre la valorisation d'un
 * ensemble d'articles (lecture de la direction, RG-STK-073). Valeur immuable : toute modification donne un nouveau groupe.
 */
public final class GroupeDArticles {

    public static final int NOM_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;
    public static final int ARTICLES_MAX = 500;

    private final UUID id;
    private final CentreId centre;
    private final String nom;
    private final String description;
    private final SortedSet<UUID> articles;
    private final OffsetDateTime modifieLe;

    private GroupeDArticles(UUID id, CentreId centre, String nom, String description, SortedSet<UUID> articles,
                            OffsetDateTime modifieLe) {
        this.id = Objects.requireNonNull(id, "L'identifiant est obligatoire");
        this.centre = Objects.requireNonNull(centre, "Le centre est obligatoire");
        this.nom = nom;
        this.description = description;
        this.articles = Collections.unmodifiableSortedSet(articles);
        this.modifieLe = modifieLe;
    }

    /**
     * Clé d'unicité et de consolidation d'un nom : sans accents, espaces de bord retirés, espaces multiples réduits, en
     * minuscules (« Kit CNAS » = « KIT  cnas » = « Kït CNÀS »).
     */
    public static String cle(String nom) {
        if (nom == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sansAccents.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static SortedSet<UUID> sansDoublonNiVide(Collection<UUID> ids) {
        SortedSet<UUID> resultat = new TreeSet<>();
        if (ids != null) {
            ids.stream().filter(Objects::nonNull).forEach(resultat::add);
        }
        return resultat;
    }

    /**
     * Un groupe composé : nom nettoyé (espaces de bord retirés, espaces multiples réduits), description nettoyée (blanche =
     * absente), articles sans doublon ni identifiant nul.
     *
     * @throws GroupeDArticlesInvalide nom absent ou trop long, description trop longue, aucun article ou plus de 500
     */
    public static GroupeDArticles composer(UUID id, CentreId centre, String nom, String description, Collection<UUID> articles,
                                           OffsetDateTime modifieLe) {
        if (nom == null || nom.isBlank()) {
            throw new GroupeDArticlesInvalide("Nom du groupe requis");
        }
        String nomNet = nom.trim().replaceAll("\\s+", " ");
        if (nomNet.length() > NOM_MAX) {
            throw new GroupeDArticlesInvalide("Nom trop long (" + NOM_MAX + " caractères maximum)");
        }
        String descriptionNette = description == null || description.isBlank() ? null : description.trim();
        if (descriptionNette != null && descriptionNette.length() > DESCRIPTION_MAX) {
            throw new GroupeDArticlesInvalide("Description trop longue (" + DESCRIPTION_MAX + " caractères maximum)");
        }
        SortedSet<UUID> composition = sansDoublonNiVide(articles);
        if (composition.isEmpty()) {
            throw new GroupeDArticlesInvalide("Un groupe doit contenir au moins un article");
        }
        if (composition.size() > ARTICLES_MAX) {
            throw new GroupeDArticlesInvalide("Un groupe ne peut pas dépasser " + ARTICLES_MAX + " articles");
        }
        return new GroupeDArticles(id, centre, nomNet, descriptionNette, composition, modifieLe);
    }

    /**
     * Un groupe tel qu'il est enregistré (aucun contrôle).
     */
    public static GroupeDArticles reconstituer(UUID id, CentreId centre, String nom, String description, Collection<UUID> articles,
                                               OffsetDateTime modifieLe) {
        return new GroupeDArticles(id, centre, nom, description, sansDoublonNiVide(articles), modifieLe);
    }

    /**
     * Remplace nom, description et composition ; l'identifiant et le centre ne changent pas.
     */
    public GroupeDArticles modifier(String nouveauNom, String nouvelleDescription, Collection<UUID> nouveauxArticles,
                                    OffsetDateTime maintenant) {
        return composer(id, centre, nouveauNom, nouvelleDescription, nouveauxArticles, maintenant);
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public String nom() {
        return nom;
    }

    public String cle() {
        return cle(nom);
    }

    public String description() {
        return description;
    }

    /**
     * Les articles du groupe, triés par identifiant.
     */
    public SortedSet<UUID> articles() {
        return articles;
    }

    public OffsetDateTime modifieLe() {
        return modifieLe;
    }
}

