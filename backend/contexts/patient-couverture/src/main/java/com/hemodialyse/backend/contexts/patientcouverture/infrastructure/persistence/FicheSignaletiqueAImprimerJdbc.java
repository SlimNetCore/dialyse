package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * La fiche signalétique à imprimer sur la table réelle {@code patients} (DOC5i, ADR 0201) : la requête que le gabarit embarquait, sans la
 * jointure externe sur {@code centers} (le nom du centre vient de l'annuaire d'Organisation) ni les colonnes que le gabarit ne déclarait pas.
 */
public class FicheSignaletiqueAImprimerJdbc implements FicheSignaletiqueAImprimer {

    private static final String SELECTION = "SELECT p.code_patient, p.civilite, p.nom, p.prenom, p.sexe, p.groupe_sanguin, "
            + "p.date_naissance, p.lieu_naissance, p.date_admission, p.nombre_enfants, p.situation_familiale, p.profession1, p.adresse, "
            + "p.tel_personnel, p.tel_mobile, p.tel_bureau, p.email, p.numero_assurance, p.etat_patient, p.qualite_assure, p.observation, "
            + "p.sous_kt, p.assure_nom, p.assure_prenom FROM patients p WHERE p.id = ? AND p.center_id = ?";

    private static final RowMapper<FicheImprimable> LIGNE = (rs, i) -> new FicheImprimable(rs.getString("code_patient"),
            rs.getString("civilite"), rs.getString("nom"), rs.getString("prenom"), rs.getString("sexe"), rs.getString("groupe_sanguin"),
            rs.getObject("date_naissance", LocalDate.class), rs.getString("lieu_naissance"),
            rs.getObject("date_admission", LocalDate.class), rs.getObject("nombre_enfants", Integer.class),
            rs.getString("situation_familiale"), rs.getString("profession1"), rs.getString("adresse"), rs.getString("tel_personnel"),
            rs.getString("tel_mobile"), rs.getString("tel_bureau"), rs.getString("email"), rs.getString("numero_assurance"),
            rs.getString("etat_patient"), rs.getString("qualite_assure"), rs.getString("observation"),
            rs.getObject("sous_kt", Boolean.class), rs.getString("assure_nom"), rs.getString("assure_prenom"));

    private final JdbcTemplate jdbc;

    public FicheSignaletiqueAImprimerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FicheImprimable> duPatient(CentreId centre, UUID patient) {
        return jdbc.query(SELECTION, LIGNE, patient, centre.valeur()).stream().findFirst();
    }
}
