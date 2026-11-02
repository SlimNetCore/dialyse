package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SallesJdbc implements SalleRepository {
    private final JdbcTemplate jdbc;
    private final EvictionApresCommit eviction;

    public SallesJdbc(JdbcTemplate jdbc) {
        this(jdbc, EvictionApresCommit.AUCUNE);
    }

    public SallesJdbc(JdbcTemplate jdbc, EvictionApresCommit eviction) {
        this.jdbc = jdbc;
        this.eviction = eviction;
    }

    @Override
    public Optional<Salle> trouver(CentreId centreId, SalleId id) {
        List<Salle> r = jdbc.query("SELECT id, center_id, code, nom, ouverte FROM salle WHERE center_id = ? AND id = ?",
                (rs, i) -> new Salle(new SalleId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("code"), rs.getString("nom"), rs.getBoolean("ouverte")),
                centreId.valeur(), id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Salle s) {
        ecrire(s);
        // Après l'écriture, et après le commit s'il y a une transaction : salles et générateurs (rattachés aux salles) du centre.
        eviction.retirer("ref.salles", s.centreId().valeur().toString());
        eviction.retirer("ref.generateurs", s.centreId().valeur().toString());
    }

    private void ecrire(Salle s) {
        int n = jdbc.update("UPDATE salle SET code=?, nom=?, ouverte=? WHERE id=? AND center_id=?", s.code(), s.nom(), s.ouverte(), s.id().valeur(), s.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO salle (id, center_id, code, nom, ouverte) VALUES (?,?,?,?,?)",
                    s.id().valeur(), s.centreId().valeur(), s.code(), s.nom(), s.ouverte());
        }
    }

    @Override
    @Cacheable(cacheNames = "ref.salles", key = "#centreId.valeur().toString()")
    public List<Salle> listerParCentre(CentreId centreId) {
        return jdbc.query("SELECT id, center_id, code, nom, ouverte FROM salle WHERE center_id = ? ORDER BY code",
                (rs, i) -> new Salle(new SalleId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("code"), rs.getString("nom"), rs.getBoolean("ouverte")),
                centreId.valeur());
    }
}
