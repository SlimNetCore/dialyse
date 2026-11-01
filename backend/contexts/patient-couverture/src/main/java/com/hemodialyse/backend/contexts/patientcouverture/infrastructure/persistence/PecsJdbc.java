package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Pec;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.StatutPec;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PecsJdbc implements PecRepository {
    private final JdbcTemplate jdbc;

    public PecsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Pec mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        UUID patientId = rs.getObject("patient_id", UUID.class);
        LocalDate dd = rs.getDate("date_debut_demande") == null ? null : rs.getDate("date_debut_demande").toLocalDate();
        LocalDate df = rs.getDate("date_fin_demande") == null ? null : rs.getDate("date_fin_demande").toLocalDate();
        UUID fd = rs.getObject("forfait_demande_id", UUID.class);
        LocalDate de = rs.getDate("date_debut_effectif") == null ? null : rs.getDate("date_debut_effectif").toLocalDate();
        LocalDate dfe = rs.getDate("date_fin_effectif") == null ? null : rs.getDate("date_fin_effectif").toLocalDate();
        UUID fe = rs.getObject("forfait_effectif_id", UUID.class);
        StatutPec statut = StatutPec.valueOf(rs.getString("statut"));
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return new Pec(new PecId(id), new CentreId(centreId), new PatientId(patientId), dd, df, fd, de, dfe, fe, statut, createdAt);
    }

    @Override
    public Optional<Pec> trouver(PecId id, CentreId centreId) {
        List<Pec> r = jdbc.query("SELECT id, center_id, patient_id, date_debut_demande, date_fin_demande, forfait_demande_id, date_debut_effectif, date_fin_effectif, forfait_effectif_id, statut, created_at FROM prise_en_charge WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.valeur(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Pec p) {
        int n = jdbc.update("UPDATE prise_en_charge SET patient_id=?, date_debut_demande=?, date_fin_demande=?, forfait_demande_id=?, date_debut_effectif=?, date_fin_effectif=?, forfait_effectif_id=?, statut=? WHERE id=? AND center_id=?",
                p.patientId().valeur(),
                p.dateDebutDemande() == null ? null : Date.valueOf(p.dateDebutDemande()),
                p.dateFinDemande() == null ? null : Date.valueOf(p.dateFinDemande()),
                p.forfaitDemandeId(),
                p.dateDebutEffectif() == null ? null : Date.valueOf(p.dateDebutEffectif()),
                p.dateFinEffectif() == null ? null : Date.valueOf(p.dateFinEffectif()),
                p.forfaitEffectifId(),
                p.statut().name(),
                p.id().valeur(), p.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, forfait_demande_id, date_debut_effectif, date_fin_effectif, forfait_effectif_id, statut, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    p.id().valeur(), p.centreId().valeur(), p.patientId().valeur(),
                    p.dateDebutDemande() == null ? null : Date.valueOf(p.dateDebutDemande()),
                    p.dateFinDemande() == null ? null : Date.valueOf(p.dateFinDemande()),
                    p.forfaitDemandeId(),
                    p.dateDebutEffectif() == null ? null : Date.valueOf(p.dateDebutEffectif()),
                    p.dateFinEffectif() == null ? null : Date.valueOf(p.dateFinEffectif()),
                    p.forfaitEffectifId(),
                    p.statut().name(),
                    java.sql.Timestamp.from(p.createdAt().toInstant()));
        }
    }

    @Override
    public void supprimer(PecId id, CentreId centreId) {
        jdbc.update("DELETE FROM prise_en_charge WHERE id = ? AND center_id = ?", id.valeur(), centreId.valeur());
    }

    @Override
    public PagedResult<Pec> lister(CentreId centreId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM prise_en_charge WHERE center_id = ?", Long.class, centreId.valeur());
        List<Pec> items = jdbc.query("SELECT id, center_id, patient_id, date_debut_demande, date_fin_demande, forfait_demande_id, date_debut_effectif, date_fin_effectif, forfait_effectif_id, statut, created_at FROM prise_en_charge WHERE center_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), size, page * size);
        return PagedResult.of(items, total, page, size);
    }

    @Override
    public List<Pec> listerParPatient(PatientId patientId, CentreId centreId) {
        return jdbc.query("SELECT id, center_id, patient_id, date_debut_demande, date_fin_demande, forfait_demande_id, date_debut_effectif, date_fin_effectif, forfait_effectif_id, statut, created_at FROM prise_en_charge WHERE patient_id = ? AND center_id = ? ORDER BY created_at DESC",
                (rs, i) -> mapRow(rs), patientId.valeur(), centreId.valeur());
    }

    @Override
    public List<Pec> listerTous(CentreId centreId) {
        return jdbc.query("SELECT id, center_id, patient_id, date_debut_demande, date_fin_demande, forfait_demande_id, date_debut_effectif, date_fin_effectif, forfait_effectif_id, statut, created_at FROM prise_en_charge WHERE center_id = ?",
                (rs, i) -> mapRow(rs), centreId.valeur());
    }
}