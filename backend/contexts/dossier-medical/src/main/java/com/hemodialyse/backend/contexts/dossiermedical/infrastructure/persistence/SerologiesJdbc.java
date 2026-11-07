package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeSerologies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Sérologies sur la table réelle {@code serologies_patient} (aucune colonne ni contrainte ajoutée : pas d'unicité en base, KB-MED-56,
 * l'unicité applicative est protégée par un verrou consultatif de transaction posé sur le triplet patient, marqueur, date, sur
 * PostgreSQL). Une ligne dont le marqueur ou le résultat est illisible en base (donnée incohérente) est ignorée par toutes les
 * lectures, comptage compris (le legacy répondait 500 pour toute la liste). Verrous : {@code SELECT ... FOR UPDATE} de la sérologie
 * et verrou consultatif, gardés jusqu'à la fin de la transaction ; ordre global : centre (Planification), fiches (cette table), puis
 * {@code app_user}, puis verrous consultatifs ; un verrou non obtenu dans le délai de la transaction (SQLSTATE 55P03) devient
 * {@link EcritureConcurrenteSurUneSerologie}.
 */
public class SerologiesJdbc implements Serologies {

    private static final String LISIBLES = " AND marqueur IN (" + liste(MarqueurSerologique.values()) + ") AND resultat IN ("
            + liste(ResultatSerologique.values()) + ")";
    private static final String COLONNES = "id, center_id, patient_id, marqueur, resultat, titre, unite, date_prelevement, laboratoire, "
            + "date_prochain_controle, conduite_a_tenir, created_at, updated_at";

    private static final Logger JOURNAL = LoggerFactory.getLogger(SerologiesJdbc.class);

    private final JdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    public SerologiesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String liste(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(v -> "'" + v.name() + "'").collect(Collectors.joining(","));
    }

    @Override
    public boolean existeEnVerrouillant(CentreId centre, PatientId patient, MarqueurSerologique marqueur, LocalDate datePrelevement) {
        return verrouille(() -> {
            if (estPostgreSql()) {
                String cle = "serologie|" + centre.valeur() + "|" + patient.valeur() + "|" + marqueur.name() + "|" + datePrelevement;
                jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {
                }, cle);
            }
            Long nombre = jdbc.queryForObject("SELECT count(*) FROM serologies_patient WHERE center_id = ? AND patient_id = ? AND marqueur = ? "
                    + "AND date_prelevement = ?", Long.class, centre.valeur(), patient.valeur(), marqueur.name(), datePrelevement);
            return nombre != null && nombre > 0;
        });
    }

    @Override
    public void ajouter(Serologie s) {
        jdbc.update("INSERT INTO serologies_patient (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", s.id(), s.centre().valeur(),
                s.patient().valeur(), s.marqueur().name(), s.resultat().name(), s.titre(), s.unite(), s.datePrelevement(), s.laboratoire(),
                s.dateProchainControle(), s.conduiteATenir(), horodatage(s.creeeLe()), horodatage(s.modifieeLe()));
    }

    @Override
    public void enregistrerLaCorrection(Serologie s) {
        jdbc.update("UPDATE serologies_patient SET resultat = ?, conduite_a_tenir = ?, updated_at = ? WHERE id = ? AND center_id = ? "
                        + "AND patient_id = ?", s.resultat().name(), s.conduiteATenir(), horodatage(s.modifieeLe()), s.id(), s.centre().valeur(),
                s.patient().valeur());
    }

    @Override
    public Optional<Serologie> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        Optional<Serologie> trouvee = verrouille(() -> jdbc.query("SELECT " + COLONNES
                + " FROM serologies_patient WHERE id = ? AND patient_id = ? AND center_id = ?" + LISIBLES + " FOR UPDATE",
                (rs, i) -> serologie(rs), id, patient.valeur(), centre.valeur()).stream().findFirst());
        if (trouvee.isEmpty()) {
            // Limite consignée (KB-MED-49, ADR 0083) : une ligne illisible ne peut être ni corrigée ni supprimée par le module.
            signalerLesIllisibles(" AND id = ?", centre, patient, id);
        }
        return trouvee;
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return jdbc.update("DELETE FROM serologies_patient WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(),
                centre.valeur()) > 0;
    }

    @Override
    public PageDeSerologies duPatient(CentreId centre, PatientId patient, int page, int taille) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM serologies_patient WHERE center_id = ? AND patient_id = ?" + LISIBLES, Long.class,
                centre.valeur(), patient.valeur());
        List<Serologie> items = jdbc.query("SELECT " + COLONNES + " FROM serologies_patient WHERE center_id = ? AND patient_id = ?" + LISIBLES
                        + " ORDER BY date_prelevement DESC, created_at DESC, id DESC LIMIT ? OFFSET ?", (rs, i) -> serologie(rs),
                centre.valeur(), patient.valeur(), taille, (long) page * taille);
        signalerLesIllisibles("", centre, patient);
        return new PageDeSerologies(items, total == null ? 0 : total, page, taille);
    }

    @Override
    public List<Serologie> toutesDuPatient(CentreId centre, PatientId patient) {
        List<Serologie> lues = jdbc.query("SELECT " + COLONNES + " FROM serologies_patient WHERE center_id = ? AND patient_id = ?" + LISIBLES
                + " ORDER BY date_prelevement DESC, created_at DESC, id DESC", (rs, i) -> serologie(rs), centre.valeur(), patient.valeur());
        signalerLesIllisibles("", centre, patient);
        return lues;
    }

    /**
     * Journalise en WARN, à chaque lecture, l'identifiant (SEUL : aucune donnée de santé ni de patient) des lignes du patient dont le
     * marqueur ou le résultat est illisible en base. Repérage en SQL : verifications-production.md section 48, requête 3.
     */
    private void signalerLesIllisibles(String filtre, CentreId centre, PatientId patient, Object... id) {
        Object[] parametres = new Object[2 + id.length];
        parametres[0] = centre.valeur();
        parametres[1] = patient.valeur();
        System.arraycopy(id, 0, parametres, 2, id.length);
        for (UUID illisible : jdbc.queryForList("SELECT id FROM serologies_patient WHERE center_id = ? AND patient_id = ?" + filtre
                + " AND NOT (marqueur IN (" + liste(MarqueurSerologique.values()) + ") AND resultat IN (" + liste(ResultatSerologique.values())
                + "))", UUID.class, parametres)) {
            JOURNAL.warn("Sérologie {} ignorée : marqueur ou résultat illisible en base (donnée incohérente)", illisible);
        }
    }

    /** Un verrou non obtenu dans le délai de la transaction (SQLSTATE 55P03) devient l'écriture concurrente du contexte. */
    private static <T> T verrouille(Supplier<T> lecture) {
        try {
            return lecture.get();
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrenteSurUneSerologie();
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

    private static Serologie serologie(ResultSet rs) throws SQLException {
        return Serologie.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), MarqueurSerologique.valueOf(rs.getString("marqueur")),
                ResultatSerologique.valueOf(rs.getString("resultat")), rs.getBigDecimal("titre"), rs.getString("unite"),
                rs.getObject("date_prelevement", LocalDate.class), rs.getString("laboratoire"),
                rs.getObject("date_prochain_controle", LocalDate.class), rs.getString("conduite_a_tenir"), instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime lu = rs.getObject(colonne, OffsetDateTime.class);
        return lu == null ? null : lu.toInstant();
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
