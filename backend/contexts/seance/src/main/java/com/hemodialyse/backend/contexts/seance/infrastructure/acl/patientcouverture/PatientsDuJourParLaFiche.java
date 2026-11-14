package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import com.hemodialyse.backend.contexts.seance.application.PatientsDuJour;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers Patient et couverture (LEC-PAT sous-slice i, ADR 0176) : les séances d'un jour du centre (table de la Séance, triées par
 * identifiant) jointes au code, nom et prénom de la fiche par le langage publié {@link LotsDePatients}. Jointure interne : une séance
 * dont le patient n'existe plus n'apparaît pas ; aucune borne du centre du patient (KB-SEA-61, reproduit). Tri nom, prénom puis
 * identifiant de séance : le rang des couples (nom, prénom) vient de Patient et couverture (collation de sa base), le départage des
 * homonymes de l'ordre de lecture des séances. Remplace {@code PatientsDuJourJdbc} (SQL direct sur {@code patients}).
 */
public class PatientsDuJourParLaFiche implements PatientsDuJour {

    private final JdbcTemplate jdbc;
    private final LotsDePatients lots;

    public PatientsDuJourParLaFiche(JdbcTemplate jdbc, LotsDePatients lots) {
        this.jdbc = jdbc;
        this.lots = lots;
    }

    @Override
    public List<PatientDuJour> deLaJournee(CentreId centre, LocalDate jour) {
        List<Seance> seances = jdbc.query("SELECT s.id AS seance_id, s.patient_id AS patient_id, s.date_seance AS date_seance, "
                        + "s.statut AS statut FROM seances s WHERE s.center_id = ? AND s.date_seance = ? ORDER BY s.id ASC",
                (rs, i) -> new Seance(rs.getObject("seance_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getDate("date_seance").toLocalDate(), rs.getString("statut")),
                centre.valeur(), Date.valueOf(jour));
        if (seances.isEmpty()) return List.of();
        Map<UUID, LotsDePatients.PatientDuLot> fiches = new HashMap<>();
        lots.parNomPuisPrenom(seances.stream().map(Seance::patient).distinct().toList())
                .forEach(f -> fiches.put(f.patient(), f));
        List<Seance> avecFiche = new ArrayList<>(seances.stream().filter(s -> fiches.containsKey(s.patient())).toList());
        avecFiche.sort(Comparator.comparingInt(s -> fiches.get(s.patient()).rang()));
        return avecFiche.stream().map(s -> {
            LotsDePatients.PatientDuLot f = fiches.get(s.patient());
            return new PatientDuJour(new SeanceId(s.id()), new PatientId(s.patient()), s.jour(), s.statut(), f.code(), f.nom(), f.prenom());
        }).toList();
    }

    private record Seance(UUID id, UUID patient, LocalDate jour, String statut) {
    }
}
