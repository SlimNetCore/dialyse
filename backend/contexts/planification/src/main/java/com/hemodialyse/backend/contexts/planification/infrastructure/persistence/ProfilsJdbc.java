package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Profils de planification des infirmiers (table {@code infirmier_profil_planning}) : taux d'activité et compétences, stockées
 * en liste séparée par des virgules, dans l'ordre de l'énumération. Une valeur stockée vide ou blanche donne aucune compétence.
 */
public class ProfilsJdbc implements ProfilsRepository {

    private final JdbcTemplate jdbc;

    public ProfilsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Set<Competence> decoder(String competences) {
        Set<Competence> resultat = EnumSet.noneOf(Competence.class);
        if (competences == null || competences.isBlank()) return resultat;
        Arrays.stream(competences.split(",")).map(String::trim).filter(s -> !s.isBlank())
                .forEach(s -> resultat.add(Competence.valueOf(s)));
        return resultat;
    }

    static String encoder(Set<Competence> competences) {
        return competences.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Map<InfirmierId, ProfilDePlanification> duCentre(CentreId centre) {
        Map<InfirmierId, ProfilDePlanification> profils = new HashMap<>();
        jdbc.query("SELECT infirmier_id, taux_activite, competences FROM infirmier_profil_planning WHERE center_id = ?", rs -> {
            InfirmierId infirmier = new InfirmierId(rs.getObject("infirmier_id", UUID.class));
            profils.put(infirmier, new ProfilDePlanification(infirmier, rs.getInt("taux_activite"),
                    decoder(rs.getString("competences"))));
        }, centre.valeur());
        return profils;
    }

    @Override
    public void enregistrer(CentreId centre, ProfilDePlanification profil) {
        RefusDeVerrou.verrouillant(() -> {
            String competences = encoder(profil.competences());
            int modifiees = jdbc.update("UPDATE infirmier_profil_planning SET taux_activite = ?, competences = ? "
                    + "WHERE center_id = ? AND infirmier_id = ?", profil.tauxActivite(), competences, centre.valeur(),
                    profil.infirmier().valeur());
            if (modifiees == 0) {
                jdbc.update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) "
                        + "VALUES (?, ?, ?, ?)", centre.valeur(), profil.infirmier().valeur(), profil.tauxActivite(),
                        competences);
            }
            return null;
        });
    }
}
