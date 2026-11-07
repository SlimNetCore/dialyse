package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LienDeParente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatDeCrossmatch;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanDuDonneur;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.horodatage;
import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.instant;

/**
 * Donneurs vivants sur la table réelle {@code donneurs_vivants} (aucune colonne ni contrainte ajoutée). Une ligne dont le lien de parenté,
 * le statut ou le crossmatch est illisible est ignorée par les lectures (le legacy répondait 400 pour toute la liste), son identifiant SEUL
 * est journalisé en WARN ; elle reste supprimable (repérage SELECT seule : {@code verifications-production.md} section 51). Tri : création
 * décroissante puis identifiant.
 */
public class DonneursVivantsJdbc implements DonneursVivants {
    private static final Logger JOURNAL = LoggerFactory.getLogger(DonneursVivantsJdbc.class);
    private static final String COLONNES = "id, patient_id, center_id, nom, prenom, date_naissance, lien_parente, telephone, groupe_sanguin, "
            + "typage_hla, statut_bilan, crossmatch_resultat, date_crossmatch, bilan_realise, contre_indications, decision_finale, "
            + "date_decision, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public DonneursVivantsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DonneurVivant> duPatient(CentreId centre, PatientId patient) {
        List<DonneurVivant> donneurs = new ArrayList<>();
        jdbc.query("SELECT " + COLONNES + " FROM donneurs_vivants WHERE patient_id = ? AND center_id = ? ORDER BY created_at DESC, id", rs -> {
            lire(rs).ifPresent(donneurs::add);
        }, patient.valeur(), centre.valeur());
        return donneurs;
    }

    @Override
    public void ajouter(DonneurVivant d) {
        jdbc.update("INSERT INTO donneurs_vivants (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", d.id(), d.patient().valeur(),
                d.centre().valeur(), d.nom(), d.prenom(), d.dateNaissance(), d.lienDeParente().name(), d.telephone(), d.groupeSanguin(),
                d.typageHla(), d.statutBilan().name(), d.crossmatch().name(), d.dateCrossmatch(), d.bilanRealise(), d.contreIndications(),
                d.decisionFinale(), d.dateDecision(), horodatage(d.creeLe()), horodatage(d.modifieLe()));
    }

    @Override
    public void enregistrer(DonneurVivant d) {
        jdbc.update("UPDATE donneurs_vivants SET nom = ?, prenom = ?, date_naissance = ?, lien_parente = ?, telephone = ?, groupe_sanguin = ?, "
                        + "typage_hla = ?, statut_bilan = ?, crossmatch_resultat = ?, date_crossmatch = ?, bilan_realise = ?, "
                        + "contre_indications = ?, decision_finale = ?, date_decision = ?, updated_at = ? "
                        + "WHERE id = ? AND patient_id = ? AND center_id = ?", d.nom(), d.prenom(), d.dateNaissance(), d.lienDeParente().name(),
                d.telephone(), d.groupeSanguin(), d.typageHla(), d.statutBilan().name(), d.crossmatch().name(), d.dateCrossmatch(),
                d.bilanRealise(), d.contreIndications(), d.decisionFinale(), d.dateDecision(), horodatage(d.modifieLe()), d.id(),
                d.patient().valeur(), d.centre().valeur());
    }

    @Override
    public Optional<DonneurVivant> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return VerrousJdbc.verrouille(() -> {
            List<DonneurVivant> lus = new ArrayList<>();
            jdbc.query("SELECT " + COLONNES + " FROM donneurs_vivants WHERE id = ? AND patient_id = ? AND center_id = ? FOR UPDATE", rs -> {
                lire(rs).ifPresent(lus::add);
            }, id, patient.valeur(), centre.valeur());
            return lus.stream().findFirst();
        }, EcritureConcurrenteSurUnDonneurVivant::new);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return jdbc.update("DELETE FROM donneurs_vivants WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(),
                centre.valeur()) > 0;
    }

    private Optional<DonneurVivant> lire(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        LienDeParente lien;
        StatutDuBilanDuDonneur statut;
        ResultatDeCrossmatch crossmatch;
        try {
            lien = LienDeParente.valueOf(rs.getString("lien_parente"));
            statut = StatutDuBilanDuDonneur.valueOf(rs.getString("statut_bilan"));
            crossmatch = ResultatDeCrossmatch.valueOf(rs.getString("crossmatch_resultat"));
        } catch (IllegalArgumentException | NullPointerException e) {
            JOURNAL.warn("Donneur vivant {} ignoré : lien de parenté, statut ou crossmatch illisible en base (donnée incohérente)", id);
            return Optional.empty();
        }
        return Optional.of(DonneurVivant.reconstituer(id, new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("nom"), rs.getString("prenom"),
                rs.getObject("date_naissance", LocalDate.class), lien, rs.getString("telephone"), rs.getString("groupe_sanguin"),
                rs.getString("typage_hla"), statut, crossmatch, rs.getObject("date_crossmatch", LocalDate.class), rs.getString("bilan_realise"),
                rs.getString("contre_indications"), rs.getString("decision_finale"), rs.getObject("date_decision", LocalDate.class),
                instant(rs, "created_at"), instant(rs, "updated_at")));
    }
}
