package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SourceDObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurMesuree;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.horodatage;
import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.instant;

/**
 * Observations biologiques sur la table réelle {@code observations_biologiques} (aucune colonne ni contrainte ajoutée, aucune clé
 * étrangère vers la demande d'examen). Une ligne dont le système de codage, le statut ou la source est illisible en base, ou dont la
 * valeur numérique n'a pas d'unité (donnée incohérente), est ignorée par toutes les lectures, comptage compris (le legacy répondait 500
 * pour toute la liste) ; son identifiant, seul, est journalisé en WARN, et seulement quand une ligne est écartée (repérage SQL :
 * verifications-production.md section 50). Verrou : {@code SELECT ... FOR UPDATE} de la ligne, gardé jusqu'à la fin de la transaction ;
 * un verrou non obtenu dans le délai (SQLSTATE 55P03) devient {@link EcritureConcurrenteSurUneObservation}.
 */
public class ObservationsBiologiquesJdbc implements ObservationsBiologiques {
    private static final String LISIBLES = " AND analyte_code_system IN (" + liste(SystemeDeCodage.values()) + ") AND statut IN ("
            + liste(StatutDObservation.values()) + ") AND source IN (" + liste(SourceDObservation.values())
            + ") AND (valeur_num IS NULL OR (unite IS NOT NULL AND TRIM(unite) <> ''))";
    private static final String COLONNES = "id, center_id, patient_id, demande_examen_id, analyte_code_system, analyte_code, "
            + "analyte_code_display, valeur_num, unite, valeur_texte, date_prelevement, statut, source, created_at, updated_at";
    private static final String ORDRE = " ORDER BY date_prelevement DESC, created_at DESC, id DESC";
    private static final String DU_PATIENT = "center_id = ? AND patient_id = ?";
    private static final Logger JOURNAL = LoggerFactory.getLogger(ObservationsBiologiquesJdbc.class);

    private final JdbcTemplate jdbc;

    public ObservationsBiologiquesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String liste(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(v -> "'" + v.name() + "'").collect(Collectors.joining(","));
    }

    /** Un filtre de lecture : condition SQL et ses paramètres. */
    private record Filtre(String sql, List<Object> parametres) {
        Filtre et(String condition, Object... valeurs) {
            List<Object> tous = new ArrayList<>(parametres);
            tous.addAll(Arrays.asList(valeurs));
            return new Filtre(sql + " AND " + condition, tous);
        }
    }

    private static Filtre duPatient(CentreId centre, PatientId patient) {
        return new Filtre(DU_PATIENT, List.of(centre.valeur(), patient.valeur()));
    }

    @Override
    public void ajouter(ObservationBiologique o) {
        var valeur = o.valeurNumerique();
        jdbc.update("INSERT INTO observations_biologiques (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", o.id(), o.centre().valeur(),
                o.patient().valeur(), o.demandeDExamen(), o.analyte().systeme().name(), o.analyte().code(), o.analyte().affichage(),
                valeur == null ? null : valeur.valeur(), valeur == null ? null : valeur.unite(), o.valeurTexte(), o.datePrelevement(),
                o.statut().name(), o.source().name(), horodatage(o.creeLe()), horodatage(o.modifieLe()));
    }

    @Override
    public void enregistrerLaCorrection(ObservationBiologique o) {
        var valeur = o.valeurNumerique();
        jdbc.update("UPDATE observations_biologiques SET valeur_num = ?, unite = ?, valeur_texte = ?, statut = ?, updated_at = ? "
                        + "WHERE id = ? AND center_id = ? AND patient_id = ?", valeur == null ? null : valeur.valeur(),
                valeur == null ? null : valeur.unite(), o.valeurTexte(), o.statut().name(), horodatage(o.modifieLe()), o.id(),
                o.centre().valeur(), o.patient().valeur());
    }

