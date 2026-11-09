package com.hemodialyse.backend.contexts.stock.contrat;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.CentresFournis;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrouDuCentreFourni;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doublure de test des ports fournis au Stock (O4 lot B, ADR 0108) pour les contrats PostgreSQL du Stock : le Stock ne peut pas dépendre
 * d'Organisation, la plateforme câble en production l'annuaire d'Organisation ; ici, la table des centres de la base du contrat répond avec
 * les requêtes d'origine des lecteurs retirés. Code de TEST uniquement.
 */
public final class CentresFournisSurJdbcDeTest implements CentresFournis, VerrouDuCentreFourni {

    private final JdbcTemplate jdbc;

    public CentresFournisSurJdbcDeTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> centresActifsAuSensStrict() {
        return jdbc.query("SELECT id FROM centers WHERE actif = TRUE ORDER BY id", (rs, i) -> rs.getObject(1, UUID.class));
    }

    @Override
    public List<UUID> tousLesCentres() {
        return jdbc.query("SELECT id FROM centers", (rs, i) -> rs.getObject(1, UUID.class));
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

    @Override
    public void verrouiller(UUID centre) {
        jdbc.queryForList("SELECT id FROM centers WHERE id = ? FOR UPDATE", UUID.class, centre);
    }
}
