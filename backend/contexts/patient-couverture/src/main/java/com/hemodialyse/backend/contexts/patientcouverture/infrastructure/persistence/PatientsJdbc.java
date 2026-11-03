package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Coordonnees;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Identite;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NumeroAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SuiviMedical;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter du port {@link PatientRepository} sur la table {@code patients} (les vraies colonnes : l'état est
 * {@code etat_patient}, sa date {@code date_evenement_etat}). Les colonnes de PLACE ({@code salle_id}, {@code position_id},
 * {@code generateur_id}, {@code jour_*}) sont LUES mais jamais écrites ici : la Planification les écrit (ADR 0052). Une ligne
 * héritée aux colonnes nulles se relit avec les valeurs par défaut documentées (état permanent, jours faux).
 */
public class PatientsJdbc implements PatientRepository {

    private static final String COLONNES = "id, center_id, code_patient, civilite, nom, prenom, sexe, groupe_sanguin, "
            + "nombre_enfants, date_admission, en_sommeil, date_naissance, lieu_naissance, situation_familiale, profession1, "
            + "adresse, tel_personnel, tel_mobile, tel_bureau, email, numero_assurance, type_patient, etat_patient, "
            + "date_evenement_etat, qualite_assure, observation, sous_kt, epo_enabled, epo_date, fer_enabled, fer_date, "
            + "photo_base64, centre_payeur_id, medecin_traitant_id, salle_id, position_id, transporteur_aller_id, "
            + "transporteur_retour_id, categorie_transport_id, generateur_id, jour_dimanche, jour_lundi, jour_mardi, "
            + "jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, assure_nom, assure_prenom, assure_numero_assurance, "
            + "assure_sexe, assure_date_naissance, assure_tel_personnel, assure_tel_mobile, assure_tel_bureau, assure_adresse, "
            + "assure_groupe_sanguin, assure_history_json, pieces_jointes_json, created_at";

    private static final String MISE_A_JOUR = "UPDATE patients SET civilite = ?, nom = ?, prenom = ?, sexe = ?, "
            + "groupe_sanguin = ?, nombre_enfants = ?, date_admission = ?, en_sommeil = ?, date_naissance = ?, "
            + "lieu_naissance = ?, situation_familiale = ?, profession1 = ?, adresse = ?, tel_personnel = ?, tel_mobile = ?, "
            + "tel_bureau = ?, email = ?, numero_assurance = ?, type_patient = ?, etat_patient = ?, date_evenement_etat = ?, "
            + "qualite_assure = ?, observation = ?, sous_kt = ?, epo_enabled = ?, epo_date = ?, fer_enabled = ?, fer_date = ?, "
            + "photo_base64 = ?, centre_payeur_id = ?, medecin_traitant_id = ?, transporteur_aller_id = ?, "
            + "transporteur_retour_id = ?, categorie_transport_id = ?, assure_nom = ?, assure_prenom = ?, "
            + "assure_numero_assurance = ?, assure_sexe = ?, assure_date_naissance = ?, assure_tel_personnel = ?, "
            + "assure_tel_mobile = ?, assure_tel_bureau = ?, assure_adresse = ?, assure_groupe_sanguin = ?, "
            + "assure_history_json = ?, pieces_jointes_json = ? WHERE id = ? AND center_id = ?";

