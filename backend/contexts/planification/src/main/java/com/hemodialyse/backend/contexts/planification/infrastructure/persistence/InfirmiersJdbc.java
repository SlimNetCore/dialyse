package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Référentiel des infirmiers sur la table {@code infirmier} (mêmes colonnes et même contrainte {@code UNIQUE(center_id,
 * matricule)} qu'avant la refonte). Les lectures qui précèdent une écriture verrouillent la ligne ({@code FOR UPDATE}) ; un refus de
 * verrou ou une clé créée entre-temps est une écriture concurrente (409). L'unicité sans la casse repose sur la recherche
 * {@code LOWER(matricule)} sous le verrou du référentiel : aucun index fonctionnel n'est ajouté (ADR 0056).
 */
public class InfirmiersJdbc implements InfirmiersRepository {

    private static final String COLONNES =
            "id, center_id, matricule, nom, prenom, telephone, qualification, habilite_isolement, actif, user_id";

    private static final RowMapper<Infirmier> MAPPER = (rs, i) -> {
        UUID compte = rs.getObject("user_id", UUID.class);
        return Infirmier.reconstituer(new InfirmierId(rs.getObject("id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), Matricule.enregistre(rs.getString("matricule")),
                new IdentiteDeLInfirmier(rs.getString("nom"), rs.getString("prenom"), rs.getString("telephone")),
                Qualification.valueOf(rs.getString("qualification")), rs.getBoolean("habilite_isolement"),
                rs.getBoolean("actif"), compte == null ? null : new CompteId(compte));
    };

    private final JdbcTemplate jdbc;

    public InfirmiersJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Infirmier> pourEcriture(CentreId centre, InfirmierId id) {
        return RefusDeVerrou.verrouillant(() -> jdbc.query("SELECT " + COLONNES
                + " FROM infirmier WHERE center_id = ? AND id = ? FOR UPDATE", MAPPER, centre.valeur(), id.valeur())
                .stream().findFirst());
    }

    @Override
    public PageDeResultats<Infirmier> page(CentreId centre, Pagination pagination) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier WHERE center_id = ?", Long.class, centre.valeur());
        List<Infirmier> fiches = jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? "
                        + "ORDER BY nom, prenom, matricule LIMIT ? OFFSET ?",
                MAPPER, centre.valeur(), pagination.taille(), pagination.decalage());
        return new PageDeResultats<>(fiches, total == null ? 0 : total, pagination.page(), pagination.taille());
    }

    @Override
    public Optional<Infirmier> parMatricule(CentreId centre, Matricule matricule) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? AND LOWER(matricule) = LOWER(?)",
                MAPPER, centre.valeur(), matricule.valeur()).stream().findFirst();
    }

    @Override
    public Optional<Infirmier> parCompte(CentreId centre, CompteId compte) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier WHERE center_id = ? AND user_id = ?",
                MAPPER, centre.valeur(), compte.valeur()).stream().findFirst();
    }

    @Override
    public List<Infirmier> fichesDuCompteDuCentre(CentreId centre, CompteId compte) {
        return RefusDeVerrou.verrouillant(() -> jdbc.query("SELECT " + COLONNES
                + " FROM infirmier WHERE center_id = ? AND user_id = ? ORDER BY id FOR UPDATE",
                MAPPER, centre.valeur(), compte.valeur()));
    }

    @Override
    public List<Infirmier> fichesDuCompte(CompteId compte) {
        return RefusDeVerrou.verrouillant(() -> jdbc.query("SELECT " + COLONNES
                + " FROM infirmier WHERE user_id = ? ORDER BY id FOR UPDATE", MAPPER, compte.valeur()));
    }

    @Override
    public void enregistrer(Infirmier fiche) {
        RefusDeVerrou.verrouillant(() -> {
            UUID compte = fiche.compte().map(CompteId::valeur).orElse(null);
            IdentiteDeLInfirmier identite = fiche.identite();
            int modifiees = jdbc.update("UPDATE infirmier SET matricule = ?, nom = ?, prenom = ?, telephone = ?, "
                            + "qualification = ?, habilite_isolement = ?, actif = ?, user_id = ? WHERE id = ? AND center_id = ?",
                    fiche.matricule().valeur(), identite.nom(), identite.prenom(), identite.telephone(),
                    fiche.qualification().name(), fiche.habiliteIsolement(), fiche.actif(), compte, fiche.id().valeur(),
                    fiche.centre().valeur());
            if (modifiees == 0) {
                jdbc.update("INSERT INTO infirmier (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        fiche.id().valeur(), fiche.centre().valeur(), fiche.matricule().valeur(), identite.nom(),
                        identite.prenom(), identite.telephone(), fiche.qualification().name(), fiche.habiliteIsolement(),
                        fiche.actif(), compte);
            }
            return null;
        });
    }
}
