package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Dernière créatinine du patient au centre sur la table réelle {@code resultats_analyses} (SE7f, ADR 0100). Reproduit MOT POUR MOT le
 * chemin du legacy ({@code ResultatAnalyseJpaRepository.findByPatientIdAndCenterIdOrderByDatePrelevementDesc} puis
 * {@code KdigoGreffeQueryService.dernierCreatinine} : {@code filter(creatinine != null).max(date)}) : toutes les analyses du patient au
 * centre, triées par date de prélèvement décroissante SEULEMENT, puis en Java la créatinine non nulle de date maximale, la première lue
 * l'emportant à date égale (ordre du SGBD, non défini : KB-SEA-96, reproduit). Aucune autre colonne lue.
 */
public class DerniereCreatinineJdbc implements DerniereCreatininePubliee {

    private record Mesure(LocalDate prelevement, BigDecimal creatinine) {
    }

    private final JdbcTemplate jdbc;

    public DerniereCreatinineJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BigDecimal> duPatient(UUID centre, UUID patient) {
        return jdbc.query("SELECT date_prelevement, creatinine_mg_dl FROM resultats_analyses WHERE patient_id = ? AND center_id = ? "
                        + "ORDER BY date_prelevement DESC",
                        (rs, i) -> new Mesure(rs.getObject("date_prelevement", LocalDate.class), rs.getBigDecimal("creatinine_mg_dl")),
                        patient, centre).stream()
                .filter(m -> m.creatinine() != null)
                .max(Comparator.comparing(Mesure::prelevement))
                .map(Mesure::creatinine);
    }
}
