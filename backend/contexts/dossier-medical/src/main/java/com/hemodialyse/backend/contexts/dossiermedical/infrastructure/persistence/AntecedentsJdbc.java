package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAntecedent;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.horodatage;
import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.instant;

/**
 * Antécédents sur la table réelle {@code antecedents_medicaux} (aucune colonne ni contrainte ajoutée : pas d'unicité en base,
 * KB-MED-64 / 71 ; l'unicité du diagnostic actif est protégée par un verrou consultatif de transaction sur PostgreSQL). Une ligne dont
 * le type, le statut ou le système de codage est illisible en base (donnée incohérente) est ignorée par toutes les lectures, comptage
 * compris (le legacy répondait 500 pour toute la liste) ; son identifiant, seul, est journalisé en WARN (repérage SQL :
 * verifications-production.md section 49). Verrous : {@code SELECT ... FOR UPDATE} de la fiche puis verrou consultatif du diagnostic,
 * gardés jusqu'à la fin de la transaction ; un verrou non obtenu dans le délai (SQLSTATE 55P03) devient
 * {@link EcritureConcurrenteSurUnAntecedent}.
 */
public class AntecedentsJdbc implements Antecedents {
    private static final String LISIBLES = " AND type_antecedent IN (" + liste(TypeDAntecedent.values()) + ") AND statut_clinique IN ("
            + liste(StatutClinique.values()) + ") AND (diagnostic_code IS NULL OR diagnostic_code_system IN ("
            + liste(SystemeDeCodage.values()) + "))";
    private static final String COLONNES = "id, center_id, patient_id, type_antecedent, diagnostic_code_system, diagnostic_code, "
            + "diagnostic_code_display, libelle_libre, date_debut, date_fin, statut_clinique, severite, note, created_at, updated_at";
    private static final Logger JOURNAL = LoggerFactory.getLogger(AntecedentsJdbc.class);

    private final JdbcTemplate jdbc;
    private final VerrousJdbc verrous;

