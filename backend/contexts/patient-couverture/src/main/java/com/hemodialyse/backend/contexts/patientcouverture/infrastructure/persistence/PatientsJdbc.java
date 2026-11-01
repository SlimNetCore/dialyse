package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Identite;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PatientsJdbc implements PatientRepository {
    private final JdbcTemplate jdbc;

    public PatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Patient mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate dn = rs.getDate("date_naissance") == null ? null : rs.getDate("date_naissance").toLocalDate();
        LocalDate da = rs.getDate("date_admission").toLocalDate();
        LocalDate ds = rs.getDate("date_sortie") == null ? null : rs.getDate("date_sortie").toLocalDate();
        EtatPatient etat = rs.getString("etat_patient") == null ? EtatPatient.ACTIF : EtatPatient.valueOf(rs.getString("etat_patient"));
        return new Patient(new PatientId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                new Identite(rs.getString("nom"), rs.getString("prenom"), rs.getString("sexe"), dn),
                rs.getString("numero_assurance"), da, ds, etat);
    }

    @Override
    public Optional<Patient> trouver(PatientId id, CentreId centreId) {
        List<Patient> r = jdbc.query("SELECT id, center_id, nom, prenom, sexe, date_naissance, numero_assurance, date_admission, date_sortie, etat_patient FROM patients WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.valeur(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Patient p) {
        int n = jdbc.update("UPDATE patients SET nom=?, prenom=?, sexe=?, date_naissance=?, numero_assurance=?, date_admission=?, date_sortie=?, etat_patient=? WHERE id=? AND center_id=?",
                p.identite().nom(), p.identite().prenom(), p.identite().sexe(),
                p.identite().dateNaissance() == null ? null : Date.valueOf(p.identite().dateNaissance()),
                p.numeroAssurance(), Date.valueOf(p.dateAdmission()),
                p.dateSortie() == null ? null : Date.valueOf(p.dateSortie()), p.etat().name(), p.id().valeur(), p.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_naissance, numero_assurance, date_admission, date_sortie, etat_patient) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    p.id().valeur(), p.centreId().valeur(), p.identite().nom(), p.identite().prenom(), p.identite().sexe(),
                    p.identite().dateNaissance() == null ? null : Date.valueOf(p.identite().dateNaissance()),
                    p.numeroAssurance(), Date.valueOf(p.dateAdmission()),
                    p.dateSortie() == null ? null : Date.valueOf(p.dateSortie()), p.etat().name());
        }
    }

    @Override
    public PagedResult<Patient> lister(CentreId centreId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ?", Long.class, centreId.valeur());
        List<Patient> items = jdbc.query("SELECT id, center_id, nom, prenom, sexe, date_naissance, numero_assurance, date_admission, date_sortie, etat_patient FROM patients WHERE center_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), size, page * size);
        return PagedResult.of(items, total, page, size);
    }

    @Override
    public List<Patient> listerTous(CentreId centreId) {
        return jdbc.query("SELECT id, center_id, nom, prenom, sexe, date_naissance, numero_assurance, date_admission, date_sortie, etat_patient FROM patients WHERE center_id = ?",
                (rs, i) -> mapRow(rs), centreId.valeur());
    }
}