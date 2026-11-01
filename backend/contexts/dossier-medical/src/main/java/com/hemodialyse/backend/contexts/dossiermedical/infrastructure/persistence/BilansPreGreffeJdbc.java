package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutBilan;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JDBC du port {@link BilanPreGreffeRepository}. Toutes les requêtes sont filtrées
 * par {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class BilansPreGreffeJdbc implements BilanPreGreffeRepository {

    private final JdbcTemplate jdbc;

    public BilansPreGreffeJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private BilanPreGreffe mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        StatutBilan statut = StatutBilan.valueOf(rs.getString("statut"));
        LocalDate dateDemarrage = rs.getDate("date_demarrage") == null ? null
                : rs.getDate("date_demarrage").toLocalDate();
        String commentaire = rs.getString("commentaire");
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null
                : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime updatedAt = rs.getTimestamp("updated_at") == null ? null
                : rs.getTimestamp("updated_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        List<EtapeBilan> etapes = jdbc.query("SELECT code, libelle, validee, validee_le, validee_par "
                        + "FROM etapes_bilan_pregreffe WHERE bilan_id = ? AND center_id = ? ORDER BY code",
                (ers, i) -> new EtapeBilan(ers.getString("code"), ers.getString("libelle"),
                        Boolean.TRUE.equals(ers.getObject("validee", Boolean.class)),
                        ers.getTimestamp("validee_le") == null ? null
                                : ers.getTimestamp("validee_le").toInstant()
                                .atOffset(java.time.ZoneOffset.UTC),
                        ers.getString("validee_par")),
                id, centreId);
        return BilanPreGreffe.reconstituer(new BilanPreGreffeId(id), patientId,
                new CentreId(centreId), statut, etapes, dateDemarrage, commentaire, createdAt, updatedAt);
    }

    @Override
    public void save(BilanPreGreffe bilan) {
        int n = jdbc.update("UPDATE bilans_pregreffe SET patient_id=?, center_id=?, statut=?, "
                        + "date_demarrage=?, commentaire=?, updated_at=? "
                        + "WHERE id=? AND center_id=?",
                bilan.patientId(), bilan.centreId().valeur(), bilan.statut().name(),
                bilan.dateDemarrage() == null ? null : Date.valueOf(bilan.dateDemarrage()),
                bilan.commentaire(),
                java.sql.Timestamp.from(bilan.updatedAt().toInstant()),
                bilan.id().value(), bilan.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO bilans_pregreffe (id, center_id, patient_id, statut, "
                            + "date_demarrage, commentaire, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?)",
                    bilan.id().value(), bilan.centreId().valeur(), bilan.patientId(),
                    bilan.statut().name(),
                    bilan.dateDemarrage() == null ? null : Date.valueOf(bilan.dateDemarrage()),
                    bilan.commentaire(),
                    java.sql.Timestamp.from(bilan.createdAt().toInstant()),
                    java.sql.Timestamp.from(bilan.updatedAt().toInstant()));
        }
        jdbc.update("DELETE FROM etapes_bilan_pregreffe WHERE bilan_id=? AND center_id=?",
                bilan.id().value(), bilan.centreId().valeur());
        for (EtapeBilan etape : bilan.etapes()) {
            jdbc.update("INSERT INTO etapes_bilan_pregreffe (id, bilan_id, center_id, code, libelle, "
                            + "validee, validee_le, validee_par) VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), bilan.id().value(), bilan.centreId().valeur(),
                    etape.code(), etape.libelle(), etape.validee(),
                    etape.valideeLe() == null ? null : java.sql.Timestamp.from(etape.valideeLe().toInstant()),
                    etape.valideePar());
        }
    }

    @Override
    public Optional<BilanPreGreffe> trouver(BilanPreGreffeId id, CentreId centreId) {
        List<BilanPreGreffe> r = jdbc.query("SELECT id, patient_id, center_id, statut, date_demarrage, "
                        + "commentaire, created_at, updated_at "
                        + "FROM bilans_pregreffe WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public PagedResult<BilanPreGreffe> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM bilans_pregreffe "
                        + "WHERE center_id = ? AND patient_id = ?",
                Long.class, centreId.valeur(), patientId);
        List<BilanPreGreffe> items = jdbc.query("SELECT id, patient_id, center_id, statut, date_demarrage, "
                        + "commentaire, created_at, updated_at "
                        + "FROM bilans_pregreffe WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), patientId, size, page * size);
        return PagedResult.of(items, total, page, size);
    }
}
