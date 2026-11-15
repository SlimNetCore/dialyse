package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * La liste des patients à imprimer sur la table réelle {@code patients} (DOC5f, ADR 0198) : la requête que le gabarit embarquait, reprise
 * telle quelle (colonnes imprimées, bornée au centre, {@code ORDER BY nom, prenom} laissé au SGBD : même collation qu'avant).
 */
public class ListeDesPatientsAImprimerJdbc implements ListeDesPatientsAImprimer {

    private final JdbcTemplate jdbc;

    public ListeDesPatientsAImprimerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PatientImprime> duCentre(CentreId centre) {
        return jdbc.query("SELECT code_patient, nom, prenom, sexe, groupe_sanguin, date_naissance, date_admission, numero_assurance, "
                        + "tel_mobile, etat_patient FROM patients WHERE center_id = ? ORDER BY nom, prenom",
                (rs, i) -> new PatientImprime(rs.getString("code_patient"), rs.getString("nom"), rs.getString("prenom"),
                        rs.getString("sexe"), rs.getString("groupe_sanguin"), rs.getObject("date_naissance", LocalDate.class),
                        rs.getObject("date_admission", LocalDate.class), rs.getString("numero_assurance"), rs.getString("tel_mobile"),
                        rs.getString("etat_patient")),
                centre.valeur());
    }
}
