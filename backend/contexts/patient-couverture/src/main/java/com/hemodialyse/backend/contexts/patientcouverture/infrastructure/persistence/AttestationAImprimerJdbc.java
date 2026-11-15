package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * L'ouverture de droit à imprimer sur les tables réelles {@code attestation_droit} et {@code patients} (DOC5h, ADR 0200) : la requête que le
 * gabarit embarquait, sans la jointure sur {@code centers} (le nom du centre vient de l'annuaire d'Organisation) : jointure sur le patient du
 * même centre, bornée au centre, {@code ORDER BY date_fin DESC LIMIT 1} laissés au SGBD.
 */
public class AttestationAImprimerJdbc implements AttestationAImprimer {

    private static final String SELECTION = "SELECT p.nom, p.prenom, p.sexe, p.date_naissance, p.numero_assurance, p.adresse, p.tel_mobile, "
            + "a.date_debut, a.date_fin FROM attestation_droit a JOIN patients p ON p.id = a.patient_id AND p.center_id = a.center_id "
            + "WHERE a.center_id = ? AND ";
    private static final String TRI = " ORDER BY a.date_fin DESC LIMIT 1";

    private static final RowMapper<AttestationImprimable> LIGNE = (rs, i) -> new AttestationImprimable(rs.getString("nom"),
            rs.getString("prenom"), rs.getString("sexe"), rs.getObject("date_naissance", LocalDate.class), rs.getString("numero_assurance"),
            rs.getString("adresse"), rs.getString("tel_mobile"), rs.getObject("date_debut", LocalDate.class),
            rs.getObject("date_fin", LocalDate.class));

    private final JdbcTemplate jdbc;

    public AttestationAImprimerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AttestationImprimable> parIdentifiant(CentreId centre, UUID attestation) {
        return jdbc.query(SELECTION + "a.id = ?" + TRI, LIGNE, centre.valeur(), attestation).stream().findFirst();
    }

    @Override
    public Optional<AttestationImprimable> laPlusRecenteDuPatient(CentreId centre, UUID patient) {
        return jdbc.query(SELECTION + "a.patient_id = ?" + TRI, LIGNE, centre.valeur(), patient).stream().findFirst();
    }
}