    @Override
    public Optional<ObservationBiologique> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        Filtre filtre = duPatient(centre, patient).et("id = ?", id);
        Optional<ObservationBiologique> trouvee = VerrousJdbc.verrouille(() -> jdbc.query("SELECT " + COLONNES + " FROM observations_biologiques WHERE "
                + filtre.sql() + LISIBLES + " FOR UPDATE", (rs, i) -> observation(rs), filtre.parametres().toArray()).stream().findFirst(),
                EcritureConcurrenteSurUneObservation::new);
        if (trouvee.isEmpty()) {
            // Limite consignée (ADR 0085) : une ligne illisible ne peut être ni corrigée ni supprimée par le module.
            signalerLesIllisibles(filtre);
        }
        return trouvee;
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return jdbc.update("DELETE FROM observations_biologiques WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(),
                centre.valeur()) > 0;
    }

    @Override
    public PageDeFiches<ObservationBiologique> duPatient(CentreId centre, PatientId patient, String code, LocalDate du, LocalDate au, int page,
                                                         int taille) {
        Filtre filtre = duPatient(centre, patient);
        if (code != null && !code.isBlank()) filtre = filtre.et("analyte_code = ?", code);
        if (du != null) filtre = filtre.et("date_prelevement >= ?", du);
        if (au != null) filtre = filtre.et("date_prelevement <= ?", au);
        long[] comptes = comptes(filtre);
        List<Object> parametres = new ArrayList<>(filtre.parametres());
        parametres.add(taille);
        parametres.add((long) page * taille);
        List<ObservationBiologique> items = jdbc.query("SELECT " + COLONNES + " FROM observations_biologiques WHERE " + filtre.sql() + LISIBLES
                + ORDRE + " LIMIT ? OFFSET ?", (rs, i) -> observation(rs), parametres.toArray());
        if (comptes[1] > comptes[0]) signalerLesIllisibles(filtre);
        return new PageDeFiches<>(items, comptes[0], page, taille);
    }

    @Override
    public List<ObservationBiologique> deLaDemande(CentreId centre, PatientId patient, UUID demandeDExamen) {
        Filtre filtre = duPatient(centre, patient).et("demande_examen_id = ?", demandeDExamen);
        List<ObservationBiologique> lues = jdbc.query("SELECT " + COLONNES + " FROM observations_biologiques WHERE " + filtre.sql() + LISIBLES
                + ORDRE, (rs, i) -> observation(rs), filtre.parametres().toArray());
        long[] comptes = comptes(filtre);
        if (comptes[1] > comptes[0]) signalerLesIllisibles(filtre);
        return lues;
    }

    /** Une seule requête : lignes lisibles et toutes les lignes du filtre (le repérage des illisibles n'a lieu que s'il y en a). */
    private long[] comptes(Filtre filtre) {
        return jdbc.queryForObject("SELECT count(CASE WHEN 1 = 1" + LISIBLES + " THEN 1 END), count(*) FROM observations_biologiques WHERE "
                + filtre.sql(), (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, filtre.parametres().toArray());
    }

    /** Journalise en WARN l'identifiant SEUL (aucune donnée de santé ni de patient) des lignes illisibles du filtre. */
    private void signalerLesIllisibles(Filtre filtre) {
        for (UUID illisible : jdbc.queryForList("SELECT id FROM observations_biologiques WHERE " + filtre.sql() + " AND NOT (1 = 1" + LISIBLES + ")",
                UUID.class, filtre.parametres().toArray())) {
            JOURNAL.warn("Observation {} ignorée : système de codage, statut, source ou valeur sans unité illisible en base (donnée incohérente)",
                    illisible);
        }
    }

    private static ObservationBiologique observation(ResultSet rs) throws SQLException {
        var analyte = CodeClinique.reconstituer(SystemeDeCodage.valueOf(rs.getString("analyte_code_system")), rs.getString("analyte_code"),
                rs.getString("analyte_code_display"));
        var valeur = rs.getBigDecimal("valeur_num");
        return ObservationBiologique.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), rs.getObject("demande_examen_id", UUID.class), analyte,
                valeur == null ? null : new ValeurMesuree(valeur, rs.getString("unite")), rs.getString("valeur_texte"),
                rs.getObject("date_prelevement", LocalDate.class), StatutDObservation.valueOf(rs.getString("statut")),
                SourceDObservation.valueOf(rs.getString("source")), instant(rs, "created_at"), instant(rs, "updated_at"));
    }
}
