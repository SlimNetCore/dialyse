package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDEtape;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.horodatage;
import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.instant;

/**
 * Étapes du bilan pré-greffe sur la table réelle {@code etapes_bilan_pre_greffe} (aucune colonne ni contrainte ajoutée ; la double
 * génération est évitée par un verrou consultatif du patient sur PostgreSQL). Une ligne dont la catégorie ou le statut est illisible est
 * ignorée par les lectures (le legacy répondait 400 pour toute la liste), son identifiant SEUL est journalisé en WARN ; elle compte pour
 * la génération et reste supprimable (repérage SELECT seule : {@code verifications-production.md} section 51). Tri de la liste : par la
 * collation de la base (H2 : points de code, PostgreSQL : à vérifier, KB-MED-97), départage par création puis identifiant.
 */
public class EtapesDeBilanJdbc implements EtapesDeBilan {
    private static final Logger JOURNAL = LoggerFactory.getLogger(EtapesDeBilanJdbc.class);
    private static final String COLONNES = "id, patient_id, center_id, categorie, libelle, statut, date_realisation, resultat, date_expiration, "
            + "demande_examen_id, serologie_id, created_at, updated_at";

    private final JdbcTemplate jdbc;
    private final VerrousJdbc verrous;

    public EtapesDeBilanJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.verrous = new VerrousJdbc(jdbc);
    }

    @Override
    public List<EtapeDeBilan> duPatient(CentreId centre, PatientId patient) {
        List<EtapeDeBilan> etapes = new ArrayList<>();
        jdbc.query("SELECT " + COLONNES + " FROM etapes_bilan_pre_greffe WHERE patient_id = ? AND center_id = ? "
                + "ORDER BY categorie, libelle, created_at, id", rs -> {
            lire(rs).ifPresent(etapes::add);
        }, patient.valeur(), centre.valeur());
        return etapes;
    }

    @Override
    public Set<String> clesDuPatient(CentreId centre, PatientId patient) {
        return new HashSet<>(jdbc.query("SELECT categorie, libelle FROM etapes_bilan_pre_greffe WHERE patient_id = ? AND center_id = ?",
                (rs, i) -> rs.getString("categorie") + "|" + rs.getString("libelle"), patient.valeur(), centre.valeur()));
    }

    @Override
    public void verrouillerLaGeneration(CentreId centre, PatientId patient) {
        VerrousJdbc.verrouille(() -> {
            verrous.consultatif("etapes-pre-greffe|" + centre.valeur() + "|" + patient.valeur());
            return Boolean.TRUE;
        }, EcritureConcurrenteSurUneEtapeDeBilan::new);
    }

    @Override
    public void ajouter(EtapeDeBilan e) {
        jdbc.update("INSERT INTO etapes_bilan_pre_greffe (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", e.id(), e.patient().valeur(),
                e.centre().valeur(), e.categorie().name(), e.libelle(), e.statut().name(), e.dateRealisation(), e.resultat(),
                e.dateExpiration(), e.demandeExamenId(), e.serologieId(), horodatage(e.creeLe()), horodatage(e.modifieLe()));
    }

    @Override
    public void enregistrer(EtapeDeBilan e) {
        jdbc.update("UPDATE etapes_bilan_pre_greffe SET statut = ?, date_realisation = ?, resultat = ?, date_expiration = ?, "
                        + "demande_examen_id = ?, serologie_id = ?, updated_at = ? WHERE id = ? AND patient_id = ? AND center_id = ?",
                e.statut().name(), e.dateRealisation(), e.resultat(), e.dateExpiration(), e.demandeExamenId(), e.serologieId(),
                horodatage(e.modifieLe()), e.id(), e.patient().valeur(), e.centre().valeur());
    }

    @Override
    public Optional<EtapeDeBilan> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return VerrousJdbc.verrouille(() -> {
            List<EtapeDeBilan> lues = new ArrayList<>();
            jdbc.query("SELECT " + COLONNES + " FROM etapes_bilan_pre_greffe WHERE id = ? AND patient_id = ? AND center_id = ? FOR UPDATE",
                    rs -> {
                        lire(rs).ifPresent(lues::add);
                    }, id, patient.valeur(), centre.valeur());
            return lues.stream().findFirst();
        }, EcritureConcurrenteSurUneEtapeDeBilan::new);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return jdbc.update("DELETE FROM etapes_bilan_pre_greffe WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(),
                centre.valeur()) > 0;
    }

    private Optional<EtapeDeBilan> lire(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        CategorieDEtape categorie;
        StatutDEtape statut;
        try {
            categorie = CategorieDEtape.valueOf(rs.getString("categorie"));
            statut = StatutDEtape.valueOf(rs.getString("statut"));
        } catch (IllegalArgumentException | NullPointerException e) {
            JOURNAL.warn("Étape de bilan pré-greffe {} ignorée : catégorie ou statut illisible en base (donnée incohérente)", id);
            return Optional.empty();
        }
        return Optional.of(EtapeDeBilan.reconstituer(id, new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), categorie, rs.getString("libelle"), statut,
                rs.getObject("date_realisation", LocalDate.class), rs.getString("resultat"), rs.getObject("date_expiration", LocalDate.class),
                rs.getObject("demande_examen_id", UUID.class), rs.getObject("serologie_id", UUID.class), instant(rs, "created_at"),
                instant(rs, "updated_at")));
    }
}