    private static final String INSERTION = "INSERT INTO patients (id, center_id, code_patient, created_at, civilite, nom, "
            + "prenom, sexe, groupe_sanguin, nombre_enfants, date_admission, en_sommeil, date_naissance, lieu_naissance, "
            + "situation_familiale, profession1, adresse, tel_personnel, tel_mobile, tel_bureau, email, numero_assurance, "
            + "type_patient, etat_patient, date_evenement_etat, qualite_assure, observation, sous_kt, epo_enabled, epo_date, "
            + "fer_enabled, fer_date, photo_base64, centre_payeur_id, medecin_traitant_id, transporteur_aller_id, "
            + "transporteur_retour_id, categorie_transport_id, assure_nom, assure_prenom, assure_numero_assurance, "
            + "assure_sexe, assure_date_naissance, assure_tel_personnel, assure_tel_mobile, assure_tel_bureau, assure_adresse, "
            + "assure_groupe_sanguin, assure_history_json, pieces_jointes_json, jour_dimanche, jour_lundi, jour_mardi, "
            + "jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
            + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, false,false,false,false,false,false,false)";

    private final JdbcTemplate jdbc;

    public PatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Patient> trouver(CentreId centre, PatientId id) {
        return jdbc.query("SELECT " + COLONNES + " FROM patients WHERE center_id = ? AND id = ?", LIGNE, centre.valeur(),
                id.valeur()).stream().findFirst();
    }

    @Override
    public Optional<PatientId> trouverLeDetenteurDuNumero(CentreId centre, NumeroAssurance numero) {
        return jdbc.queryForList("SELECT id FROM patients WHERE center_id = ? AND UPPER(TRIM(numero_assurance)) = ? ORDER BY id",
                UUID.class, centre.valeur(), numero.valeur()).stream().findFirst().map(PatientId::new);
    }

    @Override
    public void enregistrer(Patient patient) {
        Fiche f = patient.fiche();
        Identite i = f.identite();
        Coordonnees k = f.coordonnees();
        SuiviMedical s = f.suivi();
        Couverture c = f.couverture();
        Affectation a = f.affectation();
        Couverture.AssureSurLaFiche as = c.assure();
        Object[] communs = {i.civilite(), i.nom(), i.prenom(), i.sexe(), i.groupeSanguin(), i.nombreEnfants(),
                date(f.dateAdmission()), s.enSommeil(), date(i.dateNaissance()), i.lieuNaissance(), i.situationFamiliale(),
                k.profession(), k.adresse(), k.telPersonnel(), k.telMobile(), k.telBureau(), k.email(),
                c.numeroAssurance().valeur(), patient.vacancier() ? "VACANCIER" : "NON_VACANCIER", patient.etatBrut(),
                date(f.dateEvenement()), c.qualiteAssure(), s.observation(), s.sousKt(), s.epoActive(), date(s.epoDate()),
                s.ferActif(), date(s.ferDate()), s.photoBase64(), c.centrePayeurId(), a.medecinTraitantId(),
                a.transporteurAllerId(), a.transporteurRetourId(), a.categorieTransportId(), as.nom(), as.prenom(),
                c.assureNumeroAssurance(), as.sexe(), date(dateOuNulle(as.dateNaissance())), as.telPersonnel(), as.telMobile(),
                as.telBureau(), as.adresse(), as.groupeSanguin(), c.assureHistoriqueJson(), c.piecesJointesJson()};
        Object[] miseAJour = java.util.Arrays.copyOf(communs, communs.length + 2);
        miseAJour[communs.length] = patient.id().valeur();
        miseAJour[communs.length + 1] = patient.centre().valeur();
        if (jdbc.update(MISE_A_JOUR, miseAJour) > 0) return;
        Object[] insertion = new Object[communs.length + 4];
        insertion[0] = patient.id().valeur();
        insertion[1] = patient.centre().valeur();
        insertion[2] = patient.code();
        insertion[3] = OffsetDateTime.ofInstant(patient.creeLe(), ZoneOffset.UTC);
        System.arraycopy(communs, 0, insertion, 4, communs.length);
        jdbc.update(INSERTION, insertion);
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private static LocalDate dateOuNulle(String texte) {
        if (texte == null || texte.isBlank()) return null;
        try {
            return LocalDate.parse(texte.trim().substring(0, Math.min(10, texte.trim().length())));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final RowMapper<Patient> LIGNE = (rs, n) -> {
        String etatEnBase = rs.getString("etat_patient");
        EtatDuPatient etat = etatLu(etatEnBase);
        String etatInconnu = etatEnBase != null && etat == null ? etatEnBase : null;
        Identite identite = new Identite(rs.getString("civilite"), rs.getString("nom"), rs.getString("prenom"),
                rs.getString("sexe"), rs.getString("groupe_sanguin"), entier(rs, "nombre_enfants"), jour(rs, "date_naissance"),
                rs.getString("lieu_naissance"), rs.getString("situation_familiale"));
        Coordonnees coordonnees = new Coordonnees(rs.getString("profession1"), rs.getString("adresse"),
                rs.getString("tel_personnel"), rs.getString("tel_mobile"), rs.getString("tel_bureau"), rs.getString("email"));
        SuiviMedical suivi = new SuiviMedical(booleen(rs, "sous_kt"), booleen(rs, "epo_enabled"), jour(rs, "epo_date"),
                booleen(rs, "fer_enabled"), jour(rs, "fer_date"), booleen(rs, "en_sommeil"), rs.getString("observation"),
                rs.getString("photo_base64"));
        LocalDate naissanceAssure = jour(rs, "assure_date_naissance");
        Couverture couverture = new Couverture(new NumeroAssurance(rs.getString("numero_assurance")),
                rs.getString("qualite_assure"), rs.getObject("centre_payeur_id", UUID.class),
                rs.getString("assure_numero_assurance"),
                new Couverture.AssureSurLaFiche(rs.getString("assure_sexe"), rs.getString("assure_nom"),
                        rs.getString("assure_prenom"), naissanceAssure == null ? null : naissanceAssure.toString(),
                        rs.getString("assure_tel_personnel"), rs.getString("assure_adresse"),
                        rs.getString("assure_groupe_sanguin"), rs.getString("assure_tel_mobile"),
                        rs.getString("assure_tel_bureau")),
                rs.getString("assure_history_json"), rs.getString("pieces_jointes_json"));
        Affectation affectation = new Affectation(rs.getObject("medecin_traitant_id", UUID.class),
                rs.getObject("salle_id", UUID.class), rs.getObject("position_id", UUID.class),
                rs.getObject("transporteur_aller_id", UUID.class), rs.getObject("transporteur_retour_id", UUID.class),
                rs.getObject("categorie_transport_id", UUID.class), rs.getObject("generateur_id", UUID.class),
                new Affectation.Jours(booleen(rs, "jour_dimanche"), booleen(rs, "jour_lundi"), booleen(rs, "jour_mardi"),
                        booleen(rs, "jour_mercredi"), booleen(rs, "jour_jeudi"), booleen(rs, "jour_vendredi"),
                        booleen(rs, "jour_samedi")));
        Fiche fiche = new Fiche(identite, coordonnees, suivi, couverture, affectation, jour(rs, "date_admission"), etat,
                jour(rs, "date_evenement_etat"));
        OffsetDateTime cree = rs.getObject("created_at", OffsetDateTime.class);
        return Patient.reconstituer(new PatientId(rs.getObject("id", UUID.class)), rs.getString("code_patient"),
                new CentreId(rs.getObject("center_id", UUID.class)), "VACANCIER".equals(rs.getString("type_patient")),
                cree == null ? null : cree.toInstant(), fiche, etatInconnu);
    };

    /** Nul pour un état nul ou hors de la liste fermée (la valeur brute est alors conservée par le patient). */
    private static EtatDuPatient etatLu(String brut) {
        if (brut == null) return null;
        try {
            return EtatDuPatient.valueOf(brut);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean booleen(ResultSet rs, String colonne) throws SQLException {
        return rs.getBoolean(colonne);
    }

    private static int entier(ResultSet rs, String colonne) throws SQLException {
        return rs.getInt(colonne);
    }

    private static LocalDate jour(ResultSet rs, String colonne) throws SQLException {
        Date d = rs.getDate(colonne);
        return d == null ? null : d.toLocalDate();
    }
}
