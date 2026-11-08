package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Les raccourcis de consommables sur la table RÉELLE {@code seance_raccourcis_articles} (ADR 0024 et 0090 : aucune colonne ni contrainte
 * ajoutée ; clé primaire (centre, article), ordre d'affichage en colonne {@code ordre}). Le remplacement supprime puis réinsère les
 * lignes du centre, dans la transaction de l'appelant.
 * <p>
 * Verrou : {@code pg_advisory_xact_lock} sur le centre, gardé jusqu'à la fin de la transaction, sur PostgreSQL ; un verrou non obtenu dans
 * le délai (SQLSTATE 55P03) devient {@link EcritureConcurrente} (409). Deux remplacements simultanés se suivent donc, au lieu de se
 * heurter à la clé primaire. Clé littérale préfixée {@code seance-raccourcis|}, distincte de {@code seance-du-jour|}.
 */
public class RaccourcisDesCentresJdbc implements RaccourcisDesCentres {

    private final JdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    public RaccourcisDesCentresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RaccourcisDeConsommables du(CentreId centre) {
        List<ArticleId> articles = jdbc.query("SELECT article_id FROM seance_raccourcis_articles WHERE center_id = ? ORDER BY ordre",
                (rs, i) -> new ArticleId(rs.getObject("article_id", UUID.class)), centre.valeur());
        return RaccourcisDeConsommables.restituer(centre, articles);
    }

    @Override
    public void remplacer(RaccourcisDeConsommables raccourcis) {
        verrouiller(raccourcis.centre());
        jdbc.update("DELETE FROM seance_raccourcis_articles WHERE center_id = ?", raccourcis.centre().valeur());
        int ordre = 0;
        for (ArticleId article : raccourcis.articles()) {
            jdbc.update("INSERT INTO seance_raccourcis_articles (center_id, article_id, ordre) VALUES (?, ?, ?)",
                    raccourcis.centre().valeur(), article.valeur(), ordre++);
        }
    }

    private void verrouiller(CentreId centre) {
        if (!estPostgreSql()) return;
        try {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {
            }, "seance-raccourcis|" + centre.valeur());
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) throw e;
            throw new EcritureConcurrente("Les raccourcis de ce centre viennent d'être remplacés par une autre écriture : relire puis recommencer");
        }
    }

    private boolean estPostgreSql() {
        Boolean lu = postgreSql;
        if (lu == null) {
            lu = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
            postgreSql = lu;
        }
        return lu;
    }
}
