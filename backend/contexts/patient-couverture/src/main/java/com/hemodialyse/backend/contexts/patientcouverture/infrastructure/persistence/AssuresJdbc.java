package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePrincipal;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL des assurés et de leurs affectations sur les tables actuelles {@code assure} et {@code assure_patient} (slice PC2 à
 * reprendre : seul le contrat du port est stable). Aucune écriture hors de ces deux tables.
 */
public class AssuresJdbc implements AssuresDuCentre {

    private static final String ASSURE = "SELECT numero_assurance, nom, prenom, sexe, date_naissance, tel_personnel, "
            + "tel_mobile, tel_bureau, adresse, groupe_sanguin, center_id FROM assure";
    private static final String AFFECTATION = "SELECT id, patient_id, numero_assurance, center_id, is_primary, "
            + "date_affectation, date_debut_affectation, date_fin_affectation FROM assure_patient";

    private static final RowMapper<Assure> LIGNE_ASSURE = (rs, n) -> {
        Date naissance = rs.getDate("date_naissance");
        return new Assure(rs.getString("numero_assurance"), rs.getString("nom"), rs.getString("prenom"), rs.getString("sexe"),
                naissance == null ? null : naissance.toLocalDate(), rs.getString("tel_personnel"), rs.getString("tel_mobile"),
                rs.getString("tel_bureau"), rs.getString("adresse"), rs.getString("groupe_sanguin"),
                rs.getObject("center_id", UUID.class));
    };

    private static final RowMapper<Affectation> LIGNE_AFFECTATION = (rs, n) -> {
        Date debut = rs.getDate("date_debut_affectation");
        Date fin = rs.getDate("date_fin_affectation");
        return new Affectation(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getString("numero_assurance"), rs.getObject("center_id", UUID.class), rs.getBoolean("is_primary"),
                rs.getObject("date_affectation", OffsetDateTime.class), debut == null ? null : debut.toLocalDate(),
                fin == null ? null : fin.toLocalDate());
    };

    private final JdbcTemplate jdbc;

    public AssuresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Assure> rechercher(CentreId centre, String texte) {
        if (texte == null || texte.isEmpty()) {
            return jdbc.query(ASSURE + " WHERE center_id = ? ORDER BY nom ASC, prenom ASC", LIGNE_ASSURE, centre.valeur());
        }
        String like = "%" + texte.toLowerCase(Locale.ROOT) + "%";
        return jdbc.query(ASSURE + " WHERE center_id = ? AND (LOWER(numero_assurance) LIKE ? OR LOWER(COALESCE(nom, '')) LIKE ? "
                + "OR LOWER(COALESCE(prenom, '')) LIKE ?) ORDER BY nom ASC, prenom ASC", LIGNE_ASSURE, centre.valeur(), like,
                like, like);
    }

    @Override
    public Optional<Assure> trouver(String numeroAssurance) {
        if (numeroAssurance == null || numeroAssurance.isBlank()) return Optional.empty();
        return jdbc.query(ASSURE + " WHERE numero_assurance = ?", LIGNE_ASSURE, numeroAssurance).stream().findFirst();
    }

    @Override
    public void mettreAJour(Assure a) {
        jdbc.update("UPDATE assure SET nom = ?, prenom = ?, sexe = ?, date_naissance = ?, tel_personnel = ?, tel_mobile = ?, "
                        + "tel_bureau = ?, adresse = ?, groupe_sanguin = ? WHERE numero_assurance = ?",
                a.nom(), a.prenom(), a.sexe(), a.dateNaissance() == null ? null : Date.valueOf(a.dateNaissance()),
                a.telPersonnel(), a.telMobile(), a.telBureau(), a.adresse(), a.groupeSanguin(), a.numeroAssurance());
    }

    @Override
    public List<Affectation> historique(CentreId centre, PatientId patient) {
        return jdbc.query(AFFECTATION + " WHERE center_id = ? AND patient_id = ? ORDER BY date_affectation DESC",
                LIGNE_AFFECTATION, centre.valeur(), patient.valeur());
    }

    @Override
    public Optional<Affectation> trouverAffectation(UUID id) {
        return jdbc.query(AFFECTATION + " WHERE id = ?", LIGNE_AFFECTATION, id).stream().findFirst();
    }

