package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DossiersMedicauxDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Dossiers médicaux de base sur la table réelle {@code dossier_medical_patient} (aucune colonne ni contrainte ajoutée ; unicité globale
 * de {@code patient_id}). Un doublon déjà présent en base pour un patient et un centre est lu sans erreur : le plus ancien est retenu,
 * son identifiant SEUL est journalisé en WARN (jamais de 500). Le verrou consultatif d'enregistrement ({@code dossier-medical|}) n'existe
 * que sur PostgreSQL ; l'unicité en base fait foi partout : une insertion refusée devient une écriture concurrente (409).
 */
public class DossiersMedicauxDeBaseJdbc implements DossiersMedicauxDeBase {
    private static final Logger JOURNAL = LoggerFactory.getLogger(DossiersMedicauxDeBaseJdbc.class);
    private static final String COLONNES = "id, patient_id, center_id, nephropathie_initiale, date_mise_en_dialyse, hepatite_b_statut, "
            + "hepatite_c_statut, observation_globale, conclusion_medicale, created_at, updated_at";
    private static final String RECHERCHE = "SELECT " + COLONNES + " FROM dossier_medical_patient WHERE patient_id = ? AND center_id = ? "
            + "ORDER BY created_at, id";

    private final JdbcTemplate jdbc;
    private final VerrousJdbc verrous;

    public DossiersMedicauxDeBaseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.verrous = new VerrousJdbc(jdbc);
    }

    @Override
    public Optional<DossierMedicalDeBase> duPatient(CentreId centre, PatientId patient) {
        return lire(RECHERCHE, centre, patient);
    }

    @Override
    public void verrouillerLEnregistrement(PatientId patient) {
        VerrousJdbc.verrouille(() -> {
            verrous.consultatif("dossier-medical|" + patient.valeur());
            return null;
        }, EcritureConcurrenteSurUnDossierMedical::new);
    }

    @Override
    public Optional<DossierMedicalDeBase> duPatientEnVerrouillant(CentreId centre, PatientId patient) {
        return VerrousJdbc.verrouille(() -> lire(RECHERCHE + " FOR UPDATE", centre, patient), EcritureConcurrenteSurUnDossierMedical::new);
    }

    @Override
    public void ajouter(DossierMedicalDeBase d) {
        try {
            jdbc.update("INSERT INTO dossier_medical_patient (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)", d.id(), d.patient().valeur(),
                    d.centre().valeur(), d.nephropathieInitiale(), d.dateMiseEnDialyse(), d.hepatiteBStatut(), d.hepatiteCStatut(),
                    d.observationGlobale(), d.conclusionMedicale(), horodatage(d.creeLe()), horodatage(d.modifieLe()));
        } catch (DataIntegrityViolationException e) {
            throw new EcritureConcurrenteSurUnDossierMedical();
        }
    }

    @Override
    public void enregistrer(DossierMedicalDeBase d) {
        jdbc.update("UPDATE dossier_medical_patient SET nephropathie_initiale = ?, date_mise_en_dialyse = ?, hepatite_b_statut = ?, "
                        + "hepatite_c_statut = ?, observation_globale = ?, conclusion_medicale = ?, updated_at = ? WHERE id = ? AND center_id = ?",
                d.nephropathieInitiale(), d.dateMiseEnDialyse(), d.hepatiteBStatut(), d.hepatiteCStatut(), d.observationGlobale(),
                d.conclusionMedicale(), horodatage(d.modifieLe()), d.id(), d.centre().valeur());
    }

    private Optional<DossierMedicalDeBase> lire(String sql, CentreId centre, PatientId patient) {
        List<DossierMedicalDeBase> lus = new ArrayList<>();
        jdbc.query(sql, (ResultSet rs) -> {
            lus.add(reconstituer(rs));
        }, patient.valeur(), centre.valeur());
        if (lus.size() > 1) {
            JOURNAL.warn("Plusieurs dossiers médicaux de base pour un même patient : le plus ancien {} est retenu", lus.get(0).id());
        }
        return lus.stream().findFirst();
    }

    private static DossierMedicalDeBase reconstituer(ResultSet rs) throws SQLException {
        return DossierMedicalDeBase.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("nephropathie_initiale"),
                rs.getObject("date_mise_en_dialyse", LocalDate.class), rs.getString("hepatite_b_statut"), rs.getString("hepatite_c_statut"),
                rs.getString("observation_globale"), rs.getString("conclusion_medicale"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }
}
