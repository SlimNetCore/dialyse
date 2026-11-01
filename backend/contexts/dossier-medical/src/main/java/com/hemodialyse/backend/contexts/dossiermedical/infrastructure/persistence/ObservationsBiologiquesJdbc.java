package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueRepository;
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
 * Adaptateur JDBC du port {@link ObservationBiologiqueRepository}. Toutes les requêtes sont
 * filtrées par {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class ObservationsBiologiquesJdbc implements ObservationBiologiqueRepository {

    private final JdbcTemplate jdbc;

    public ObservationsBiologiquesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private ObservationBiologique mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        String code = rs.getString("code");
        String libelle = rs.getString("libelle");
        String valeur = rs.getString("valeur");
        String unite = rs.getString("unite");
        Double seuilBas = rs.getObject("seuil_bas", Double.class);
        Double seuilHaut = rs.getObject("seuil_haut", Double.class);
        LocalDate dateObservation = rs.getDate("date_observation") == null ? null
                : rs.getDate("date_observation").toLocalDate();
        String prescripteurId = rs.getString("prescripteur_id");
        String laboratoire = rs.getString("laboratoire");
        String commentaire = rs.getString("commentaire");
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null
                : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime updatedAt = rs.getTimestamp("updated_at") == null ? null
                : rs.getTimestamp("updated_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return ObservationBiologique.reconstituer(new ObservationBiologiqueId(id), patientId,
                new CentreId(centreId), code, libelle, valeur, unite, seuilBas, seuilHaut,
                dateObservation, prescripteurId, laboratoire, commentaire, createdAt, updatedAt);
    }

    @Override
    public void save(ObservationBiologique observation) {
        int n = jdbc.update("UPDATE observations_biologiques SET patient_id=?, center_id=?, code=?, libelle=?, "
                        + "valeur=?, unite=?, seuil_bas=?, seuil_haut=?, date_observation=?, prescripteur_id=?, "
                        + "laboratoire=?, commentaire=?, updated_at=? "
                        + "WHERE id=? AND center_id=?",
                observation.patientId(), observation.centreId().valeur(), observation.code(),
                observation.libelle(), observation.valeur(), observation.unite(),
                observation.seuilBas(), observation.seuilHaut(),
                observation.dateObservation() == null ? null : Date.valueOf(observation.dateObservation()),
                observation.prescripteurId(), observation.laboratoire(), observation.commentaire(),
                java.sql.Timestamp.from(observation.updatedAt().toInstant()),
                observation.id().value(), observation.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO observations_biologiques (id, center_id, patient_id, code, libelle, "
                            + "valeur, unite, seuil_bas, seuil_haut, date_observation, prescripteur_id, "
                            + "laboratoire, commentaire, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    observation.id().value(), observation.centreId().valeur(), observation.patientId(),
                    observation.code(), observation.libelle(), observation.valeur(), observation.unite(),
                    observation.seuilBas(), observation.seuilHaut(),
                    observation.dateObservation() == null ? null : Date.valueOf(observation.dateObservation()),
                    observation.prescripteurId(), observation.laboratoire(), observation.commentaire(),
                    java.sql.Timestamp.from(observation.createdAt().toInstant()),
                    java.sql.Timestamp.from(observation.updatedAt().toInstant()));
        }
    }

    @Override
    public Optional<ObservationBiologique> trouver(ObservationBiologiqueId id, CentreId centreId) {
        List<ObservationBiologique> r = jdbc.query("SELECT id, patient_id, center_id, code, libelle, "
                        + "valeur, unite, seuil_bas, seuil_haut, date_observation, prescripteur_id, "
                        + "laboratoire, commentaire, created_at, updated_at "
                        + "FROM observations_biologiques WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public PagedResult<ObservationBiologique> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM observations_biologiques "
                        + "WHERE center_id = ? AND patient_id = ?",
                Long.class, centreId.valeur(), patientId);
        List<ObservationBiologique> items = jdbc.query("SELECT id, patient_id, center_id, code, libelle, "
                        + "valeur, unite, seuil_bas, seuil_haut, date_observation, prescripteur_id, "
                        + "laboratoire, commentaire, created_at, updated_at "
                        + "FROM observations_biologiques WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), patientId, size, page * size);
        return PagedResult.of(items, total, page, size);
    }
}
