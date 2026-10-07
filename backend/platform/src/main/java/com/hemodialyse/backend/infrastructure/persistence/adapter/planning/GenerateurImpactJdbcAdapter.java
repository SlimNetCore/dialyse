package com.hemodialyse.backend.infrastructure.persistence.adapter.planning;

import com.hemodialyse.backend.domain.patient.service.FinOccupation;
import com.hemodialyse.backend.domain.planning.port.GenerateurImpactPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Patients placés sur un générateur, lus dans la table {@code patients} du centre.
 */
@Component
public class GenerateurImpactJdbcAdapter implements GenerateurImpactPort {

    private final JdbcTemplate jdbc;

    public GenerateurImpactJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> patientsPlacesSur(UUID centerId, UUID generateurId, LocalDate aujourdhui) {
        return jdbc.query("SELECT nom, prenom, etat_patient, date_evenement_etat FROM patients "
                + "WHERE center_id = ? AND generateur_id = ? ORDER BY nom, prenom", (rs, i) -> {
            Date evenement = rs.getDate("date_evenement_etat");
            boolean sorti = FinOccupation.dernierJourOccupe(rs.getString("etat_patient"),
                            evenement == null ? null : evenement.toLocalDate())
                    .map(aujourdhui::isAfter).orElse(false);
            return sorti ? null : (rs.getString("nom") + " " + rs.getString("prenom")).trim();
        }, centerId, generateurId).stream().filter(java.util.Objects::nonNull).toList();
    }
}
