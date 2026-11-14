package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Annuaire des centres sur la table {@code centers}. SQL repris à l'identique des lecteurs retirés (O4 lot A, ADR 0107) :
 * {@code CentresActifsJdbc} (Absences), {@code CentresActifsLegacy} (Planification), {@code CentresARappelerJdbc} (Séance),
 * {@code HorlogeDesCentresLegacy} (Planification, Patient-couverture), {@code JourDuCentreJdbc}, {@code JourDuCentreLegacy}.
 */
public class AnnuaireDesCentresJdbc implements AnnuaireDesCentres {

    private final JdbcTemplate jdbc;

    public AnnuaireDesCentresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> actifs() {
        return jdbc.query("SELECT id FROM centers WHERE COALESCE(actif, TRUE) = TRUE ORDER BY id",
                (rs, i) -> rs.getObject(1, UUID.class));
    }

    @Override
    public List<UUID> tous() {
        return jdbc.query("SELECT id FROM centers", (rs, i) -> rs.getObject(1, UUID.class));
    }

    @Override
    public List<CentreNomme> tousAvecNom() {
        return jdbc.query("SELECT id, name FROM centers ORDER BY name",
                (rs, i) -> new CentreNomme(rs.getObject("id", UUID.class), rs.getString("name")));
    }

    @Override
    public Optional<String> nomDuCentre(UUID centre) {
        var lignes = jdbc.queryForList("SELECT name FROM centers WHERE id = ?", String.class, centre);
        return lignes.isEmpty() ? Optional.empty() : Optional.ofNullable(lignes.get(0));
    }

    @Override
    public Optional<ZoneId> fuseauHoraireDe(UUID centre) {
        String identifiant = jdbc.queryForList("SELECT fuseau_horaire FROM centers WHERE id = ?", String.class, centre)
                .stream().filter(f -> f != null && !f.isBlank()).findFirst().orElse(null);
        if (identifiant == null) return Optional.empty();
        try {
            return Optional.of(ZoneId.of(identifiant.trim()));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
