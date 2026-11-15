package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * La prise en charge à imprimer sur les tables réelles {@code prise_en_charge} et {@code patients} (DOC5h, ADR 0200) : la requête que le
 * gabarit embarquait, sans la jointure sur {@code centers} (le nom du centre vient de l'annuaire d'Organisation) : jointure sur le patient du
 * même centre, bornée au centre, {@code ORDER BY date_fin_demande DESC LIMIT 1} laissés au SGBD (la place d'une date absente est celle du SGBD).
 */
public class PriseEnChargeAImprimerJdbc implements PriseEnChargeAImprimer {

    private static final String SELECTION = "SELECT p.nom, p.prenom, p.numero_assurance, p.date_naissance, pc.date_debut_demande, "
            + "pc.date_fin_demande, pc.statut, pc.date_debut_effectif, pc.date_fin_effectif FROM prise_en_charge pc "
            + "JOIN patients p ON p.id = pc.patient_id AND p.center_id = pc.center_id WHERE pc.center_id = ? AND ";
    private static final String TRI = " ORDER BY pc.date_fin_demande DESC LIMIT 1";

    private static final RowMapper<PriseEnChargeImprimable> LIGNE = (rs, i) -> new PriseEnChargeImprimable(rs.getString("nom"),
            rs.getString("prenom"), rs.getString("numero_assurance"), rs.getObject("date_naissance", LocalDate.class),
            rs.getObject("date_debut_demande", LocalDate.class), rs.getObject("date_fin_demande", LocalDate.class), rs.getString("statut"),
            rs.getObject("date_debut_effectif", LocalDate.class), rs.getObject("date_fin_effectif", LocalDate.class));

    private final JdbcTemplate jdbc;

    public PriseEnChargeAImprimerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PriseEnChargeImprimable> parIdentifiant(CentreId centre, UUID priseEnCharge) {
        return jdbc.query(SELECTION + "pc.id = ?" + TRI, LIGNE, centre.valeur(), priseEnCharge).stream().findFirst();
    }

    @Override
    public Optional<PriseEnChargeImprimable> laPlusRecenteDuPatient(CentreId centre, UUID patient) {
        return jdbc.query(SELECTION + "pc.patient_id = ?" + TRI, LIGNE, centre.valeur(), patient).stream().findFirst();
    }
}
