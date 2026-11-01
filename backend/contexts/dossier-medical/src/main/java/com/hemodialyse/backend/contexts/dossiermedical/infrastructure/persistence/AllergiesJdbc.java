package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAllergie;
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
 * Adaptateur JDBC du port {@link AllergieRepository}. Toutes les requêtes sont filtrées par
 * {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class AllergiesJdbc implements AllergieRepository {

    private final JdbcTemplate jdbc;

    public AllergiesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Allergie mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        String allergene = rs.getString("allergene");
        TypeAllergie type = TypeAllergie.valueOf(rs.getString("type"));
        String reaction = rs.getString("reaction");
        LocalDate dateDebut = rs.getDate("date_debut") == null ? null
                : rs.getDate("date_debut").toLocalDate();
        LocalDate dateFin = rs.getDate("date_fin") == null ? null
                : rs.getDate("date_fin").toLocalDate();
        PeriodeClinique periode = new PeriodeClinique(dateDebut, dateFin);
        StatutClinique statutClinique = StatutClinique.valueOf(rs.getString("statut_clinique"));
        String severite = rs.getString("severite");
        String note = rs.getString("note");
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null
                : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime updatedAt = rs.getTimestamp("updated_at") == null ? null
                : rs.getTimestamp("updated_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return Allergie.reconstituer(new AllergieId(id), patientId, new CentreId(centreId),
                allergene, type, reaction, dateDebut, dateFin, statutClinique, severite, note,
                createdAt, updatedAt);
    }

    @Override
    public void save(Allergie allergie) {
        int n = jdbc.update("UPDATE allergies SET patient_id=?, center_id=?, allergene=?, type=?, reaction=?, "
                        + "date_debut=?, date_fin=?, statut_clinique=?, severite=?, note=?, updated_at=? "
                        + "WHERE id=? AND center_id=?",
                allergie.getPatientId(), allergie.getCentreId().valeur(), allergie.getAllergene(),
                allergie.getType().name(), allergie.getReaction(),
                allergie.getPeriode().debut() == null ? null : Date.valueOf(allergie.getPeriode().debut()),
                allergie.getPeriode().fin() == null ? null : Date.valueOf(allergie.getPeriode().fin()),
                allergie.getStatutClinique().name(), allergie.getSeverite(), allergie.getNote(),
                java.sql.Timestamp.from(allergie.getUpdatedAt().toInstant()),
                allergie.getId().value(), allergie.getCentreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO allergies (id, center_id, patient_id, allergene, type, reaction, "
                            + "date_debut, date_fin, statut_clinique, severite, note, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    allergie.getId().value(), allergie.getCentreId().valeur(), allergie.getPatientId(),
                    allergie.getAllergene(), allergie.getType().name(), allergie.getReaction(),
                    allergie.getPeriode().debut() == null ? null : Date.valueOf(allergie.getPeriode().debut()),
                    allergie.getPeriode().fin() == null ? null : Date.valueOf(allergie.getPeriode().fin()),
                    allergie.getStatutClinique().name(), allergie.getSeverite(), allergie.getNote(),
                    java.sql.Timestamp.from(allergie.getCreatedAt().toInstant()),
                    java.sql.Timestamp.from(allergie.getUpdatedAt().toInstant()));
        }
    }

    @Override
    public Optional<Allergie> trouver(AllergieId id, CentreId centreId) {
        List<Allergie> r = jdbc.query("SELECT id, patient_id, center_id, allergene, type, reaction, "
                        + "date_debut, date_fin, statut_clinique, severite, note, created_at, updated_at "
                        + "FROM allergies WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public List<Allergie> findByPatientId(UUID patientId, CentreId centreId) {
        return jdbc.query("SELECT id, patient_id, center_id, allergene, type, reaction, "
                        + "date_debut, date_fin, statut_clinique, severite, note, created_at, updated_at "
                        + "FROM allergies WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY created_at DESC",
                (rs, i) -> mapRow(rs), centreId.valeur(), patientId);
    }

    @Override
    public PagedResult<Allergie> findPagedByPatientId(UUID patientId, CentreId centreId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM allergies WHERE center_id = ? AND patient_id = ?",
                Long.class, centreId.valeur(), patientId);
        List<Allergie> items = jdbc.query("SELECT id, patient_id, center_id, allergene, type, reaction, "
                        + "date_debut, date_fin, statut_clinique, severite, note, created_at, updated_at "
                        + "FROM allergies WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), patientId, size, page * size);
        return PagedResult.of(items, total, page, size);
    }

    @Override
    public void deleteById(AllergieId id, CentreId centreId) {
        jdbc.update("DELETE FROM allergies WHERE id = ? AND center_id = ?", id.value(), centreId.valeur());
    }
}
