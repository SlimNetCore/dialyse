package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.PatientsDeLaReprise;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients.PatientDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter de {@link PatientsDeLaReprise} sur la table {@code patients} : les colonnes que la reprise renseigne, jamais les pièces
 * jointes, la photo, l'EPO, le fer ni l'historique de l'assuré. Les colonnes de PLACE ({@code salle_id}, {@code position_id},
 * {@code generateur_id}, {@code jour_*}) sont écrites ici par la reprise seule, sans contrôle de la Planification (hors activité du
 * centre, ADR 0134). Toute requête de fiche est bornée au centre.
 */
public class PatientsDeLaRepriseJdbc implements PatientsDeLaReprise {

    private static final List<DayOfWeek> SEMAINE = List.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    /** Colonnes de la fiche reprises, dans l'ordre de {@link #valeurs(Fiche)}. */
    private static final String[] COLONNES = {"code_patient", "numero_assurance", "nom", "prenom", "sexe", "date_naissance",
            "date_admission", "type_patient", "etat_patient", "qualite_assure", "civilite", "lieu_naissance", "situation_familiale",
            "nombre_enfants", "profession1", "adresse", "tel_personnel", "tel_mobile", "tel_bureau", "email", "groupe_sanguin",
            "date_evenement_etat", "observation", "centre_payeur_id", "medecin_traitant_id", "salle_id", "position_id",
            "generateur_id", "transporteur_aller_id", "transporteur_retour_id", "jour_dimanche", "jour_lundi", "jour_mardi",
            "jour_mercredi", "jour_jeudi", "jour_vendredi", "jour_samedi", "assure_numero_assurance", "assure_sexe", "assure_nom",
            "assure_prenom", "assure_date_naissance", "assure_tel_personnel", "assure_adresse", "assure_groupe_sanguin",
            "assure_tel_mobile", "assure_tel_bureau"};

    private static final String LECTURE = "SELECT id, " + String.join(", ", COLONNES) + " FROM patients WHERE center_id = ? AND id = ?";

    private static final String MISE_A_JOUR = "UPDATE patients SET " + String.join(" = ?, ", COLONNES)
            + " = ? WHERE id = ? AND center_id = ?";

    private static final String INSERTION = "INSERT INTO patients (id, center_id, created_at, en_sommeil, sous_kt, epo_enabled, "
            + "fer_enabled, " + String.join(", ", COLONNES) + ") VALUES (?, ?, ?, false, false, false, false"
            + ", ?".repeat(COLONNES.length) + ")";

    private final JdbcTemplate jdbc;

    public PatientsDeLaRepriseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private static LocalDate jour(ResultSet rs, String colonne) throws SQLException {
        Date d = rs.getDate(colonne);
        return d == null ? null : d.toLocalDate();
    }

    private static Object[] valeurs(Fiche f) {
        AssureSurLaFiche a = f.assure();
        Object[] jours = SEMAINE.stream().map(j -> f.jours().contains(j)).toArray();
        List<Object> v = new ArrayList<>(Arrays.asList(f.code(), f.numeroAssurance(), f.nom(), f.prenom(), f.sexe(),
                date(f.dateNaissance()), date(f.dateAdmission()), f.vacancier() ? "VACANCIER" : "NON_VACANCIER", f.etat(),
                f.qualiteAssure(), f.civilite(), f.lieuNaissance(), f.situationFamiliale(), f.nombreEnfants(), f.profession(),
                f.adresse(), f.telPersonnel(), f.telMobile(), f.telBureau(), f.email(), f.groupeSanguin(),
                date(f.dateEvenementEtat()), f.observation(), f.centrePayeur(), f.medecinTraitant(), f.salle(), f.creneau(),
                f.generateur(), f.transporteurAller(), f.transporteurRetour()));
        v.addAll(Arrays.asList(jours));
        v.addAll(Arrays.asList(f.assureNumeroAssurance(), a.sexe(), a.nom(), a.prenom(), date(a.dateNaissance()),
                a.telPersonnel(), a.adresse(), a.groupeSanguin(), a.telMobile(), a.telBureau()));
        return v.toArray();
    }

