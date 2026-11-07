package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CriticiteDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDeVerification;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeReaction;
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
 * Allergies sur la table réelle {@code allergies_patient} (aucune colonne ni contrainte ajoutée ; aucune unicité, reproduit KB-MED-66).
 * Une ligne dont la catégorie, la criticité, le type de réaction, le statut ou le système de codage est illisible en base (donnée
 * incohérente) est ignorée par toutes les lectures, comptage compris (le legacy répondait 500 pour toute la liste) ; son identifiant,
 * seul, est journalisé en WARN (repérage SQL : verifications-production.md section 49). Verrou : {@code SELECT ... FOR UPDATE} de la
 * fiche, gardé jusqu'à la fin de la transaction ; un verrou non obtenu dans le délai (SQLSTATE 55P03) devient
 * {@link EcritureConcurrenteSurUneAllergie}.
 */
public class AllergiesJdbc implements Allergies {
    private static final String LISIBLES = " AND categorie IN (" + liste(CategorieDAllergie.values()) + ") AND criticite IN ("
            + liste(CriticiteDAllergie.values()) + ") AND type_reaction IN (" + liste(TypeDeReaction.values()) + ") AND statut_verification IN ("
            + liste(StatutDeVerification.values()) + ") AND substance_code_system IN (" + liste(SystemeDeCodage.values()) + ")";
    private static final String COLONNES = "id, center_id, patient_id, substance_code_system, substance_code, substance_code_display, categorie, "
            + "criticite, type_reaction, manifestations, date_constatation, statut_verification, created_at, updated_at";
    private static final String ORDRE = " ORDER BY date_constatation DESC, created_at DESC, id DESC";
    private static final Logger JOURNAL = LoggerFactory.getLogger(AllergiesJdbc.class);

    private final JdbcTemplate jdbc;

    public AllergiesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String liste(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(v -> "'" + v.name() + "'").collect(Collectors.joining(","));
    }

    @Override
    public void ajouter(Allergie a) {
        jdbc.update("INSERT INTO allergies_patient (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", a.id(), a.centre().valeur(),
                a.patient().valeur(), a.substance().systeme().name(), a.substance().code(), a.substance().affichage(), a.categorie().name(),
                a.criticite().name(), a.typeDeReaction().name(), a.manifestations(), a.dateDeConstatation(), a.statut().name(),
                horodatage(a.creeLe()), horodatage(a.modifieLe()));
    }

    @Override
    public void enregistrerLaCorrection(Allergie a) {
        jdbc.update("UPDATE allergies_patient SET criticite = ?, manifestations = ?, statut_verification = ?, updated_at = ? "
                        + "WHERE id = ? AND center_id = ? AND patient_id = ?", a.criticite().name(), a.manifestations(), a.statut().name(),
                horodatage(a.modifieLe()), a.id(), a.centre().valeur(), a.patient().valeur());
    }

    @Override
    public Optional<Allergie> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        Optional<Allergie> trouvee = VerrousJdbc.verrouille(() -> jdbc.query("SELECT " + COLONNES
                        + " FROM allergies_patient WHERE id = ? AND patient_id = ? AND center_id = ?" + LISIBLES + " FOR UPDATE",
                (rs, i) -> allergie(rs), id, patient.valeur(), centre.valeur()).stream().findFirst(), EcritureConcurrenteSurUneAllergie::new);
        if (trouvee.isEmpty()) {
            // Limite consignée (ADR 0084) : une ligne illisible ne peut être ni corrigée ni supprimée par le module.
            signalerLesIllisibles(" AND id = ?", centre, patient, id);
        }
        return trouvee;
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return jdbc.update("DELETE FROM allergies_patient WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(),
                centre.valeur()) > 0;
    }

    @Override
    public PageDeFiches<Allergie> duPatient(CentreId centre, PatientId patient, int page, int taille) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM allergies_patient WHERE center_id = ? AND patient_id = ?" + LISIBLES, Long.class,
                centre.valeur(), patient.valeur());
        List<Allergie> items = jdbc.query("SELECT " + COLONNES + " FROM allergies_patient WHERE center_id = ? AND patient_id = ?" + LISIBLES
                        + ORDRE + " LIMIT ? OFFSET ?", (rs, i) -> allergie(rs), centre.valeur(), patient.valeur(), taille, (long) page * taille);
        signalerLesIllisibles("", centre, patient);
        return new PageDeFiches<>(items, total == null ? 0 : total, page, taille);
    }

    @Override
    public List<Allergie> critiquesDuPatient(CentreId centre, PatientId patient) {
        List<Allergie> lues = jdbc.query("SELECT " + COLONNES + " FROM allergies_patient WHERE center_id = ? AND patient_id = ? "
                + "AND criticite = 'HAUTE'" + LISIBLES + ORDRE, (rs, i) -> allergie(rs), centre.valeur(), patient.valeur());
        signalerLesIllisibles("", centre, patient);
        return lues;
    }

    /** Journalise en WARN l'identifiant SEUL (aucune donnée de santé ni de patient) des lignes illisibles du patient. */
    private void signalerLesIllisibles(String filtre, CentreId centre, PatientId patient, Object... id) {
        Object[] parametres = new Object[2 + id.length];
        parametres[0] = centre.valeur();
        parametres[1] = patient.valeur();
        System.arraycopy(id, 0, parametres, 2, id.length);
        for (UUID illisible : jdbc.queryForList("SELECT id FROM allergies_patient WHERE center_id = ? AND patient_id = ?" + filtre
                + " AND NOT (1 = 1" + LISIBLES + ")", UUID.class, parametres)) {
            JOURNAL.warn("Allergie {} ignorée : catégorie, criticité, type, statut ou système de codage illisible en base (donnée incohérente)",
                    illisible);
        }
    }

    private static Allergie allergie(ResultSet rs) throws SQLException {
        var substance = CodeClinique.reconstituer(SystemeDeCodage.valueOf(rs.getString("substance_code_system")), rs.getString("substance_code"),
                rs.getString("substance_code_display"));
        return Allergie.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), substance, CategorieDAllergie.valueOf(rs.getString("categorie")),
                CriticiteDAllergie.valueOf(rs.getString("criticite")), TypeDeReaction.valueOf(rs.getString("type_reaction")),
                rs.getString("manifestations"), rs.getObject("date_constatation", LocalDate.class),
                StatutDeVerification.valueOf(rs.getString("statut_verification")), instant(rs, "created_at"), instant(rs, "updated_at"));
    }
}
