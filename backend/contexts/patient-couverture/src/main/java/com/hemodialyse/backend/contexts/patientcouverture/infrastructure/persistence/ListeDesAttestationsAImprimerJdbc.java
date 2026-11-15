package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * La liste des ouvertures de droit à imprimer sur les tables réelles {@code attestation_droit} et {@code patients} (DOC5g, ADR 0199) : la
 * requête que le gabarit embarquait, reprise telle quelle (jointure sur le patient du même centre, bornée au centre,
 * {@code ORDER BY date_fin DESC} laissé au SGBD).
 */
public class ListeDesAttestationsAImprimerJdbc implements ListeDesAttestationsAImprimer {

    private final JdbcTemplate jdbc;

    public ListeDesAttestationsAImprimerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AttestationImprimee> duCentre(CentreId centre) {
        return jdbc.query("SELECT p.code_patient, p.nom, p.prenom, p.numero_assurance, a.date_debut, a.date_fin "
                        + "FROM attestation_droit a JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id "
                        + "WHERE a.center_id = ? ORDER BY a.date_fin DESC",
                (rs, i) -> new AttestationImprimee(rs.getString("code_patient"), rs.getString("nom"), rs.getString("prenom"),
                        rs.getString("numero_assurance"), rs.getObject("date_debut", LocalDate.class),
                        rs.getObject("date_fin", LocalDate.class)),
                centre.valeur());
    }
}