    private static final RowMapper<Fiche> LIGNE = (rs, n) -> {
        Set<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
        String[] colonnesJours = {"jour_dimanche", "jour_lundi", "jour_mardi", "jour_mercredi", "jour_jeudi", "jour_vendredi",
                "jour_samedi"};
        for (int i = 0; i < colonnesJours.length; i++) {
            if (rs.getBoolean(colonnesJours[i])) jours.add(SEMAINE.get(i));
        }
        return new Fiche(rs.getObject("id", UUID.class), rs.getString("code_patient"), rs.getString("numero_assurance"),
                rs.getString("nom"), rs.getString("prenom"), rs.getString("sexe"), jour(rs, "date_naissance"),
                jour(rs, "date_admission"), "VACANCIER".equals(rs.getString("type_patient")), rs.getString("etat_patient"),
                rs.getString("qualite_assure"), rs.getString("civilite"), rs.getString("lieu_naissance"),
                rs.getString("situation_familiale"), rs.getInt("nombre_enfants"), rs.getString("profession1"),
                rs.getString("adresse"), rs.getString("tel_personnel"), rs.getString("tel_mobile"), rs.getString("tel_bureau"),
                rs.getString("email"), rs.getString("groupe_sanguin"), jour(rs, "date_evenement_etat"), rs.getString("observation"),
                rs.getObject("centre_payeur_id", UUID.class), rs.getObject("medecin_traitant_id", UUID.class),
                rs.getObject("salle_id", UUID.class), rs.getObject("position_id", UUID.class),
                rs.getObject("generateur_id", UUID.class), rs.getObject("transporteur_aller_id", UUID.class),
                rs.getObject("transporteur_retour_id", UUID.class), jours, rs.getString("assure_numero_assurance"),
                new AssureSurLaFiche(rs.getString("assure_sexe"), rs.getString("assure_nom"), rs.getString("assure_prenom"),
                        jour(rs, "assure_date_naissance"), rs.getString("assure_tel_personnel"), rs.getString("assure_adresse"),
                        rs.getString("assure_groupe_sanguin"), rs.getString("assure_tel_mobile"),
                        rs.getString("assure_tel_bureau")));
    };

    @Override
    public List<PatientDuCentre> duCentre(CentreId centre) {
        return jdbc.query("SELECT id, numero_assurance, code_patient, nom, prenom, date_naissance FROM patients "
                        + "WHERE center_id = ? ORDER BY id",
                (rs, n) -> new PatientDuCentre(rs.getObject("id", UUID.class), rs.getString("numero_assurance"),
                        rs.getString("code_patient"), rs.getString("nom"), rs.getString("prenom"), jour(rs, "date_naissance")),
                centre.valeur());
    }

    @Override
    public Optional<Fiche> trouver(CentreId centre, UUID id) {
        return jdbc.query(LECTURE, LIGNE, centre.valeur(), id).stream().findFirst();
    }

    @Override
    public boolean identifiantPris(UUID id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    @Override
    public void creer(CentreId centre, Fiche fiche, Instant creeLe) {
        Object[] valeurs = valeurs(fiche);
        Object[] args = new Object[valeurs.length + 3];
        args[0] = fiche.id();
        args[1] = centre.valeur();
        args[2] = OffsetDateTime.ofInstant(creeLe, ZoneOffset.UTC);
        System.arraycopy(valeurs, 0, args, 3, valeurs.length);
        jdbc.update(INSERTION, args);
    }

    @Override
    public void mettreAJour(CentreId centre, Fiche fiche) {
        Object[] valeurs = valeurs(fiche);
        Object[] args = Arrays.copyOf(valeurs, valeurs.length + 2);
        args[valeurs.length] = fiche.id();
        args[valeurs.length + 1] = centre.valeur();
        jdbc.update(MISE_A_JOUR, args);
    }
}
