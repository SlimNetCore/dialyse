package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.AbordsVasculairesPublies;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Porteurs d'un cathéter actif du centre sur la table réelle {@code abords_vasculaires} (DM9, ADR 0101). SQL repris MOT POUR MOT de
 * {@code contexts/planification/.../acl/legacypatient/ContraintesDesPatientsJdbc.java:34-37} : types {@code KT_TUNNELISE} et {@code KT_AIGU},
 * abord actif, fin absente ou postérieure ou égale au jour ; lu par le centre de l'ABORD (KB-MED-108 reproduit).
 */
public class PorteursDUnCatheterJdbc implements AbordsVasculairesPublies {

    private final JdbcTemplate jdbc;

    public PorteursDUnCatheterJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> porteursDUnCatheter(UUID centre, LocalDate auJour) {
        return new HashSet<>(jdbc.query("SELECT DISTINCT patient_id FROM abords_vasculaires "
                        + "WHERE center_id = ? AND actif = TRUE AND type_abord IN ('KT_TUNNELISE', 'KT_AIGU') "
                        + "AND (date_fin IS NULL OR date_fin >= ?)",
                (rs, i) -> rs.getObject("patient_id", UUID.class), centre, Date.valueOf(auJour)));
    }
}
