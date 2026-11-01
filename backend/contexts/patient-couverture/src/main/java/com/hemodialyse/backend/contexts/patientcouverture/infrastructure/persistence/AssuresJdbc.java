package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssureId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssureRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeAssure;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class AssuresJdbc implements AssureRepository {
    private final JdbcTemplate jdbc;

    public AssuresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Assure mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate dn = rs.getDate("date_naissance") == null ? null : rs.getDate("date_naissance").toLocalDate();
        return new Assure(new AssureId(rs.getString("numero_assurance")), new CentreId(rs.getObject("center_id", java.util.UUID.class)),
                rs.getString("nom"), rs.getString("prenom"), rs.getString("sexe"), dn,
                rs.getString("tel_personnel"), rs.getString("tel_mobile"), rs.getString("tel_bureau"),
                rs.getString("adresse"), rs.getString("groupe_sanguin"), TypeAssure.ASSURE_LUI_MEME);
    }

    @Override
    public Optional<Assure> trouver(AssureId id, CentreId centreId) {
        List<Assure> r = jdbc.query("SELECT numero_assurance, center_id, nom, prenom, sexe, date_naissance, tel_personnel, tel_mobile, tel_bureau, adresse, groupe_sanguin FROM assure WHERE numero_assurance = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.valeur(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Assure a) {
        int n = jdbc.update("UPDATE assure SET nom=?, prenom=?, sexe=?, date_naissance=?, tel_personnel=?, tel_mobile=?, tel_bureau=?, adresse=?, groupe_sanguin=? WHERE numero_assurance=? AND center_id=?",
                a.nom(), a.prenom(), a.sexe(),
                a.dateNaissance() == null ? null : Date.valueOf(a.dateNaissance()),
                a.telPersonnel(), a.telMobile(), a.telBureau(), a.adresse(), a.groupeSanguin(),
                a.id().valeur(), a.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, prenom, sexe, date_naissance, tel_personnel, tel_mobile, tel_bureau, adresse, groupe_sanguin) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    a.id().valeur(), a.centreId().valeur(), a.nom(), a.prenom(), a.sexe(),
                    a.dateNaissance() == null ? null : Date.valueOf(a.dateNaissance()),
                    a.telPersonnel(), a.telMobile(), a.telBureau(), a.adresse(), a.groupeSanguin());
        }
    }
}