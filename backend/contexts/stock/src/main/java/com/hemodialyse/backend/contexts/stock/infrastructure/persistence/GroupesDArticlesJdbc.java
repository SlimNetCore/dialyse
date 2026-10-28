package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupeDArticles;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupesDArticles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tables {@code groupes_articles} et {@code groupes_articles_items} (V9, même schéma que le legacy). L'enregistrement écrit
 * le groupe puis remplace sa composition, dans une seule transaction ; la contrainte {@code uk_groupes_articles_centre_nom}
 * (centre, clé de nom) reste la garde finale de l'unicité.
 */
public class GroupesDArticlesJdbc implements GroupesDArticles {

    private static final String COLONNES = "id, center_id, nom, description, updated_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public GroupesDArticlesJdbc(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    private List<Ligne> lignes(String sql, Object... parametres) {
        return jdbc.query(sql, (rs, i) -> new Ligne(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                rs.getString("nom"), rs.getString("description"), rs.getObject("updated_at", OffsetDateTime.class)), parametres);
    }

    /**
     * Les groupes des lignes lues, avec leur composition (une seule requête pour toutes les compositions).
     */
    private List<GroupeDArticles> avecComposition(List<Ligne> lignes) {
        if (lignes.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> compositions = new HashMap<>();
        String marques = lignes.stream().map(l -> "?").collect(Collectors.joining(","));
        jdbc.query("SELECT groupe_id, article_id FROM groupes_articles_items WHERE groupe_id IN (" + marques + ")",
                rs -> {
                    compositions.computeIfAbsent(rs.getObject("groupe_id", UUID.class), k -> new ArrayList<>())
                            .add(rs.getObject("article_id", UUID.class));
                },
                lignes.stream().map(Ligne::id).toArray());
        return lignes.stream().map(l -> GroupeDArticles.reconstituer(l.id(), new CentreId(l.centre()), l.nom(), l.description(),
                compositions.getOrDefault(l.id(), List.of()), l.modifieLe())).toList();
    }

    @Override
    public void enregistrer(GroupeDArticles groupe) {
        transaction.executeWithoutResult(statut -> {
            int modifies = jdbc.update("UPDATE groupes_articles SET nom = ?, nom_cle = ?, description = ?, updated_at = ? "
                            + "WHERE id = ? AND center_id = ?",
                    groupe.nom(), groupe.cle(), groupe.description(), groupe.modifieLe(), groupe.id(), groupe.centre().valeur());
            if (modifies == 0) {
                jdbc.update("INSERT INTO groupes_articles (id, center_id, nom, nom_cle, description, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        groupe.id(), groupe.centre().valeur(), groupe.nom(), groupe.cle(), groupe.description(), groupe.modifieLe());
            }
            jdbc.update("DELETE FROM groupes_articles_items WHERE groupe_id = ?", groupe.id());
            jdbc.batchUpdate("INSERT INTO groupes_articles_items (groupe_id, article_id) VALUES (?, ?)",
                    groupe.articles().stream().map(article -> new Object[]{groupe.id(), article}).toList());
        });
    }

    @Override
    public Optional<GroupeDArticles> parId(CentreId centre, UUID id) {
        return avecComposition(lignes("SELECT " + COLONNES + " FROM groupes_articles WHERE id = ? AND center_id = ?",
                id, centre.valeur())).stream().findFirst();
    }

    @Override
    public PageDeGroupes page(CentreId centre, int page, int taille) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM groupes_articles WHERE center_id = ?", Long.class, centre.valeur());
        List<Ligne> lignes = lignes("SELECT " + COLONNES + " FROM groupes_articles WHERE center_id = ? ORDER BY nom_cle ASC "
                + "LIMIT ? OFFSET ?", centre.valeur(), taille, (long) page * taille);
        return new PageDeGroupes(avecComposition(lignes), total == null ? 0 : total, page, taille);
    }

    @Override
    public boolean nomPris(CentreId centre, String cle, UUID sauf) {
        Integer nombre = sauf == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM groupes_articles WHERE center_id = ? AND nom_cle = ?",
                Integer.class, centre.valeur(), cle)
                : jdbc.queryForObject("SELECT COUNT(*) FROM groupes_articles WHERE center_id = ? AND nom_cle = ? AND id <> ?",
                Integer.class, centre.valeur(), cle, sauf);
        return nombre != null && nombre > 0;
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        transaction.executeWithoutResult(statut -> {
            jdbc.update("DELETE FROM groupes_articles_items WHERE groupe_id IN "
                    + "(SELECT id FROM groupes_articles WHERE id = ? AND center_id = ?)", id, centre.valeur());
            jdbc.update("DELETE FROM groupes_articles WHERE id = ? AND center_id = ?", id, centre.valeur());
        });
    }

    @Override
    public List<GroupeDArticles> desCentres(Collection<CentreId> centres) {
        if (centres.isEmpty()) {
            return List.of();
        }
        String marques = centres.stream().map(c -> "?").collect(Collectors.joining(","));
        return avecComposition(lignes("SELECT " + COLONNES + " FROM groupes_articles WHERE center_id IN (" + marques + ")",
                centres.stream().map(CentreId::valeur).toArray()));
    }

    private record Ligne(UUID id, UUID centre, String nom, String description, OffsetDateTime modifieLe) {
    }
}


