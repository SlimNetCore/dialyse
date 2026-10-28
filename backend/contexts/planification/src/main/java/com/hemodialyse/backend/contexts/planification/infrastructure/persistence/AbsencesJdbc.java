package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les absences sur la table {@code infirmier_absence}. La colonne {@code declaree_par} (compte de l'auteur, V47) est nulle pour les
 * absences antérieures à sa conservation. Le non-chevauchement est jugé par le cas d'usage sous le verrou de la fiche.
 */
public class AbsencesJdbc implements AbsencesRepository {

    private static final String COLONNES = "id, center_id, infirmier_id, date_debut, date_fin, type, motif, declaree_par";

    private static final RowMapper<AbsenceInfirmier> MAPPER = (rs, i) -> {
        UUID auteur = rs.getObject("declaree_par", UUID.class);
        return AbsenceInfirmier.reconstituer(new AbsenceId(rs.getObject("id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), new InfirmierId(rs.getObject("infirmier_id", UUID.class)),
                new PeriodeDAbsence(rs.getDate("date_debut").toLocalDate(), rs.getDate("date_fin").toLocalDate()),
                TypeAbsence.valueOf(rs.getString("type")), rs.getString("motif"), auteur == null ? null : new CompteId(auteur));
    };

    private final JdbcTemplate jdbc;

    public AbsencesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(AbsenceInfirmier a) {
        RefusDeVerrou.verrouillant(() -> {
            UUID auteur = a.auteur().map(CompteId::valeur).orElse(null);
            // une absence n'est jamais modifiée : elle est déclarée puis retirée
            jdbc.update("INSERT INTO infirmier_absence (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)", a.id().valeur(),
                    a.centre().valeur(), a.infirmier().valeur(), Date.valueOf(a.periode().debut()),
                    Date.valueOf(a.periode().fin()), a.type().name(), a.motif().orElse(null), auteur);
            return null;
        });
    }

    @Override
    public Optional<AbsenceInfirmier> parId(CentreId centre, AbsenceId id) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier_absence WHERE center_id = ? AND id = ?", MAPPER,
                centre.valeur(), id.valeur()).stream().findFirst();
    }

    @Override
    public PageDeResultats<AbsenceInfirmier> page(CentreId centre, Pagination pagination) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_absence WHERE center_id = ?", Long.class, centre.valeur());
        List<AbsenceInfirmier> items = jdbc.query("SELECT " + COLONNES + " FROM infirmier_absence WHERE center_id = ? "
                        + "ORDER BY date_debut DESC, id LIMIT ? OFFSET ?", MAPPER, centre.valeur(), pagination.taille(),
                pagination.decalage());
        return new PageDeResultats<>(items, total == null ? 0 : total, pagination.page(), pagination.taille());
    }

    @Override
    public PageDeResultats<AbsenceInfirmier> pageDeLInfirmier(CentreId centre, InfirmierId infirmier, Pagination pagination) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_absence WHERE center_id = ? AND infirmier_id = ?",
                Long.class, centre.valeur(), infirmier.valeur());
        List<AbsenceInfirmier> items = jdbc.query("SELECT " + COLONNES + " FROM infirmier_absence "
                        + "WHERE center_id = ? AND infirmier_id = ? ORDER BY date_debut DESC, id LIMIT ? OFFSET ?", MAPPER,
                centre.valeur(), infirmier.valeur(), pagination.taille(), pagination.decalage());
        return new PageDeResultats<>(items, total == null ? 0 : total, pagination.page(), pagination.taille());
    }

    @Override
    public boolean existeUneAbsenceSurLaPeriode(CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode) {
        Integer nombre = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_absence WHERE center_id = ? AND infirmier_id = ? "
                        + "AND date_debut <= ? AND date_fin >= ?", Integer.class, centre.valeur(), infirmier.valeur(),
                Date.valueOf(periode.fin()), Date.valueOf(periode.debut()));
        return nombre != null && nombre > 0;
    }

    @Override
    public boolean absentLe(CentreId centre, InfirmierId infirmier, LocalDate jour) {
        Integer nombre = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_absence WHERE center_id = ? AND infirmier_id = ? "
                        + "AND date_debut <= ? AND date_fin >= ?", Integer.class, centre.valeur(), infirmier.valeur(),
                Date.valueOf(jour), Date.valueOf(jour));
        return nombre != null && nombre > 0;
    }

    @Override
    public void retirer(CentreId centre, AbsenceId id) {
        jdbc.update("DELETE FROM infirmier_absence WHERE center_id = ? AND id = ?", centre.valeur(), id.valeur());
    }
}
