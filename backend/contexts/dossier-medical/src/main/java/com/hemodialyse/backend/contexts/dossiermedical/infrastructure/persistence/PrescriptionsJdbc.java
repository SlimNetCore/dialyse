package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ContenuDePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDePrescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionMedicale;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Traitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.UniteDeFrequence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Prescriptions sur la table réelle {@code prescriptions_medicales} (aucune colonne ajoutée). Toutes les lectures filtrent par
 * patient ET centre ; l'ordre est la date de prescription décroissante, puis la création et l'identifiant décroissants (départage
 * ajouté : le legacy laissait l'égalité au SGBD). Verrou de fiche : {@code SELECT ... FOR UPDATE} jusqu'à la fin de la transaction.
 */
public class PrescriptionsJdbc implements Prescriptions {

    private static final String COLONNES = "id, patient_id, center_id, date_prescription, medecin_id, qb_cible, qd_cible, uf_max_ml, "
            + "duree_cible_min, poids_sec_cible_kg, type_dialyseur_prescrit, anticoag_type_prescrit, epo_article_id, epo_dose_ui, "
            + "epo_voie, epo_frequence_valeur, epo_frequence_unite, fer_article_id, fer_dose_mg, fer_voie, fer_frequence_valeur, "
            + "fer_frequence_unite, created_at, updated_at";
    private static final String ORDRE = " ORDER BY date_prescription DESC, created_at DESC, id DESC";

    private final JdbcTemplate jdbc;

    public PrescriptionsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(PrescriptionMedicale p) {
        ContenuDePrescription c = p.contenu();
        jdbc.update("INSERT INTO prescriptions_medicales (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p.id(), p.patient().valeur(), p.centre().valeur(), c.date(), c.medecin(), c.qbCible(), c.qdCible(), c.ufMaxMl(),
                c.dureeCibleMin(), c.poidsSecCibleKg(), c.typeDialyseur(), c.anticoagulant(), c.epo().article(), c.epo().dose(),
                c.epo().voie(), c.epo().frequenceValeur(), nom(c.epo()), c.fer().article(), c.fer().dose(), c.fer().voie(),
                c.fer().frequenceValeur(), nom(c.fer()), horodatage(p.creeeLe()), horodatage(p.modifieeLe()));
    }

    @Override
    public void enregistrerLeRemplacement(PrescriptionMedicale p) {
        ContenuDePrescription c = p.contenu();
        jdbc.update("UPDATE prescriptions_medicales SET date_prescription = ?, medecin_id = ?, qb_cible = ?, qd_cible = ?, uf_max_ml = ?, "
                        + "duree_cible_min = ?, poids_sec_cible_kg = ?, type_dialyseur_prescrit = ?, anticoag_type_prescrit = ?, "
                        + "epo_article_id = ?, epo_dose_ui = ?, epo_voie = ?, epo_frequence_valeur = ?, epo_frequence_unite = ?, "
                        + "fer_article_id = ?, fer_dose_mg = ?, fer_voie = ?, fer_frequence_valeur = ?, fer_frequence_unite = ?, "
                        + "created_at = ?, updated_at = ? WHERE id = ? AND patient_id = ? AND center_id = ?",
                c.date(), c.medecin(), c.qbCible(), c.qdCible(), c.ufMaxMl(), c.dureeCibleMin(), c.poidsSecCibleKg(), c.typeDialyseur(),
                c.anticoagulant(), c.epo().article(), c.epo().dose(), c.epo().voie(), c.epo().frequenceValeur(), nom(c.epo()),
                c.fer().article(), c.fer().dose(), c.fer().voie(), c.fer().frequenceValeur(), nom(c.fer()),
                horodatage(p.creeeLe()), horodatage(p.modifieeLe()), p.id(), p.patient().valeur(), p.centre().valeur());
    }

    @Override
    public Optional<PrescriptionMedicale> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        try {
            return jdbc.query("SELECT " + COLONNES + " FROM prescriptions_medicales WHERE id = ? AND patient_id = ? AND center_id = ? FOR UPDATE",
                    (rs, i) -> prescription(rs), id, patient.valeur(), centre.valeur()).stream().findFirst();
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrente();
        }
    }

    @Override
    public void supprimer(CentreId centre, PatientId patient, UUID id) {
        jdbc.update("DELETE FROM prescriptions_medicales WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(), centre.valeur());
    }

    @Override
    public PageDePrescriptions duPatient(CentreId centre, PatientId patient, Periode periode, int page, int taille) {
        List<Object> args = filtre(centre, patient, periode);
        Long total = jdbc.queryForObject("SELECT count(*) FROM prescriptions_medicales WHERE " + clause(periode), Long.class, args.toArray());
        args.add(taille);
        args.add((long) page * taille);
        List<PrescriptionMedicale> items = jdbc.query("SELECT " + COLONNES + " FROM prescriptions_medicales WHERE " + clause(periode)
                + ORDRE + " LIMIT ? OFFSET ?", (rs, i) -> prescription(rs), args.toArray());
        return new PageDePrescriptions(items, total == null ? 0 : total, page, taille);
    }

    @Override
    public List<PrescriptionMedicale> duPatientSurLaPeriode(CentreId centre, PatientId patient, Periode periode) {
        return jdbc.query("SELECT " + COLONNES + " FROM prescriptions_medicales WHERE " + clause(periode) + ORDRE,
                (rs, i) -> prescription(rs), filtre(centre, patient, periode).toArray());
    }

    @Override
    public List<PrescriptionMedicale> lesPlusRecentesDuCentre(CentreId centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM prescriptions_medicales p WHERE p.center_id = ? AND p.date_prescription = "
                + "(SELECT MAX(m.date_prescription) FROM prescriptions_medicales m WHERE m.center_id = p.center_id AND m.patient_id = p.patient_id) "
                + "ORDER BY p.patient_id, p.id", (rs, i) -> prescription(rs), centre.valeur());
    }

    private static String clause(Periode periode) {
        return "patient_id = ? AND center_id = ?" + (periode.du() != null ? " AND date_prescription >= ?" : "")
                + (periode.au() != null ? " AND date_prescription <= ?" : "");
    }

    private static List<Object> filtre(CentreId centre, PatientId patient, Periode periode) {
        List<Object> args = new ArrayList<>(List.of(patient.valeur(), centre.valeur()));
        if (periode.du() != null) args.add(periode.du());
        if (periode.au() != null) args.add(periode.au());
        return args;
    }

    private static PrescriptionMedicale prescription(ResultSet rs) throws SQLException {
        var contenu = new ContenuDePrescription(rs.getObject("date_prescription", LocalDate.class), rs.getObject("medecin_id", UUID.class),
                rs.getObject("qb_cible", Integer.class), rs.getObject("qd_cible", Integer.class), rs.getObject("uf_max_ml", Integer.class),
                rs.getObject("duree_cible_min", Integer.class), rs.getBigDecimal("poids_sec_cible_kg"),
                rs.getString("type_dialyseur_prescrit"), rs.getString("anticoag_type_prescrit"),
                traitement(rs, "epo_article_id", "epo_dose_ui", "epo_voie", "epo_frequence_valeur", "epo_frequence_unite"),
                traitement(rs, "fer_article_id", "fer_dose_mg", "fer_voie", "fer_frequence_valeur", "fer_frequence_unite"));
        return PrescriptionMedicale.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), contenu, instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Traitement traitement(ResultSet rs, String article, String dose, String voie, String valeur, String unite) throws SQLException {
        String lue = rs.getString(unite);
        return new Traitement(rs.getObject(article, UUID.class), rs.getObject(dose, Integer.class), rs.getString(voie),
                rs.getObject(valeur, Integer.class), lue == null ? null : UniteDeFrequence.valueOf(lue));
    }

    private static String nom(Traitement t) {
        return t.frequence() == null ? null : t.frequence().name();
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime lu = rs.getObject(colonne, OffsetDateTime.class);
        return lu == null ? null : lu.toInstant();
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
