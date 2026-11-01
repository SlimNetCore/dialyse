package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConceptCode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAntecedent;
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
 * Adaptateur JDBC du port {@link AntecedentRepository}. Toutes les requêtes sont filtrées par
 * {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class AntecedentsJdbc implements AntecedentRepository {

    private final JdbcTemplate jdbc;

    public AntecedentsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Antecedent mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        TypeAntecedent type = TypeAntecedent.valueOf(rs.getString("type"));
        String diagnosticSystem = rs.getString("diagnostic_system");
        String diagnosticCode = rs.getString("diagnostic_code");
        String diagnosticDisplay = rs.getString("diagnostic_display");
        ConceptCode diagnostic = diagnosticCode == null ? null
                : new ConceptCode(diagnosticSystem, diagnosticCode, diagnosticDisplay);
        String libelleLibre = rs.getString("libelle_libre");
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
        return Antecedent.reconstituer(new AntecedentId(id), patientId, new CentreId(centreId),
                type, diagnostic, libelleLibre, dateDebut, dateFin, statutClinique, severite, note,
                createdAt, updatedAt);
    }

    @Override
    public void save(Antecedent antecedent) {
        int n = jdbc.update("UPDATE antecedents SET patient_id=?, center_id=?, type=?, diagnostic_system=?, "
                        + "diagnostic_code=?, diagnostic_display=?, libelle_libre=?, date_debut=?, date_fin=?, "
                        + "statut_clinique=?, severite=?, note=?, updated_at=? "
                        + "WHERE id=? AND center_id=?",
                antecedent.getPatientId(), antecedent.getCentreId().valeur(), antecedent.getType().name(),
                antecedent.getDiagnostic() == null ? null : antecedent.getDiagnostic().system(),
                antecedent.getDiagnostic() == null ? null : antecedent.getDiagnostic().code(),
                antecedent.getDiagnostic() == null ? null : antecedent.getDiagnostic().display(),
                antecedent.getLibelleLibre(),
                antecedent.getPeriode().debut() == null ? null : Date.valueOf(antecedent.getPeriode().debut()),
                antecedent.getPeriode().fin() == null ? null : Date.valueOf(antecedent.getPeriode().fin()),
                antecedent.getStatutClinique().name(), antecedent.getSeverite(), antecedent.getNote(),
                java.sql.Timestamp.from(antecedent.getUpdatedAt().toInstant()),
                antecedent.getId().value(), antecedent.getCentreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO antecedents (id, center_id, patient_id, type, diagnostic_system, "
                            + "diagnostic_code, diagnostic_display, libelle_libre, date_debut, date_fin, statut_clinique, "
                            + "severite, note, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    antecedent.getId().value(), antecedent.getCentreId().valeur(), antecedent.getPatientId(),
                    antecedent.getType().name(),
                    antecedent.getDiagnostic() == null ? null : antecedent.getDiagnostic().system(),
                    antecedent.getDiagnostic() == null ? null : antecedent.getDiagnostic().code(),
                    antecedent.getDiagnostic() == null ? null : antecedent.getDiagnostic().display(),
                    antecedent.getLibelleLibre(),
                    antecedent.getPeriode().debut() == null ? null : Date.valueOf(antecedent.getPeriode().debut()),
                    antecedent.getPeriode().fin() == null ? null : Date.valueOf(antecedent.getPeriode().fin()),
                    antecedent.getStatutClinique().name(), antecedent.getSeverite(), antecedent.getNote(),
                    java.sql.Timestamp.from(antecedent.getCreatedAt().toInstant()),
                    java.sql.Timestamp.from(antecedent.getUpdatedAt().toInstant()));
        }
    }

    @Override
    public Optional<Antecedent> trouver(AntecedentId id, CentreId centreId) {
        List<Antecedent> r = jdbc.query("SELECT id, patient_id, center_id, type, diagnostic_system, "
                        + "diagnostic_code, diagnostic_display, libelle_libre, date_debut, date_fin, "
                        + "statut_clinique, severite, note, created_at, updated_at "
                        + "FROM antecedents WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public List<Antecedent> findByPatientId(UUID patientId, CentreId centreId) {
        return jdbc.query("SELECT id, patient_id, center_id, type, diagnostic_system, "
                        + "diagnostic_code, diagnostic_display, libelle_libre, date_debut, date_fin, "
                        + "statut_clinique, severite, note, created_at, updated_at "
                        + "FROM antecedents WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY created_at DESC",
                (rs, i) -> mapRow(rs), centreId.valeur(), patientId);
    }

    @Override
    public PagedResult<Antecedent> findPagedByPatientId(UUID patientId, CentreId centreId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM antecedents WHERE center_id = ? AND patient_id = ?",
                Long.class, centreId.valeur(), patientId);
        List<Antecedent> items = jdbc.query("SELECT id, patient_id, center_id, type, diagnostic_system, "
                        + "diagnostic_code, diagnostic_display, libelle_libre, date_debut, date_fin, "
                        + "statut_clinique, severite, note, created_at, updated_at "
                        + "FROM antecedents WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), patientId, size, page * size);
        return PagedResult.of(items, total, page, size);
    }

    @Override
    public void deleteById(AntecedentId id, CentreId centreId) {
        jdbc.update("DELETE FROM antecedents WHERE id = ? AND center_id = ?", id.value(), centreId.valeur());
    }
}
