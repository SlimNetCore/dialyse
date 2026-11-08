package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Deux comptages publiés (ADR 0099, SE7e), lecture seule, requêtes reprises MOT POUR MOT : E10, séances imprimables du cahier de dialyse
 * ({@code CahierDialyseReportService.nombreSeances}, casse exacte du statut : KB-SEA-88) ; E17, séances qui imposent un forfait
 * ({@code ReferentialAdminJdbcAdapter.countUsages} sur l'usage {@code seances.forfait_override_id}, sans centre ni statut : KB-SEA-91).
 */
public class SeancesDuCahierEtForfaitsImposesJdbc implements SeancesDuCahierPubliees, ForfaitsImposesAuxSeancesPublies {

    private final JdbcTemplate jdbc;

    public SeancesDuCahierEtForfaitsImposesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int nombreDeSeancesAImprimer(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND patient_id = ? "
                        + "AND statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND date_seance BETWEEN ? AND ?",
                Integer.class, centre, patient, Date.valueOf(du), Date.valueOf(au));
        return n == null ? 0 : n;
    }

    @Override
    public long nombreDeSeancesQuiImposentLeForfait(UUID forfait) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE forfait_override_id = ?", Long.class, forfait);
        return count == null ? 0 : count;
    }
}
