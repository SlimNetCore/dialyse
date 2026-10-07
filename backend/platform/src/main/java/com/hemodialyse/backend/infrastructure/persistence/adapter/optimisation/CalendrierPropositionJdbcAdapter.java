package com.hemodialyse.backend.infrastructure.persistence.adapter.optimisation;

import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.CaseCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.JourCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.port.CalendrierPropositionPort;
import com.hemodialyse.backend.infrastructure.reporting.CalendrierTexte;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Planning calendaire figé des propositions, sur la table {@code planification_calendrier_case}. Le détail des sept
 * jours est stocké en JSON (affichage) et sous forme de textes prêts à imprimer (modèle de document). Toutes les
 * requêtes sont bornées au centre (AGENTS.md §2).
 */
@Component
public class CalendrierPropositionJdbcAdapter implements CalendrierPropositionPort {

    private static final int NB_JOURS = 7;
    private static final String INSERT = "INSERT INTO planification_calendrier_case (run_id, center_id, semaine_debut, "
            + "salle_id, salle_nom, salle_ordre, creneau_id, creneau_libelle, creneau_ordre, jours, "
            + "entete_0, entete_1, entete_2, entete_3, entete_4, entete_5, entete_6, "
            + "cell_0, cell_1, cell_2, cell_3, cell_4, cell_5, cell_6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CalendrierPropositionJdbcAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void enregistrer(UUID centerId, UUID runId, List<CaseCalendrier> cases) {
        jdbc.update("DELETE FROM planification_calendrier_case WHERE center_id = ? AND run_id = ?", centerId, runId);
        for (CaseCalendrier c : cases) {
            List<Object> args = new ArrayList<>(List.of(runId, centerId, Date.valueOf(c.semaineDebut()), c.salleId(),
                    c.salleNom(), c.salleOrdre(), c.creneauId(), c.creneauLibelle(), c.creneauOrdre(),
                    mapper.writeValueAsString(c.jours())));
            for (int i = 0; i < NB_JOURS; i++)
                args.add(i < c.jours().size() ? CalendrierTexte.entete(c.jours().get(i)) : null);
            for (int i = 0; i < NB_JOURS; i++)
                args.add(i < c.jours().size() ? CalendrierTexte.cellule(c.jours().get(i)) : null);
            jdbc.update(INSERT, args.toArray());
        }
    }

    @Override
    public List<CaseCalendrier> lire(UUID centerId, UUID runId, LocalDate semaineDebut) {
        return jdbc.query("SELECT semaine_debut, salle_id, salle_nom, salle_ordre, creneau_id, creneau_libelle, "
                        + "creneau_ordre, jours FROM planification_calendrier_case WHERE center_id = ? AND run_id = ? "
                        + "AND semaine_debut = ? ORDER BY creneau_ordre, salle_ordre",
                (rs, i) -> new CaseCalendrier(rs.getDate("semaine_debut").toLocalDate(),
                        rs.getObject("salle_id", UUID.class), rs.getString("salle_nom"), rs.getInt("salle_ordre"),
                        rs.getObject("creneau_id", UUID.class), rs.getString("creneau_libelle"),
                        rs.getInt("creneau_ordre"),
                        mapper.readValue(rs.getString("jours"), new TypeReference<List<JourCalendrier>>() {
                        })),
                centerId, runId, Date.valueOf(semaineDebut));
    }

    @Override
    public List<LocalDate> semaines(UUID centerId, UUID runId) {
        return jdbc.query("SELECT DISTINCT semaine_debut FROM planification_calendrier_case WHERE center_id = ? "
                        + "AND run_id = ? ORDER BY semaine_debut", (rs, i) -> rs.getDate(1).toLocalDate(), centerId,
                runId);
    }

    @Override
    public void purgerOrphelins(UUID centerId) {
        jdbc.update("DELETE FROM planification_calendrier_case WHERE center_id = ? AND run_id NOT IN "
                + "(SELECT id FROM planification_optimisation WHERE center_id = ?)", centerId, centerId);
    }
}