    public AntecedentsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.verrous = new VerrousJdbc(jdbc);
    }

    private static String liste(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(v -> "'" + v.name() + "'").collect(Collectors.joining(","));
    }

    @Override
    public boolean unDiagnosticActifExisteEnVerrouillant(CentreId centre, PatientId patient, CodeClinique diagnostic, UUID sauf) {
        return VerrousJdbc.verrouille(() -> {
            verrous.consultatif("antecedent|" + centre.valeur() + "|" + patient.valeur() + "|" + diagnostic.systeme() + "|" + diagnostic.code());
            Long nombre = jdbc.queryForObject("SELECT count(*) FROM antecedents_medicaux WHERE center_id = ? AND patient_id = ? "
                            + "AND statut_clinique = 'ACTIF' AND diagnostic_code_system = ? AND diagnostic_code = ?"
                            + (sauf == null ? "" : " AND id <> ?"), Long.class,
                    sauf == null ? new Object[]{centre.valeur(), patient.valeur(), diagnostic.systeme().name(), diagnostic.code()}
                            : new Object[]{centre.valeur(), patient.valeur(), diagnostic.systeme().name(), diagnostic.code(), sauf});
            return nombre != null && nombre > 0;
        }, EcritureConcurrenteSurUnAntecedent::new);
    }

    @Override
    public void ajouter(Antecedent a) {
        CodeClinique d = a.diagnostic();
        jdbc.update("INSERT INTO antecedents_medicaux (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", a.id(), a.centre().valeur(),
                a.patient().valeur(), a.type().name(), d == null ? null : d.systeme().name(), d == null ? null : d.code(),
                d == null ? null : d.affichage(), a.libelleLibre(), a.debut(), a.fin(), a.statut().name(), a.severite(), a.note(),
                horodatage(a.creeLe()), horodatage(a.modifieLe()));
    }

    @Override
    public void enregistrerLaCorrection(Antecedent a) {
        CodeClinique d = a.diagnostic();
        jdbc.update("UPDATE antecedents_medicaux SET diagnostic_code_system = ?, diagnostic_code = ?, diagnostic_code_display = ?, "
                        + "libelle_libre = ?, date_debut = ?, date_fin = ?, statut_clinique = ?, severite = ?, note = ?, updated_at = ? "
                        + "WHERE id = ? AND center_id = ? AND patient_id = ?", d == null ? null : d.systeme().name(), d == null ? null : d.code(),
                d == null ? null : d.affichage(), a.libelleLibre(), a.debut(), a.fin(), a.statut().name(), a.severite(), a.note(),
                horodatage(a.modifieLe()), a.id(), a.centre().valeur(), a.patient().valeur());
    }

    @Override
    public Optional<Antecedent> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        Optional<Antecedent> trouve = VerrousJdbc.verrouille(() -> jdbc.query("SELECT " + COLONNES
                        + " FROM antecedents_medicaux WHERE id = ? AND patient_id = ? AND center_id = ?" + LISIBLES + " FOR UPDATE",
                (rs, i) -> antecedent(rs), id, patient.valeur(), centre.valeur()).stream().findFirst(), EcritureConcurrenteSurUnAntecedent::new);
        if (trouve.isEmpty()) {
            // Limite consignée (ADR 0084) : une ligne illisible ne peut être ni corrigée ni supprimée par le module.
            signalerLesIllisibles(" AND id = ?", centre, patient, id);
        }
        return trouve;
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return jdbc.update("DELETE FROM antecedents_medicaux WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(),
                centre.valeur()) > 0;
    }

    @Override
    public PageDeFiches<Antecedent> duPatient(CentreId centre, PatientId patient, int page, int taille) {
        // Une seule requête de comptage donne les lignes lisibles ET toutes les lignes : le repérage des illisibles n'a lieu que s'il y en a.
        long[] comptes = jdbc.queryForObject("SELECT count(CASE WHEN 1 = 1" + LISIBLES + " THEN 1 END), count(*) FROM antecedents_medicaux "
                + "WHERE center_id = ? AND patient_id = ?", (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, centre.valeur(), patient.valeur());
        List<Antecedent> items = jdbc.query("SELECT " + COLONNES + " FROM antecedents_medicaux WHERE center_id = ? AND patient_id = ?" + LISIBLES
                        + " ORDER BY date_debut DESC, created_at DESC, id DESC LIMIT ? OFFSET ?", (rs, i) -> antecedent(rs),
                centre.valeur(), patient.valeur(), taille, (long) page * taille);
        if (comptes[1] > comptes[0]) signalerLesIllisibles("", centre, patient);
        return new PageDeFiches<>(items, comptes[0], page, taille);
    }

    /** Journalise en WARN l'identifiant SEUL (aucune donnée de santé ni de patient) des lignes illisibles du patient. */
    private void signalerLesIllisibles(String filtre, CentreId centre, PatientId patient, Object... id) {
        Object[] parametres = new Object[2 + id.length];
        parametres[0] = centre.valeur();
        parametres[1] = patient.valeur();
        System.arraycopy(id, 0, parametres, 2, id.length);
        for (UUID illisible : jdbc.queryForList("SELECT id FROM antecedents_medicaux WHERE center_id = ? AND patient_id = ?" + filtre
                + " AND NOT (1 = 1" + LISIBLES + ")", UUID.class, parametres)) {
            JOURNAL.warn("Antécédent {} ignoré : type, statut ou système de codage illisible en base (donnée incohérente)", illisible);
        }
    }

    private static Antecedent antecedent(ResultSet rs) throws SQLException {
        String code = rs.getString("diagnostic_code");
        CodeClinique diagnostic = code == null ? null : CodeClinique.reconstituer(SystemeDeCodage.valueOf(rs.getString("diagnostic_code_system")),
                code, rs.getString("diagnostic_code_display"));
        return Antecedent.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), TypeDAntecedent.valueOf(rs.getString("type_antecedent")), diagnostic,
                rs.getString("libelle_libre"), rs.getObject("date_debut", LocalDate.class), rs.getObject("date_fin", LocalDate.class),
                StatutClinique.valueOf(rs.getString("statut_clinique")), rs.getString("severite"), rs.getString("note"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }
}
