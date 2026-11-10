package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.CaisseDAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Caisses;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Les caisses d'assurance sur la table actuelle {@code caisse_assurance} (RF3a, refonte sur place). Chaque requête est bornée à
 * {@code center_id} ; chaque écriture signale son centre ({@code ecritures}) pour l'invalidation des listes en cache après commit.
 */
public class CaissesJdbc implements Caisses {

    private static final String CAISSE = "SELECT id, center_id, code, nom, type_caisse FROM caisse_assurance";

    private static final RowMapper<CaisseDAssurance> LIGNE = (rs, n) -> CaisseDAssurance.reconstituer(rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("nom"), rs.getString("type_caisse"));

    private final JdbcTemplate jdbc;
    private final Consumer<CentreId> ecritures;

    public CaissesJdbc(JdbcTemplate jdbc, Consumer<CentreId> ecritures) {
        this.jdbc = jdbc;
        this.ecritures = ecritures;
    }

    @Override
    public Optional<CaisseDAssurance> trouver(CentreId centre, UUID id) {
        return jdbc.query(CAISSE + " WHERE center_id = ? AND id = ?", LIGNE, centre.valeur(), id).stream().findFirst();
    }

    @Override
    public List<CaisseDAssurance> duCentre(CentreId centre) {
        return jdbc.query(CAISSE + " WHERE center_id = ? ORDER BY code, id", LIGNE, centre.valeur());
    }

    @Override
    public void enregistrer(CaisseDAssurance c) {
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, ?, ?, ?)", c.id(),
                c.centre().valeur(), c.code(), c.nom(), c.type());
        ecritures.accept(c.centre());
    }

    @Override
    public void remplacer(CaisseDAssurance c) {
        jdbc.update("UPDATE caisse_assurance SET code = ?, nom = ?, type_caisse = ? WHERE id = ? AND center_id = ?", c.code(), c.nom(),
                c.type(), c.id(), c.centre().valeur());
        ecritures.accept(c.centre());
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        jdbc.update("DELETE FROM caisse_assurance WHERE id = ? AND center_id = ?", id, centre.valeur());
        ecritures.accept(centre);
    }
}
