package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.JourDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Planning calendaire figé des propositions, sur la table {@code planification_calendrier_case} : le détail des sept jours en JSON
 * (affichage) et sous forme de textes prêts à imprimer (modèle de document). Toutes les requêtes sont bornées au centre ; compatible
 * H2 et PostgreSQL.
 */
public class CalendriersDesPropositionsJdbc implements CalendriersDesPropositions {

    private static final int NB_JOURS = 7;
    private static final String INSERT = "INSERT INTO planification_calendrier_case (run_id, center_id, semaine_debut, "
            + "salle_id, salle_nom, salle_ordre, creneau_id, creneau_libelle, creneau_ordre, jours, "
            + "entete_0, entete_1, entete_2, entete_3, entete_4, entete_5, entete_6, "
            + "cell_0, cell_1, cell_2, cell_3, cell_4, cell_5, cell_6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CalendriersDesPropositionsJdbc(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void enregistrer(CentreId centre, ExecutionId execution, List<CaseDuCalendrier> cases) {
        jdbc.update("DELETE FROM planification_calendrier_case WHERE center_id = ? AND run_id = ?", centre.valeur(),
                execution.valeur());
        for (CaseDuCalendrier c : cases) {
            List<Object> args = new ArrayList<>(List.of(execution.valeur(), centre.valeur(), Date.valueOf(c.semaineDebut()),
                    c.salleId(), c.salleNom(), c.salleOrdre(), c.creneauId(), c.creneauLibelle(), c.creneauOrdre(),
                    mapper.writeValueAsString(c.jours())));
            for (int i = 0; i < NB_JOURS; i++)
                args.add(i < c.jours().size() ? CalendrierEnTexte.entete(c.jours().get(i)) : null);
            for (int i = 0; i < NB_JOURS; i++)
                args.add(i < c.jours().size() ? CalendrierEnTexte.cellule(c.jours().get(i)) : null);
            jdbc.update(INSERT, args.toArray());
        }
    }

    @Override
    public List<CaseDuCalendrier> lire(CentreId centre, ExecutionId execution, LocalDate semaineDebut) {
        return jdbc.query("SELECT semaine_debut, salle_id, salle_nom, salle_ordre, creneau_id, creneau_libelle, "
                        + "creneau_ordre, jours FROM planification_calendrier_case WHERE center_id = ? AND run_id = ? "
                        + "AND semaine_debut = ? ORDER BY creneau_ordre, salle_ordre",
                (rs, i) -> new CaseDuCalendrier(rs.getDate("semaine_debut").toLocalDate(),
                        rs.getObject("salle_id", UUID.class), rs.getString("salle_nom"), rs.getInt("salle_ordre"),
                        rs.getObject("creneau_id", UUID.class), rs.getString("creneau_libelle"), rs.getInt("creneau_ordre"),
                        mapper.readValue(rs.getString("jours"), new TypeReference<List<JourDuCalendrier>>() {
                        })),
                centre.valeur(), execution.valeur(), Date.valueOf(semaineDebut));
    }

    @Override
    public List<LocalDate> semaines(CentreId centre, ExecutionId execution) {
        return jdbc.query("SELECT DISTINCT semaine_debut FROM planification_calendrier_case WHERE center_id = ? "
                        + "AND run_id = ? ORDER BY semaine_debut", (rs, i) -> rs.getDate(1).toLocalDate(), centre.valeur(),
                execution.valeur());
    }

    @Override
    public void purgerOrphelins(CentreId centre) {
        jdbc.update("DELETE FROM planification_calendrier_case WHERE center_id = ? AND run_id NOT IN "
                + "(SELECT id FROM planification_optimisation WHERE center_id = ?)", centre.valeur(), centre.valeur());
    }
}
