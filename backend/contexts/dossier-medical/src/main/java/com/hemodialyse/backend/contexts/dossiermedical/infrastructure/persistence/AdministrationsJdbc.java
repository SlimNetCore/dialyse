package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AdministrationDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DoseAdministree;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Administrations sur la table réelle {@code administrations_anemie} (aucune colonne ajoutée). Ordre : date d'administration
 * décroissante, puis création et identifiant décroissants (départage ajouté).
 */
public class AdministrationsJdbc implements Administrations {

    private static final String COLONNES = "id, patient_id, center_id, prescription_medicale_id, type_traitement, molecule, dose, unite_dose, "
            + "voie, date_administration, seance_id, administre_par, administree, motif_non_administration, article_id, quantite_article, created_at";
    private static final String ORDRE = " ORDER BY date_administration DESC, created_at DESC, id DESC";

    private final JdbcTemplate jdbc;

    public AdministrationsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(AdministrationDeTraitement a) {
        jdbc.update("INSERT INTO administrations_anemie (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", a.id(), a.patient().valeur(),
                a.centre().valeur(), a.prescription(), a.traitement().name(), a.molecule(), a.dose() == null ? null : a.dose().valeur(),
                a.dose() == null ? null : a.dose().unite(), a.voie(), a.jour(), a.seance(), a.administrePar(), a.administree(), a.motif(),
                a.article(), a.quantiteArticle(), OffsetDateTime.ofInstant(a.creeeLe(), ZoneOffset.UTC));
    }

    @Override
    public PageDAdministrations duPatient(CentreId centre, PatientId patient, int page, int taille) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM administrations_anemie WHERE center_id = ? AND patient_id = ?", Long.class,
                centre.valeur(), patient.valeur());
        List<AdministrationDeTraitement> items = jdbc.query("SELECT " + COLONNES + " FROM administrations_anemie WHERE center_id = ? AND patient_id = ?"
                + ORDRE + " LIMIT ? OFFSET ?", (rs, i) -> administration(rs), centre.valeur(), patient.valeur(), taille, (long) page * taille);
        return new PageDAdministrations(items, total == null ? 0 : total, page, taille);
    }

    @Override
    public List<AdministrationDeTraitement> lesPlusRecentes(CentreId centre, PatientId patient, int nombre) {
        return jdbc.query("SELECT " + COLONNES + " FROM administrations_anemie WHERE center_id = ? AND patient_id = ?" + ORDRE + " LIMIT ?",
                (rs, i) -> administration(rs), centre.valeur(), patient.valeur(), nombre);
    }

    @Override
    public Administre administreSur(CentreId centre, PatientId patient, TypeDeTraitement traitement, LocalDate debut, LocalDate fin) {
        Integer nombre = jdbc.queryForObject("SELECT COUNT(1) FROM administrations_anemie WHERE center_id = ? AND patient_id = ? "
                + "AND type_traitement = ? AND administree = TRUE AND date_administration BETWEEN ? AND ?", Integer.class, centre.valeur(),
                patient.valeur(), traitement.name(), debut, fin);
        Long dose = jdbc.queryForObject("SELECT COALESCE(SUM(dose), 0) FROM administrations_anemie WHERE center_id = ? AND patient_id = ? "
                + "AND type_traitement = ? AND administree = TRUE AND date_administration BETWEEN ? AND ? AND UPPER(unite_dose) = UPPER(?)",
                Long.class, centre.valeur(), patient.valeur(), traitement.name(), debut, fin, traitement.unite());
        return new Administre(nombre == null ? 0 : nombre, dose == null ? 0 : dose);
    }

    /** Même effet que {@code AdministrationTraitementRepositoryAdapter.deleteBySeanceId} (retiré par DM9) : les lignes de la séance AU centre. */
    @Override
    public int effacerCellesDeLaSeance(CentreId centre, UUID seance) {
        return jdbc.update("DELETE FROM administrations_anemie WHERE seance_id = ? AND center_id = ?", seance, centre.valeur());
    }

    private static AdministrationDeTraitement administration(ResultSet rs) throws SQLException {
        var valeur = rs.getBigDecimal("dose");
        DoseAdministree dose = valeur == null ? null : new DoseAdministree(valeur, rs.getString("unite_dose"));
        OffsetDateTime creee = rs.getObject("created_at", OffsetDateTime.class);
        Instant instant = creee == null ? null : creee.toInstant();
        return new AdministrationDeTraitement(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), rs.getObject("prescription_medicale_id", UUID.class),
                TypeDeTraitement.valueOf(rs.getString("type_traitement")), rs.getString("molecule"), dose, rs.getString("voie"),
                rs.getObject("date_administration", LocalDate.class), rs.getObject("seance_id", UUID.class), rs.getString("administre_par"),
                rs.getBoolean("administree"), rs.getString("motif_non_administration"), rs.getObject("article_id", UUID.class),
                rs.getBigDecimal("quantite_article"), instant);
    }
}
