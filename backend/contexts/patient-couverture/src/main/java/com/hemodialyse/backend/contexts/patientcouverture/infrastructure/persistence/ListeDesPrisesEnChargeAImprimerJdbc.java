package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * La liste des prises en charge à imprimer sur les tables réelles {@code prise_en_charge} et {@code patients} (DOC5g, ADR 0199) : la
 * requête que le gabarit embarquait, reprise telle quelle (jointure sur le patient du même centre, bornée au centre,
 * {@code ORDER BY date_fin_demande DESC} laissé au SGBD).
 */
public class ListeDesPrisesEnChargeAImprimerJdbc implements ListeDesPrisesEnChargeAImprimer {

    private final JdbcTemplate jdbc;

    public ListeDesPrisesEnChargeAImprimerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PriseEnChargeImprimee> duCentre(CentreId centre) {
        return jdbc.query("SELECT p.code_patient, p.nom, p.prenom, p.numero_assurance, pc.date_debut_demande, pc.date_fin_demande, pc.statut "
                        + "FROM prise_en_charge pc JOIN patients p ON p.id = pc.patient_id AND p.center_id = pc.center_id "
                        + "WHERE pc.center_id = ? ORDER BY pc.date_fin_demande DESC",
                (rs, i) -> new PriseEnChargeImprimee(rs.getString("code_patient"), rs.getString("nom"), rs.getString("prenom"),
                        rs.getString("numero_assurance"), rs.getObject("date_debut_demande", LocalDate.class),
                        rs.getObject("date_fin_demande", LocalDate.class), rs.getString("statut")),
                centre.valeur());
    }
}
