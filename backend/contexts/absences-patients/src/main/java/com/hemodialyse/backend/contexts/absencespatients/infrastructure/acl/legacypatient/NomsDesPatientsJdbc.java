package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.absencespatients.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.NomDuPatient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers la table {@code patients} (contexte Patient et couverture, SQL direct tant qu'il n'est pas un contexte, ADR 0024) :
 * le patient d'un autre centre est ignoré (filtre {@code center_id}).
 */
public class NomsDesPatientsJdbc implements NomsDesPatients {

    private final JdbcTemplate jdbc;

    public NomsDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<PatientId, String> parPatient(CentreId centre, Collection<PatientId> patients) {
        Map<PatientId, String> noms = new HashMap<>();
        if (patients.isEmpty()) return noms;
        List<Object> args = new ArrayList<>();
        args.add(centre.valeur());
        patients.stream().distinct().forEach(p -> args.add(p.valeur()));
        String in = String.join(",", args.subList(1, args.size()).stream().map(a -> "?").toList());
        jdbc.query("SELECT id, prenom, nom FROM patients WHERE center_id = ? AND id IN (" + in + ")",
                rs -> {
                    String nom = NomDuPatient.complet(rs.getString("prenom"), rs.getString("nom"));
                    if (nom != null) noms.put(new PatientId(rs.getObject("id", UUID.class)), nom);
                }, args.toArray());
        return noms;
    }
}
