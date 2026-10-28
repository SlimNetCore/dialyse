package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Verrou des places du centre (ADR 0049, décision du 27/10) : {@code SELECT … FOR UPDATE} de la ligne {@code planning_parametres} du
 * centre, <b>créée avec les valeurs par défaut si elle est absente</b> (un centre jamais paramétré a ainsi une exclusion mutuelle).
 * L'insertion est conditionnelle ({@code WHERE NOT EXISTS}) et portable H2/PostgreSQL ; si deux écritures créent la ligne ensemble,
 * l'index unique départage et la perdante reçoit 409 (écriture concurrente), sans relire dans une transaction abandonnée. Le
 * {@code lock_timeout} est posé par l'unité de travail.
 */
public class VerrouDesPlacesJdbc implements VerrouDesPlaces {

    private static final String VERROUILLER = "SELECT center_id FROM planning_parametres WHERE center_id = ? FOR UPDATE";

    private final JdbcTemplate jdbc;

    public VerrouDesPlacesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void prendre(CentreId centre) {
        RefusDeVerrou.verrouillant(() -> {
            if (!jdbc.queryForList(VERROUILLER, centre.valeur()).isEmpty()) {
                return null;
            }
            ParametresDuPlanning defaut = ParametresDuPlanning.parDefaut();
            String jours = defaut.joursOuverts().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
            jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, salles_isolement, patients_par_infirmier, "
                            + "patients_par_poste_serie, updated_at) SELECT CAST(? AS UUID), CAST(? AS VARCHAR(100)), NULL, "
                            + "CAST(? AS INTEGER), CAST(? AS INTEGER), CAST(? AS TIMESTAMP WITH TIME ZONE) "
                            + "WHERE NOT EXISTS (SELECT 1 FROM planning_parametres WHERE center_id = ?)",
                    centre.valeur(), jours, defaut.ratio().patientsParInfirmier(), defaut.patientsParPoste().nombre(),
                    Timestamp.from(Instant.now()), centre.valeur());
            jdbc.queryForList(VERROUILLER, centre.valeur());
            return null;
        });
    }
}
