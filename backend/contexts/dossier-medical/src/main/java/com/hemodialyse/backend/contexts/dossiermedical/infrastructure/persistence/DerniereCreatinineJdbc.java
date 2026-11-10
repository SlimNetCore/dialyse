package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Dernière créatinine du patient au centre sur la table réelle {@code resultats_analyses} (SE7f, ADR 0100). Reproduit le chemin du legacy
 * ({@code ResultatAnalyseJpaRepository.findByPatientIdAndCenterIdOrderByDatePrelevementDesc} puis
 * {@code KdigoGreffeQueryService.dernierCreatinine} : la créatinine non nulle de date de prélèvement maximale) avec UNE correction, datée
 * du 2026-11-10 (lot L5a, ADR 0117, KB-SEA-96 a) : à date de prélèvement égale l'ordre n'est plus celui du SGBD, la dernière saisie fait foi
 * ({@code created_at DESC NULLS LAST, id DESC}), puis la première ligne dont la créatinine n'est pas nulle est retenue. Une créatinine à 0
 * reste une valeur lue (KB-SEA-96 b, close sans changement : le domaine la juge non évaluable). Aucune autre colonne lue.
 */
public class DerniereCreatinineJdbc implements DerniereCreatininePubliee {

    private final JdbcTemplate jdbc;

    public DerniereCreatinineJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BigDecimal> duPatient(UUID centre, UUID patient) {
        return jdbc.query("SELECT creatinine_mg_dl FROM resultats_analyses WHERE patient_id = ? AND center_id = ? "
                                + "AND creatinine_mg_dl IS NOT NULL ORDER BY date_prelevement DESC, created_at DESC NULLS LAST, id DESC LIMIT 1",
                        (rs, i) -> rs.getBigDecimal("creatinine_mg_dl"), patient, centre).stream().findFirst();
    }
}
