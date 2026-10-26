package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Remplacement;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Les remplacements sur la table {@code infirmier_remplacement}. La contrainte {@code UNIQUE (center_id, date_jour, creneau_id,
 * infirmier_id)} reste le dernier garde-fou : sa violation (écriture concurrente passée sous le verrou) est un 409, plus un 500.
 */
public class RemplacementsJdbc implements RemplacementsRepository {

    private static final String COLONNES = "id, center_id, date_jour, salle_id, creneau_id, infirmier_id, remplace_infirmier_id";

    private static final RowMapper<Remplacement> MAPPER = (rs, i) -> {
        UUID remplace = rs.getObject("remplace_infirmier_id", UUID.class);
        return Remplacement.reconstituer(new RemplacementId(rs.getObject("id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), rs.getDate("date_jour").toLocalDate(),
                new SalleId(rs.getObject("salle_id", UUID.class)), new CreneauId(rs.getObject("creneau_id", UUID.class)),
                new InfirmierId(rs.getObject("infirmier_id", UUID.class)), remplace == null ? null : new InfirmierId(remplace));
    };

    private final JdbcTemplate jdbc;

    public RemplacementsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(Remplacement r) {
        RefusDeVerrou.verrouillant(() -> jdbc.update("INSERT INTO infirmier_remplacement (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                r.id().valeur(), r.centre().valeur(), Date.valueOf(r.date()), r.salle().valeur(), r.creneau().valeur(),
                r.remplacant().valeur(), r.remplace().map(InfirmierId::valeur).orElse(null)));
    }

    @Override
    public Optional<Remplacement> parId(CentreId centre, RemplacementId id) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier_remplacement WHERE center_id = ? AND id = ?", MAPPER,
                centre.valeur(), id.valeur()).stream().findFirst();
    }

    @Override
    public boolean remplaceDeja(CentreId centre, InfirmierId infirmier, LocalDate jour, CreneauId creneau) {
        Integer nombre = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_remplacement WHERE center_id = ? AND infirmier_id = ? "
                        + "AND date_jour = ? AND creneau_id = ?", Integer.class, centre.valeur(), infirmier.valeur(), Date.valueOf(jour),
                creneau.valeur());
        return nombre != null && nombre > 0;
    }

    @Override
    public void annuler(CentreId centre, RemplacementId id) {
        jdbc.update("DELETE FROM infirmier_remplacement WHERE center_id = ? AND id = ?", centre.valeur(), id.valeur());
    }
}
