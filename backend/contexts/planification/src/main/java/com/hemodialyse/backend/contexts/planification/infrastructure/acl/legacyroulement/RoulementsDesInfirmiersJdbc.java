package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyroulement;

import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ACL en lecture seule vers le roulement des infirmiers (table {@code infirmier_affectation}, propriété de la slice P3, encore
 * dans le legacy) : les jours sont stockés en liste séparée par des virgules.
 */
public class RoulementsDesInfirmiersJdbc implements RoulementsDesInfirmiers {
    private final JdbcTemplate jdbc;

    public RoulementsDesInfirmiersJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static List<JourDeLaSemaine> decoder(String jours) {
        Set<JourDeLaSemaine> resultat = EnumSet.noneOf(JourDeLaSemaine.class);
        Arrays.stream(jours.split(",")).filter(s -> !s.isBlank()).forEach(s -> resultat.add(JourDeLaSemaine.valueOf(s.trim())));
        return List.copyOf(resultat);
    }

    @Override
    public Map<InfirmierId, List<AffectationLue>> deLaListe(CentreId centre, Collection<InfirmierId> infirmiers) {
        if (infirmiers.isEmpty()) {
            return Map.of();
        }
        String marques = infirmiers.stream().map(id -> "?").collect(Collectors.joining(", "));
        List<Object> arguments = new ArrayList<>();
        arguments.add(centre.valeur());
        infirmiers.forEach(id -> arguments.add(id.valeur()));
        Map<InfirmierId, List<AffectationLue>> resultat = new HashMap<>();
        jdbc.query("SELECT id, infirmier_id, salle_id, creneau_id, jours FROM infirmier_affectation WHERE center_id = ? "
                + "AND infirmier_id IN (" + marques + ") ORDER BY creneau_id, id", rs -> {
            InfirmierId infirmier = new InfirmierId(rs.getObject("infirmier_id", UUID.class));
            resultat.computeIfAbsent(infirmier, k -> new ArrayList<>()).add(new AffectationLue(rs.getObject("id", UUID.class),
                    new SalleId(rs.getObject("salle_id", UUID.class)), new CreneauId(rs.getObject("creneau_id", UUID.class)),
                    decoder(rs.getString("jours"))));
        }, arguments.toArray());
        return resultat;
    }
}
