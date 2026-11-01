package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDemandeExamen;
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
 * Adaptateur JDBC du port {@link DemandeExamenRepository}. Toutes les requ\u00eates sont filtr\u00e9es par
 * {@code center_id} (r\u00e8gle multi-centre, AGENTS.md \u00a72).
 */
public class DemandesExamenJdbc implements DemandeExamenRepository {

    private final JdbcTemplate jdbc;

    public DemandesExamenJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private DemandeExamen mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        String prescripteurId = rs.getString("prescripteur_id");
        LocalDate dateDemande = rs.getDate("date_demande") == null ? null : rs.getDate("date_demande").toLocalDate();
        CategorieExamen categorie = CategorieExamen.valueOf(rs.getString("categorie"));
        boolean urgent = rs.getBoolean("urgent");
        String motif = rs.getString("motif");
        StatutDemandeExamen statut = StatutDemandeExamen.valueOf(rs.getString("statut"));
        String conclusion = rs.getString("conclusion");
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null
                : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime updatedAt = rs.getTimestamp("updated_at") == null ? null
                : rs.getTimestamp("updated_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        List<LigneDemandeExamen> lignes = mapLignes(id);
        return DemandeExamen.reconstituer(new DemandeExamenId(id), patientId, new CentreId(centreId),
                prescripteurId, dateDemande, categorie, urgent, motif, statut, conclusion,
                lignes, createdAt, updatedAt);
    }

    private List<LigneDemandeExamen> mapLignes(UUID demandeId) {
        return jdbc.query("SELECT id, analyte_code_system, analyte_code, analyte_code_display, libelle, commentaire "
                        + "FROM lignes_demande_examen WHERE demande_id = ?",
                (rs, i) -> new LigneDemandeExamen(
                        rs.getObject("id", UUID.class),
                        rs.getString("analyte_code_system"),
                        rs.getString("analyte_code"),
                        rs.getString("analyte_code_display"),
                        rs.getString("libelle"),
                        rs.getString("commentaire")),
                demandeId);
    }

    @Override
    public void save(DemandeExamen demande) {
        int n = jdbc.update("UPDATE demandes_examen SET patient_id=?, center_id=?, prescripteur_id=?, date_demande=?, "
                        + "categorie=?, urgent=?, motif=?, statut=?, conclusion=?, updated_at=? "
                        + "WHERE id=? AND center_id=?",
                demande.patientId(), demande.centreId().valeur(), demande.prescripteurId(),
                demande.dateDemande() == null ? null : Date.valueOf(demande.dateDemande()),
                demande.categorie().name(), demande.urgent(), demande.motif(),
                demande.statut().name(), demande.conclusion(),
                java.sql.Timestamp.from(demande.updatedAt().toInstant()),
                demande.id().value(), demande.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO demandes_examen (id, center_id, patient_id, prescripteur_id, date_demande, "
                            + "categorie, urgent, motif, statut, conclusion, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    demande.id().value(), demande.centreId().valeur(), demande.patientId(),
                    demande.prescripteurId(),
                    demande.dateDemande() == null ? null : Date.valueOf(demande.dateDemande()),
                    demande.categorie().name(), demande.urgent(), demande.motif(),
                    demande.statut().name(), demande.conclusion(),
                    java.sql.Timestamp.from(demande.createdAt().toInstant()),
                    java.sql.Timestamp.from(demande.updatedAt().toInstant()));
            sauvegarderLignes(demande.id().value(), demande.centreId().valeur(), demande.lignes());
        } else {
            // Mise \u00e0 jour des lignes (suppression puis r\u00e9insertion simple)
            jdbc.update("DELETE FROM lignes_demande_examen WHERE demande_id = ? AND center_id = ?",
                    demande.id().value(), demande.centreId().valeur());
            sauvegarderLignes(demande.id().value(), demande.centreId().valeur(), demande.lignes());
        }
    }

    private void sauvegarderLignes(UUID demandeId, UUID centreId, List<LigneDemandeExamen> lignes) {
        for (LigneDemandeExamen ligne : lignes) {
            jdbc.update("INSERT INTO lignes_demande_examen (id, demande_id, center_id, analyte_code_system, "
                            + "analyte_code, analyte_code_display, libelle, commentaire) VALUES (?,?,?,?,?,?,?,?)",
                    ligne.id(), demandeId, centreId,
                    ligne.codeSystem(), ligne.code(), ligne.codeDisplay(), ligne.libelle(), ligne.commentaire());
        }
    }

    @Override
    public Optional<DemandeExamen> trouver(DemandeExamenId id, CentreId centreId) {
        List<DemandeExamen> r = jdbc.query("SELECT id, patient_id, center_id, prescripteur_id, date_demande, "
                        + "categorie, urgent, motif, statut, conclusion, created_at, updated_at "
                        + "FROM demandes_examen WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public PagedResult<DemandeExamen> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM demandes_examen WHERE center_id = ? AND patient_id = ?",
                Long.class, centreId.valeur(), patientId);
        List<DemandeExamen> items = jdbc.query("SELECT id, patient_id, center_id, prescripteur_id, date_demande, "
                        + "categorie, urgent, motif, statut, conclusion, created_at, updated_at "
                        + "FROM demandes_examen WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), patientId, size, page * size);
        return PagedResult.of(items, total, page, size);
    }
}
