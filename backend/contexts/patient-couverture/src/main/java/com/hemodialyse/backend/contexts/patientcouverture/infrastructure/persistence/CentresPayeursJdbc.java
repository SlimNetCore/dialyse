package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.CentrePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentresPayeurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Les centres payeurs sur la table actuelle {@code centre_payeur} (RF3a, refonte sur place) ; mêmes règles que {@link CaissesJdbc}. */
public class CentresPayeursJdbc implements CentresPayeurs {

    private static final String CENTRE_PAYEUR = "SELECT id, center_id, code, nom, adresse, agence_id FROM centre_payeur";

    private static final RowMapper<CentrePayeur> LIGNE = (rs, n) -> CentrePayeur.reconstituer(rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("nom"), rs.getString("adresse"),
            rs.getObject("agence_id", UUID.class));

    private final JdbcTemplate jdbc;
    private final Consumer<CentreId> ecritures;

    public CentresPayeursJdbc(JdbcTemplate jdbc, Consumer<CentreId> ecritures) {
        this.jdbc = jdbc;
        this.ecritures = ecritures;
    }

    @Override
    public Optional<CentrePayeur> trouver(CentreId centre, UUID id) {
        return jdbc.query(CENTRE_PAYEUR + " WHERE center_id = ? AND id = ?", LIGNE, centre.valeur(), id).stream().findFirst();
    }

    @Override
    public List<CentrePayeur> duCentre(CentreId centre) {
        return jdbc.query(CENTRE_PAYEUR + " WHERE center_id = ? ORDER BY code, id", LIGNE, centre.valeur());
    }

    @Override
    public void enregistrer(CentrePayeur c) {
        jdbc.update("INSERT INTO centre_payeur (id, center_id, code, nom, adresse, agence_id) VALUES (?, ?, ?, ?, ?, ?)", c.id(),
                c.centre().valeur(), c.code(), c.nom(), c.adresse(), c.agence());
        ecritures.accept(c.centre());
    }

    @Override
    public void remplacer(CentrePayeur c) {
        jdbc.update("UPDATE centre_payeur SET code = ?, nom = ?, adresse = ?, agence_id = ? WHERE id = ? AND center_id = ?", c.code(),
                c.nom(), c.adresse(), c.agence(), c.id(), c.centre().valeur());
        ecritures.accept(c.centre());
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        jdbc.update("DELETE FROM centre_payeur WHERE id = ? AND center_id = ?", id, centre.valeur());
        ecritures.accept(centre);
    }
}
