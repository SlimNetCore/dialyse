package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence.Situation;
import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le roulement imprimé d'une semaine (DOC5e, ADR 0197), lu dans les seules tables de la Planification : {@code infirmier},
 * {@code infirmier_affectation}, {@code infirmier_absence}, {@code infirmier_remplacement}. C'est la requête que le gabarit embarquait,
 * jour par jour, SANS les jointures vers les salles et les créneaux (tables d'Organisation, lues par organisation.v1) : le roulement d'un
 * jour se reconnaît par le nom du jour (recherche par sous-chaîne, sensible à la casse) ; l'infirmier d'un remplacement n'a pas à être actif.
 */
public class RoulementImprimableDeLaSemaineJdbc implements RoulementImprimableDeLaSemaine {

    /** Nom du jour recherché dans le roulement, selon le rang du jour dans la semaine (dimanche d'abord). */
    private static final String[] JOURS = {"DIMANCHE", "LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI"};

    private static final String REQUETE_DU_JOUR = """
            SELECT a.salle_id AS salle, a.creneau_id AS creneau, (COALESCE(i.prenom || ' ', '') || i.nom) AS infirmier,
                   CASE WHEN EXISTS (SELECT 1 FROM infirmier_absence ab WHERE ab.center_id = a.center_id
                                     AND ab.infirmier_id = a.infirmier_id AND CAST(? AS DATE) BETWEEN ab.date_debut AND ab.date_fin)
                        THEN 'Absent' ELSE 'Prévu' END AS statut
            FROM infirmier_affectation a
            JOIN infirmier i ON i.id = a.infirmier_id AND i.center_id = a.center_id
            WHERE a.center_id = ? AND i.actif = TRUE AND a.jours LIKE ?
            UNION ALL
            SELECT r.salle_id, r.creneau_id, (COALESCE(i.prenom || ' ', '') || i.nom), 'Remplaçant'
            FROM infirmier_remplacement r
            JOIN infirmier i ON i.id = r.infirmier_id AND i.center_id = r.center_id
            WHERE r.center_id = ? AND r.date_jour = CAST(? AS DATE)
            """;

    private final JdbcTemplate jdbc;

    public RoulementImprimableDeLaSemaineJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LigneDuRoulement> lignes(CentreId centre, LocalDate debutDeSemaine) {
        List<LigneDuRoulement> lignes = new ArrayList<>();
        for (int rang = 0; rang < JOURS.length; rang++) {
            LocalDate jour = debutDeSemaine.plusDays(rang);
            Date date = Date.valueOf(jour);
            lignes.addAll(jdbc.query(REQUETE_DU_JOUR, (rs, i) -> new LigneDuRoulement(jour, rs.getObject("salle", UUID.class),
                    rs.getObject("creneau", UUID.class), rs.getString("infirmier"), situation(rs.getString("statut"))),
                    date, centre.valeur(), "%" + JOURS[rang] + "%", centre.valeur(), date));
        }
        return lignes;
    }

    private static Situation situation(String libelle) {
        for (Situation s : Situation.values()) {
            if (s.libelle().equals(libelle)) return s;
        }
        throw new IllegalStateException("Situation de présence inconnue : " + libelle);
    }
}
