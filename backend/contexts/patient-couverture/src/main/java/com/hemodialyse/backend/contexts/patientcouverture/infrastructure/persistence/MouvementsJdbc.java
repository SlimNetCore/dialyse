package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementsDesPatients;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;

/** Adapter du journal des mouvements ({@code mouvement_patient}) : ajout seulement, jamais de mise à jour. */
public class MouvementsJdbc implements MouvementsDesPatients {

    private final JdbcTemplate jdbc;

    public MouvementsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(MouvementDuPatient m) {
        jdbc.update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, etat_precedent, "
                        + "etat_nouveau, salle_id, position_id, generateur_id, jours_dialyse, automatique, cree_le) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                m.id(), m.centre().valeur(), m.patient().valeur(), m.type().name(), Date.valueOf(m.dateEffet()),
                m.etatPrecedent() == null ? null : m.etatPrecedent().name(), m.etatNouveau().name(), m.salleId(),
                m.creneauId(), m.generateurId(), m.joursDeDialyse(), m.automatique(), Timestamp.from(m.creeLe()));
    }
}