    @Override
    public void fermerLAffectationPrincipale(CentreId centre, PatientId patient, LocalDate fin) {
        jdbc.update("UPDATE assure_patient SET is_primary = false, date_fin_affectation = ? WHERE center_id = ? AND patient_id = ? "
                + "AND is_primary = true AND date_fin_affectation IS NULL", Date.valueOf(fin), centre.valeur(), patient.valeur());
    }

    @Override
    public UUID ajouterUneAffectationPrincipale(CentreId centre, PatientId patient, String numeroAssurance, LocalDate debut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation, "
                        + "date_debut_affectation, date_fin_affectation) VALUES (?,?,?,?,true,?,?,NULL)", id, patient.valeur(),
                numeroAssurance, centre.valeur(), OffsetDateTime.now(ZoneOffset.UTC), Date.valueOf(debut));
        return id;
    }

    @Override
    public Optional<Affectation> principalActuel(CentreId centre, PatientId patient) {
        return jdbc.query(AFFECTATION + " WHERE center_id = ? AND patient_id = ? AND is_primary = true "
                + "ORDER BY date_affectation DESC", LIGNE_AFFECTATION, centre.valeur(), patient.valeur()).stream().findFirst();
    }

    @Override
    public void retirerLePrincipal(CentreId centre, PatientId patient) {
        jdbc.update("UPDATE assure_patient SET is_primary = false WHERE center_id = ? AND patient_id = ?", centre.valeur(),
                patient.valeur());
    }

    @Override
    public void declarer(Assure a) {
        Date naissance = a.dateNaissance() == null ? null : Date.valueOf(a.dateNaissance());
        int mis = jdbc.update("UPDATE assure SET nom = ?, prenom = ?, sexe = ?, date_naissance = COALESCE(?, date_naissance), "
                        + "tel_personnel = ?, tel_mobile = ?, tel_bureau = ?, adresse = ?, groupe_sanguin = ? "
                        + "WHERE numero_assurance = ? AND center_id = ?", a.nom(), a.prenom(), a.sexe(), naissance, a.telPersonnel(),
                a.telMobile(), a.telBureau(), a.adresse(), a.groupeSanguin(), a.numeroAssurance(), a.centre());
        if (mis > 0) return;
        // Jamais d'écrasement d'un assuré d'un autre centre, même si ce centre l'a inséré entre le contrôle et cette écriture
        // (verrous de centres différents) : l'insertion ne fait rien si le numéro existe (ou si l'autre centre le crée au même
        // instant : violation de la clé primaire), le refus 422 suit. Portable H2 et PostgreSQL (pas de ON CONFLICT).
        int insere;
        try {
            insere = jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, prenom, sexe, date_naissance, "
                            + "tel_personnel, tel_mobile, tel_bureau, adresse, groupe_sanguin, created_at) "
                            + "SELECT ?,?,?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM assure WHERE numero_assurance = ?)",
                    a.numeroAssurance(), a.centre(), a.nom(), a.prenom(), a.sexe(), naissance, a.telPersonnel(), a.telMobile(),
                    a.telBureau(), a.adresse(), a.groupeSanguin(), OffsetDateTime.now(ZoneOffset.UTC), a.numeroAssurance());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // L'autre centre vient de créer ce numéro (le même centre est sérialisé par son verrou) : la transaction est abîmée
            // sur PostgreSQL, on ne relit pas, le refus suit et annule la fiche.
            throw AssurePrincipal.captureRefusee();
        }
        if (insere > 0) return;
        UUID centreDeLAssure = jdbc.query("SELECT center_id FROM assure WHERE numero_assurance = ?",
                (rs, n) -> rs.getObject("center_id", UUID.class), a.numeroAssurance()).stream().findFirst().orElse(null);
        AssurePrincipal.verifierQueLAssureEstDuCentre(centreDeLAssure, new CentreId(a.centre()));
    }

    @Override
    public UUID lierSansDates(CentreId centre, PatientId patient, String numeroAssurance) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation) "
                        + "VALUES (?,?,?,?,true,?)", id, patient.valeur(), numeroAssurance, centre.valeur(),
                OffsetDateTime.now(ZoneOffset.UTC));
        return id;
    }

    @Override
    public void modifierLesDates(UUID affectation, LocalDate debut, LocalDate fin) {
        jdbc.update("UPDATE assure_patient SET date_debut_affectation = ?, date_fin_affectation = ? WHERE id = ?",
                debut == null ? null : Date.valueOf(debut), fin == null ? null : Date.valueOf(fin), affectation);
    }
}
