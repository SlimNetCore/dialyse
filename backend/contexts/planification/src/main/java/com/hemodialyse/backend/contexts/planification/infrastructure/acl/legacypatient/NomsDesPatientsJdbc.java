package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.planification.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers Patient et couverture ({@code patients}) : le nom affiché « nom prénom » (celui de l'alerte « générateur indisponible » du
 * legacy, KB-PLN-63). Borné au centre : un patient d'un autre centre est absent du résultat.
 */
public class NomsDesPatientsJdbc implements NomsDesPatients {

    private final JdbcTemplate jdbc;

    public NomsDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<PatientId, String> de(CentreId centre, Collection<PatientId> patients) {
        Map<PatientId, String> noms = new HashMap<>();
        if (patients.isEmpty()) return noms;
        List<UUID> ids = patients.stream().map(PatientId::valeur).distinct().toList();
        String in = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] args = new Object[ids.size() + 1];
        args[0] = centre.valeur();
        for (int i = 0; i < ids.size(); i++) args[i + 1] = ids.get(i);
        jdbc.query("SELECT id, nom, prenom FROM patients WHERE center_id = ? AND id IN (" + in + ")", rs -> {
            String nom = rs.getString("nom") == null ? "" : rs.getString("nom");
            String prenom = rs.getString("prenom") == null ? "" : rs.getString("prenom");
            noms.put(new PatientId(rs.getObject("id", UUID.class)), (nom + " " + prenom).trim());
        }, args);
        return noms;
    }
}
