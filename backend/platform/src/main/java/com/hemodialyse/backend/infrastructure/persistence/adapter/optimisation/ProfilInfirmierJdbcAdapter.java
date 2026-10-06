package com.hemodialyse.backend.infrastructure.persistence.adapter.optimisation;

import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.ProfilInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.port.ProfilInfirmierPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Profils de planification des infirmiers (table {@code infirmier_profil_planning}) : taux d'activité et compétences,
 * stockées sous forme de liste séparée par des virgules. Toutes les requêtes sont bornées au centre.
 */
@Component
public class ProfilInfirmierJdbcAdapter implements ProfilInfirmierPort {

    private final JdbcTemplate jdbc;

    public ProfilInfirmierJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Set<CompetenceInfirmier> decoder(String competences) {
        if (competences == null || competences.isBlank()) return Set.of();
        Set<CompetenceInfirmier> resultat = EnumSet.noneOf(CompetenceInfirmier.class);
        Arrays.stream(competences.split(",")).map(String::trim).filter(s -> !s.isBlank())
                .forEach(s -> resultat.add(CompetenceInfirmier.valueOf(s)));
        return resultat;
    }

    static String encoder(Set<CompetenceInfirmier> competences) {
        return competences.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Map<UUID, ProfilInfirmier> profils(UUID centerId) {
        Map<UUID, ProfilInfirmier> profils = new HashMap<>();
        jdbc.query("SELECT infirmier_id, taux_activite, competences FROM infirmier_profil_planning WHERE center_id = ?",
                rs -> {
                    UUID id = rs.getObject("infirmier_id", UUID.class);
                    profils.put(id, new ProfilInfirmier(id, rs.getInt("taux_activite"), decoder(rs.getString("competences"))));
                }, centerId);
        return profils;
    }

    @Override
    public PagedResult<Ligne> lister(UUID centerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier WHERE center_id = ? AND actif = TRUE",
                Long.class, centerId);
        List<Ligne> items = jdbc.query("SELECT i.id, i.nom, i.prenom, i.qualification, "
                        + "COALESCE(p.taux_activite, 100) AS taux_activite, p.competences FROM infirmier i "
                        + "LEFT JOIN infirmier_profil_planning p ON p.center_id = i.center_id AND p.infirmier_id = i.id "
                        + "WHERE i.center_id = ? AND i.actif = TRUE ORDER BY i.nom, i.prenom, i.id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String prenom = rs.getString("prenom");
                    return new Ligne(id, prenom == null ? rs.getString("nom") : prenom + " " + rs.getString("nom"),
                            QualificationInfirmier.valueOf(rs.getString("qualification")),
                            new ProfilInfirmier(id, rs.getInt("taux_activite"), decoder(rs.getString("competences"))));
                }, centerId, size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public boolean enregistrer(UUID centerId, ProfilInfirmier profil) {
        Long existe = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier WHERE center_id = ? AND id = ?", Long.class,
                centerId, profil.infirmierId());
        if (existe == null || existe == 0) return false;
        int maj = jdbc.update("UPDATE infirmier_profil_planning SET taux_activite = ?, competences = ? "
                        + "WHERE center_id = ? AND infirmier_id = ?",
                profil.tauxActivite(), encoder(profil.competences()), centerId, profil.infirmierId());
        if (maj == 0) {
            jdbc.update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) "
                    + "VALUES (?, ?, ?, ?)", centerId, profil.infirmierId(), profil.tauxActivite(),
                    encoder(profil.competences()));
        }
        return true;
    }
}
