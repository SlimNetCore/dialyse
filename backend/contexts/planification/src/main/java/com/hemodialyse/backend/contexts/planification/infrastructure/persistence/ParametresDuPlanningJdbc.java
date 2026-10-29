package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.SallesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.PatientsParPosteEtSerie;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeSecurite;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Paramétrage du planning : jours, ratio et patients par poste dans {@code planning_parametres} (liste de jours séparée par des
 * virgules, ratio et poste NULL = défaut pour les lignes anciennes) ; les salles d'isolement sont celles d'Organisation, lues et
 * écrites par le port {@link SallesDuCentre}. La colonne historique {@code salles_isolement} n'est plus renseignée : elle est
 * remise à NULL à chaque écriture (KB-PLN-06 reproduit).
 */
public class ParametresDuPlanningJdbc implements ParametresDuPlanningRepository {

    private final JdbcTemplate jdbc;
    private final SallesDuCentre salles;

    private final java.time.Clock horloge;

    /**
     * Horloge du serveur (UTC) : les tests de contrat qui ne regardent pas l'heure n'ont pas à en fournir.
     */
    public ParametresDuPlanningJdbc(JdbcTemplate jdbc, SallesDuCentre salles) {
        this(jdbc, salles, java.time.Clock.systemUTC());
    }

    /**
     * L'horloge est injectée par la composition : l'adapter ne lit pas l'heure du système de lui-même.
     */
    public ParametresDuPlanningJdbc(JdbcTemplate jdbc, SallesDuCentre salles, java.time.Clock horloge) {
        this.jdbc = jdbc;
        this.salles = salles;
        this.horloge = horloge;
    }

    private record Ligne(String jours, Integer ratio, Integer poste) {
    }

    @Override
    public ParametresDuPlanning lire(CentreId centre) {
        List<Ligne> lignes = jdbc.query("SELECT jours_ouverts, patients_par_infirmier, patients_par_poste_serie "
                        + "FROM planning_parametres WHERE center_id = ?",
                (rs, i) -> new Ligne(rs.getString("jours_ouverts"), (Integer) rs.getObject("patients_par_infirmier"),
                        (Integer) rs.getObject("patients_par_poste_serie")), centre.valeur());
        ParametresDuPlanning lus = lignes.isEmpty() ? ParametresDuPlanning.parDefaut() : depuis(lignes.getFirst());
        return lus.avecLesSallesDIsolement(salles.sallesDIsolement(centre));
    }

    private static ParametresDuPlanning depuis(Ligne ligne) {
        Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
        Arrays.stream(ligne.jours().split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .forEach(s -> jours.add(JourDeLaSemaine.valueOf(s)));
        return new ParametresDuPlanning(jours.isEmpty() ? EnumSet.allOf(JourDeLaSemaine.class) : jours, Set.of(),
                ratioLu(ligne.ratio()),
                ligne.poste() == null ? PatientsParPosteEtSerie.parDefaut() : new PatientsParPosteEtSerie(ligne.poste()));
    }

    /** Un ratio hérité inutilisable (hors 1 à 20) est lu comme le défaut, jamais comme une erreur (KB-PLN-47). */
    private static RatioDeSecurite ratioLu(Integer valeur) {
        RatioDeLaPresence lu = RatioDeLaPresence.depuisLaBase(valeur);
        if (lu.remplaceParLeDefaut()) {
            org.slf4j.LoggerFactory.getLogger(ParametresDuPlanningJdbc.class)
                    .warn("[PLANIFICATION] Ratio de sécurité inutilisable en base ({}) : le défaut {} est utilisé", valeur,
                            lu.patientsParInfirmier());
        }
        return new RatioDeSecurite(lu.patientsParInfirmier());
    }

    @Override
    public void enregistrer(CentreId centre, ParametresDuPlanning p) {
        RefusDeVerrou.verrouillant(() -> {
            String jours = p.joursOuverts().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
            Timestamp maintenant = Timestamp.from(horloge.instant());
            // KB-PLN-06 reproduit : la colonne historique est remise à NULL à chaque écriture, comme le faisait le legacy.
            int modifiees = jdbc.update("UPDATE planning_parametres SET jours_ouverts = ?, salles_isolement = NULL, "
                            + "patients_par_infirmier = ?, patients_par_poste_serie = ?, updated_at = ? WHERE center_id = ?",
                    jours, p.ratio().patientsParInfirmier(), p.patientsParPoste().nombre(), maintenant, centre.valeur());
            if (modifiees == 0) {
                jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, salles_isolement, patients_par_infirmier, "
                                + "patients_par_poste_serie, updated_at) VALUES (?, ?, NULL, ?, ?, ?)",
                        centre.valeur(), jours, p.ratio().patientsParInfirmier(), p.patientsParPoste().nombre(), maintenant);
            }
            salles.marquerIsolement(centre, p.sallesIsolement());
            return null;
        });
    }
}
