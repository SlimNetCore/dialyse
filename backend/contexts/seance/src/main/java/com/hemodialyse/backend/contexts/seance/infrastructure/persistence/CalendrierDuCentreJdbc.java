package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.JourDejaAuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

/**
 * Le calendrier du centre sur les tables RÉELLES {@code center_holiday} (colonne {@code label}) et {@code center_closure_day} (colonne
 * {@code reason}), aucune colonne ajoutée. L'unicité (centre, jour) est tenue par la base : un doublon, y compris de deux ajouts
 * simultanés, devient {@link JourDejaAuCalendrier}. Le nom de la table et de la colonne ne viennent que de {@link NatureDeJour}, jamais
 * d'une saisie.
 */
public class CalendrierDuCentreJdbc implements CalendrierDuCentre {

    private final JdbcTemplate jdbc;

    public CalendrierDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String table(NatureDeJour nature) {
        return nature == NatureDeJour.JOUR_FERIE ? "center_holiday" : "center_closure_day";
    }

    private static String colonne(NatureDeJour nature) {
        return nature == NatureDeJour.JOUR_FERIE ? "label" : "reason";
    }

    @Override
    public List<JourDuCalendrier> duMois(CentreId centre, NatureDeJour nature, MoisDuCalendrier mois) {
        return jdbc.query("SELECT id, day_date, " + colonne(nature) + " AS libelle FROM " + table(nature)
                        + " WHERE center_id = ? AND day_date BETWEEN ? AND ? ORDER BY day_date ASC, id ASC",
                (rs, i) -> new JourDuCalendrier(rs.getObject("id", UUID.class), centre, rs.getDate("day_date").toLocalDate(), nature,
                        rs.getString("libelle")),
                centre.valeur(), Date.valueOf(mois.premierJour()), Date.valueOf(mois.dernierJour()));
    }

    @Override
    public void ajouter(JourDuCalendrier jour) {
        try {
            jdbc.update("INSERT INTO " + table(jour.nature()) + " (id, center_id, day_date, " + colonne(jour.nature())
                    + ") VALUES (?, ?, ?, ?)", jour.id(), jour.centre().valeur(), Date.valueOf(jour.jour()), jour.libelle());
        } catch (DuplicateKeyException e) {
            throw new JourDejaAuCalendrier(jour.nature(), jour.jour());
        }
    }

    @Override
    public boolean retirer(CentreId centre, NatureDeJour nature, UUID id) {
        return jdbc.update("DELETE FROM " + table(nature) + " WHERE id = ? AND center_id = ?", id, centre.valeur()) > 0;
    }
}
