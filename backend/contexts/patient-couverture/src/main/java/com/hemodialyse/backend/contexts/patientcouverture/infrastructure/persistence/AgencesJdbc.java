package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Agence;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agences;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Les agences sur la table actuelle {@code agence} (RF3a, refonte sur place) ; mêmes règles de borne et de signal que {@link CaissesJdbc}. */
public class AgencesJdbc implements Agences {

    private static final String AGENCE = "SELECT id, center_id, code, nom, caisse_id FROM agence";

    private static final RowMapper<Agence> LIGNE = (rs, n) -> Agence.reconstituer(rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("nom"),
            rs.getObject("caisse_id", UUID.class));

    private final JdbcTemplate jdbc;
    private final Consumer<CentreId> ecritures;

    public AgencesJdbc(JdbcTemplate jdbc, Consumer<CentreId> ecritures) {
        this.jdbc = jdbc;
        this.ecritures = ecritures;
    }

    @Override
    public Optional<Agence> trouver(CentreId centre, UUID id) {
        return jdbc.query(AGENCE + " WHERE center_id = ? AND id = ?", LIGNE, centre.valeur(), id).stream().findFirst();
    }

    @Override
    public List<Agence> duCentre(CentreId centre) {
        return jdbc.query(AGENCE + " WHERE center_id = ? ORDER BY code, id", LIGNE, centre.valeur());
    }

    @Override
    public void enregistrer(Agence a) {
        jdbc.update("INSERT INTO agence (id, center_id, code, nom, caisse_id) VALUES (?, ?, ?, ?, ?)", a.id(), a.centre().valeur(),
                a.code(), a.nom(), a.caisse());
        ecritures.accept(a.centre());
    }

    @Override
    public void remplacer(Agence a) {
        jdbc.update("UPDATE agence SET code = ?, nom = ?, caisse_id = ? WHERE id = ? AND center_id = ?", a.code(), a.nom(), a.caisse(),
                a.id(), a.centre().valeur());
        ecritures.accept(a.centre());
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        jdbc.update("DELETE FROM agence WHERE id = ? AND center_id = ?", id, centre.valeur());
        ecritures.accept(centre);
    }
}
