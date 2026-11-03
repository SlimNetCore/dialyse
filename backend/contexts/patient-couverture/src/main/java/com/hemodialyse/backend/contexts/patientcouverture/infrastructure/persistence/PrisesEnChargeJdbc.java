package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.StatutPec;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter du port {@link PrisesEnCharge} sur la table {@code prise_en_charge} (vocabulaire et colonnes du legacy). */
public class PrisesEnChargeJdbc implements PrisesEnCharge {

    private static final String COLONNES = "id, patient_id, center_id, date_debut_demande, date_fin_demande, forfait_demande_id, "
            + "date_debut_effectif, date_fin_effectif, forfait_effectif_id, statut, created_at";

    private static final RowMapper<PriseEnCharge> LIGNE = (rs, n) -> {
        OffsetDateTime cree = rs.getObject("created_at", OffsetDateTime.class);
        return PriseEnCharge.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), jour(rs.getDate("date_debut_demande")),
                jour(rs.getDate("date_fin_demande")), rs.getObject("forfait_demande_id", UUID.class),
                jour(rs.getDate("date_debut_effectif")), jour(rs.getDate("date_fin_effectif")),
                rs.getObject("forfait_effectif_id", UUID.class), StatutPec.depuis(rs.getString("statut")),
                cree == null ? null : cree.toInstant());
    };

    private final JdbcTemplate jdbc;

    public PrisesEnChargeJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PriseEnCharge> trouver(CentreId centre, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM prise_en_charge WHERE id = ? AND center_id = ?", LIGNE, id, centre.valeur())
                .stream().findFirst();
    }

    @Override
    public boolean identifiantPris(UUID id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM prise_en_charge WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    @Override
    public void enregistrer(PriseEnCharge p) {
        int mis = jdbc.update("UPDATE prise_en_charge SET date_debut_demande = ?, date_fin_demande = ?, forfait_demande_id = ?, "
                        + "date_debut_effectif = ?, date_fin_effectif = ?, forfait_effectif_id = ?, statut = ? "
                        + "WHERE id = ? AND center_id = ?", date(p.demandeDebut()), date(p.demandeFin()), p.demandeForfait(),
                date(p.accordDebut()), date(p.accordFin()), p.accordForfait(), p.statut().name(), p.id(), p.centre().valeur());
        if (mis > 0) return;
        jdbc.update("INSERT INTO prise_en_charge (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)", p.id(), p.patient().valeur(),
                p.centre().valeur(), date(p.demandeDebut()), date(p.demandeFin()), p.demandeForfait(), date(p.accordDebut()),
                date(p.accordFin()), p.accordForfait(), p.statut().name(),
                p.creeLe() == null ? null : OffsetDateTime.ofInstant(p.creeLe(), ZoneOffset.UTC));
    }

    @Override
    public void supprimer(UUID id) {
        jdbc.update("DELETE FROM prise_en_charge WHERE id = ?", id);
    }

    @Override
    public List<PriseEnCharge> duPatient(CentreId centre, PatientId patient) {
        return jdbc.query("SELECT " + COLONNES + " FROM prise_en_charge WHERE center_id = ? AND patient_id = ?", LIGNE,
                centre.valeur(), patient.valeur());
    }

    @Override
    public List<PriseEnCharge> duCentre(CentreId centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM prise_en_charge WHERE center_id = ?", LIGNE, centre.valeur());
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private static LocalDate jour(Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
