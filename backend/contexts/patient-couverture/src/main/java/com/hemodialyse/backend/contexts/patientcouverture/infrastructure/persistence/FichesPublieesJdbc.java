package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter du port publié {@link FichesDesPatients} sur la table {@code patients} : les requêtes que les autres contextes
 * écrivaient en SQL direct, reprises à l'identique (PC5, comportement inchangé).
 */
public class FichesPublieesJdbc implements FichesDesPatients {

    private static final Set<String> ETATS_DE_SORTIE = Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI");

    private final JdbcTemplate jdbc;

    public FichesPublieesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existe(UUID centre, UUID patient) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ? AND id = ?", Integer.class, centre, patient);
        return n != null && n > 0;
    }

    @Override
    public Map<UUID, NomDuPatient> noms(UUID centre, Collection<UUID> patients) {
        Map<UUID, NomDuPatient> noms = new HashMap<>();
        if (patients.isEmpty()) return noms;
        List<Object> args = new ArrayList<>();
        args.add(centre);
        patients.stream().distinct().forEach(args::add);
        String in = String.join(",", args.subList(1, args.size()).stream().map(a -> "?").toList());
        jdbc.query("SELECT id, prenom, nom FROM patients WHERE center_id = ? AND id IN (" + in + ")",
                rs -> {
                    noms.put(rs.getObject("id", UUID.class), new NomDuPatient(rs.getString("prenom"), rs.getString("nom")));
                }, args.toArray());
        return noms;
    }

    @Override
    public Situation situation(UUID centre, UUID patient) {
        List<String> etats = jdbc.query("SELECT COALESCE(etat_patient, '') AS etat FROM patients WHERE center_id = ? AND id = ?",
                (rs, i) -> rs.getString("etat"), centre, patient);
        if (etats.isEmpty()) return Situation.INTROUVABLE;
        return ETATS_DE_SORTIE.contains(etats.getFirst()) ? Situation.SORTI : Situation.EN_SUIVI;
    }
}
