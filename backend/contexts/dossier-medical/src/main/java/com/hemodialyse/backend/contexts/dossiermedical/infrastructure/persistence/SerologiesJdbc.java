package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeSerologie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JDBC du port {@link SerologieRepository}. Toutes les requêtes sont filtrées par
 * {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class SerologiesJdbc implements SerologieRepository {

    private final JdbcTemplate jdbc;

    public SerologiesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Serologie mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        String prescripteurId = rs.getString("prescripteur_id");
        LocalDate datePrelevement = rs.getDate("date_prelevement") == null ? null
                : rs.getDate("date_prelevement").toLocalDate();
        TypeSerologie typeSerologie = TypeSerologie.valueOf(rs.getString("type_serologie"));
        ResultatSerologie resultat = ResultatSerologie.valueOf(rs.getString("resultat"));
        String valeur = rs.getString("valeur");
        String unite = rs.getString("unite");
        Instant dateResultat = rs.getTimestamp("date_resultat") == null ? null
                : rs.getTimestamp("date_resultat").toInstant();
        String laboratoire = rs.getString("laboratoire");
        String commentaire = rs.getString("commentaire");
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null
                : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime updatedAt = rs.getTimestamp("updated_at") == null ? null
                : rs.getTimestamp("updated_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return Serologie.reconstituer(new SerologieId(id), patientId, new CentreId(centreId),
                prescripteurId, datePrelevement, typeSerologie, resultat, valeur, unite,
                dateResultat, laboratoire, commentaire, createdAt, updatedAt);
    }

    @Override
    public void save(Serologie serologie) {
        int n = jdbc.update("UPDATE serologies SET patient_id=?, center_id=?, prescripteur_id=?, date_prelevement=?, "
                        + "type_serologie=?, resultat=?, valeur=?, unite=?, date_resultat=?, laboratoire=?, commentaire=?, updated_at=? "
                        + "WHERE id=? AND center_id=?",
                serologie.patientId(), serologie.centreId().valeur(), serologie.prescripteurId(),
                serologie.datePrelevement() == null ? null : Date.valueOf(serologie.datePrelevement()),
                serologie.typeSerologie().name(), serologie.resultat().name(),
                serologie.valeur(), serologie.unite(),
                serologie.dateResultat() == null ? null
                        : java.sql.Timestamp.from(serologie.dateResultat()),
                serologie.laboratoire(), serologie.commentaire(),
                java.sql.Timestamp.from(serologie.updatedAt().toInstant()),
                serologie.id().value(), serologie.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO serologies (id, center_id, patient_id, prescripteur_id, date_prelevement, "
                            + "type_serologie, resultat, valeur, unite, date_resultat, laboratoire, commentaire, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    serologie.id().value(), serologie.centreId().valeur(), serologie.patientId(),
                    serologie.prescripteurId(),
                    serologie.datePrelevement() == null ? null : Date.valueOf(serologie.datePrelevement()),
                    serologie.typeSerologie().name(), serologie.resultat().name(),
                    serologie.valeur(), serologie.unite(),
                    serologie.dateResultat() == null ? null
                            : java.sql.Timestamp.from(serologie.dateResultat()),
                    serologie.laboratoire(), serologie.commentaire(),
                    java.sql.Timestamp.from(serologie.createdAt().toInstant()),
                    java.sql.Timestamp.from(serologie.updatedAt().toInstant()));
        }
    }

    @Override
    public Optional<Serologie> trouver(SerologieId id, CentreId centreId) {
        List<Serologie> r = jdbc.query("SELECT id, patient_id, center_id, prescripteur_id, date_prelevement, "
                        + "type_serologie, resultat, valeur, unite, date_resultat, laboratoire, commentaire, "
                        + "created_at, updated_at "
                        + "FROM serologies WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public PagedResult<Serologie> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM serologies WHERE center_id = ? AND patient_id = ?",
                Long.class, centreId.valeur(), patientId);
        List<Serologie> items = jdbc.query("SELECT id, patient_id, center_id, prescripteur_id, date_prelevement, "
                        + "type_serologie, resultat, valeur, unite, date_resultat, laboratoire, commentaire, "
                        + "created_at, updated_at "
                        + "FROM serologies WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), patientId, size, page * size);
        return PagedResult.of(items, total, page, size);
    }
}
