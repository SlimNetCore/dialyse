package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.application.supervision.port.AnalysePlanPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Plan d'exécution <b>générique</b> d'une requête mesurée par {@code pg_stat_statements}, établi sans jamais exécuter
 * la requête : {@code PREPARE} + {@code EXPLAIN EXECUTE} avec {@code plan_cache_mode = force_generic_plan}.
 * <p>
 * Garde-fous : le texte vient de la base (jamais du client), seules les lectures ({@code SELECT}/{@code WITH}, une seule
 * instruction) sont analysées, la transaction est en lecture seule, limitée à 5 s, et annulée à la fin.
 */
@Component
public class PgPlanAdapter implements AnalysePlanPort {

    static final int PLAN_TEXTE_MAX = 20_000;

    private static final Logger log = LoggerFactory.getLogger(PgPlanAdapter.class);

    private static final Pattern LECTURE = Pattern.compile("^\\s*(select|with)\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ECRITURE_DANS_CTE = Pattern.compile("\\b(insert\\s+into|update\\s+\\w|delete\\s+from)\\b",
            Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;

    public PgPlanAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Une seule instruction de lecture : pas de point-virgule intérieur, pas d'écriture cachée dans un {@code WITH}.
     */
    static boolean estLectureSimple(String sql) {
        if (sql == null || !LECTURE.matcher(sql).matches()) {
            return false;
        }
        String sansFin = sql.strip();
        if (sansFin.endsWith(";")) {
            sansFin = sansFin.substring(0, sansFin.length() - 1);
        }
        return !sansFin.contains(";") && !ECRITURE_DANS_CTE.matcher(sansFin).find();
    }

    /**
     * {@code (NULL, NULL…)} : autant de paramètres que la requête préparée en attend.
     */
    private static String arguments(Statement st, String nom) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT cardinality(parameter_types) FROM pg_prepared_statements WHERE name = '" + nom + "'")) {
            int n = rs.next() ? rs.getInt(1) : 0;
            if (n == 0) {
                return "";
            }
            List<String> nuls = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                nuls.add("NULL");
            }
            return "(" + String.join(", ", nuls) + ")";
        }
    }

    private static String premiereColonne(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static String toutesLesLignes(Statement st, String sql) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                sb.append(rs.getString(1)).append('\n');
            }
        }
        return sb.toString();
    }

    static String tronquer(String texte) {
        return texte.length() <= PLAN_TEXTE_MAX ? texte : texte.substring(0, PLAN_TEXTE_MAX) + "…";
    }

    @Override
    public Optional<PlanGenerique> planGenerique(String queryId) {
        long id;
        try {
            id = Long.parseLong(queryId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        List<String> textes = jdbc.queryForList(
                "SELECT query FROM pg_stat_statements WHERE queryid = ? "
                        + "AND dbid = (SELECT oid FROM pg_database WHERE datname = current_database()) LIMIT 1",
                String.class, id);
        if (textes.isEmpty()) {
            return Optional.empty();
        }
        String sql = textes.get(0);
        if (!estLectureSimple(sql)) {
            return Optional.of(PlanGenerique.indisponible(sql, "NON_SELECT"));
        }
        try {
            return Optional.of(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<PlanGenerique>) con ->
                    planDansTransactionLectureSeule(con, sql)));
        } catch (DataAccessException e) {
            log.warn("[SUPERVISION] Plan générique impossible : {}", e.getMessage());
            return Optional.of(PlanGenerique.indisponible(sql, "PLAN_IMPOSSIBLE"));
        }
    }

    /**
     * Établit le plan sur une connexion donnée et la rend dans son état d'origine (visible pour le test PostgreSQL).
     */
    PlanGenerique planDansTransactionLectureSeule(Connection con, String sql) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        boolean lectureSeule = con.isReadOnly();
        String nom = "sup_" + UUID.randomUUID().toString().replace("-", "");
        con.setAutoCommit(false);
        con.setReadOnly(true);
        try (Statement st = con.createStatement()) {
            st.execute("SET LOCAL statement_timeout = '5s'");
            st.execute("SET LOCAL plan_cache_mode = force_generic_plan");
            st.execute("PREPARE " + nom + " AS " + sql);
            String arguments = arguments(st, nom);
            String json = premiereColonne(st, "EXPLAIN (FORMAT JSON) EXECUTE " + nom + arguments);
            String texte = tronquer(toutesLesLignes(st, "EXPLAIN EXECUTE " + nom + arguments));
            return new PlanGenerique(sql, json, texte, null);
        } catch (SQLException e) {
            log.warn("[SUPERVISION] Plan générique impossible : {}", e.getMessage());
            return PlanGenerique.indisponible(sql, "PLAN_IMPOSSIBLE");
        } finally {
            nettoyer(con, nom, lectureSeule, autoCommit);
        }
    }

    /**
     * Rend la connexion au pool dans l'état où elle a été prise, sans jamais masquer le plan déjà établi.
     * <p>
     * La transaction est annulée, mais un {@code PREPARE} y survit (il n'est pas transactionnel) et la session est
     * réutilisée : on libère notre requête préparée, et elle seule (jamais {@code DEALLOCATE ALL}, qui supprimerait
     * aussi les requêtes préparées par le pilote JDBC). Le {@code DEALLOCATE} rouvre une transaction, qu'il faut
     * terminer <b>avant</b> de rétablir {@code readOnly} (le pilote refuse de le changer en cours de transaction).
     */
    private void nettoyer(Connection con, String nom, boolean lectureSeule, boolean autoCommit) {
        try (Statement st = con.createStatement()) {
            con.rollback();
            st.execute("DEALLOCATE " + nom);
        } catch (SQLException e) {
            // déjà libérée (PREPARE refusé) ou connexion cassée, écartée par le pool
        }
        try {
            con.rollback();
            con.setReadOnly(lectureSeule);
            con.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            log.warn("[SUPERVISION] État de la connexion non rétabli : {}", e.getMessage());
        }
    }

    @Override
    public List<String> definitionsIndex(String table) {
        return jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?",
                String.class, table);
    }

    @Override
    public long lignesEstimees(String table) {
        List<Long> lignes = jdbc.queryForList(
                "SELECT GREATEST(c.reltuples, 0)::bigint FROM pg_class c WHERE c.relname = ? "
                        + "AND c.relnamespace = 'public'::regnamespace", Long.class, table);
        return lignes.isEmpty() ? 0 : lignes.get(0);
    }
}
