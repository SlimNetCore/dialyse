package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.Affectation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;
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
 * Le roulement sur la table {@code infirmier_affectation} (une ligne par affectation ; les jours sont une liste séparée par des
 * virgules, triés dans l'ordre de la semaine). Dépôt de l'agrégat {@link RoulementDeLInfirmier} et lecture groupée pour les fiches.
 * Les écritures se font sous le verrou de la fiche de l'infirmier, pris par le cas d'usage.
 */
public class RoulementsJdbc implements RoulementsRepository, RoulementsDesInfirmiers {
    private final JdbcTemplate jdbc;

    public RoulementsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Set<JourDeLaSemaine> decoder(String jours) {
        Set<JourDeLaSemaine> resultat = EnumSet.noneOf(JourDeLaSemaine.class);
        Arrays.stream(jours.split(",")).filter(s -> !s.isBlank()).forEach(s -> resultat.add(JourDeLaSemaine.valueOf(s.trim())));
        return resultat;
    }

    static String encoder(Set<JourDeLaSemaine> jours) {
        return jours.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public RoulementDeLInfirmier de(CentreId centre, InfirmierId infirmier) {
        List<Affectation> affectations = jdbc.query("SELECT id, salle_id, creneau_id, jours FROM infirmier_affectation "
                        + "WHERE center_id = ? AND infirmier_id = ? ORDER BY creneau_id, id",
                (rs, i) -> new Affectation(new AffectationId(rs.getObject("id", UUID.class)),
                        new SalleId(rs.getObject("salle_id", UUID.class)), new CreneauId(rs.getObject("creneau_id", UUID.class)),
                        decoder(rs.getString("jours"))), centre.valeur(), infirmier.valeur());
        return RoulementDeLInfirmier.reconstituer(centre, infirmier, affectations);
    }

    @Override
    public void ajouter(CentreId centre, InfirmierId infirmier, Affectation a) {
        RefusDeVerrou.verrouillant(() -> jdbc.update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, "
                        + "creneau_id, jours) VALUES (?, ?, ?, ?, ?, ?)", a.id().valeur(), centre.valeur(), infirmier.valeur(),
                a.salle().valeur(), a.creneau().valeur(), encoder(a.jours())));
    }

    @Override
    public void remplacer(CentreId centre, InfirmierId infirmier, Affectation a) {
        RefusDeVerrou.verrouillant(() -> jdbc.update("UPDATE infirmier_affectation SET salle_id = ?, creneau_id = ?, jours = ? "
                        + "WHERE id = ? AND center_id = ? AND infirmier_id = ?", a.salle().valeur(), a.creneau().valeur(),
                encoder(a.jours()), a.id().valeur(), centre.valeur(), infirmier.valeur()));
    }

    @Override
    public void retirer(CentreId centre, InfirmierId infirmier, AffectationId affectation) {
        RefusDeVerrou.verrouillant(() -> jdbc.update("DELETE FROM infirmier_affectation WHERE id = ? AND center_id = ? "
                + "AND infirmier_id = ?", affectation.valeur(), centre.valeur(), infirmier.valeur()));
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
                    decoder(rs.getString("jours")).stream().sorted().toList()));
        }, arguments.toArray());
        return resultat;
    }
}
