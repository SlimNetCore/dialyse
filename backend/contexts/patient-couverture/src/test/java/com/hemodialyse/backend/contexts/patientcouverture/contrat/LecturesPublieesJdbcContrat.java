package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AttestationsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.CompteursDeCouvertureJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.CouvertureDesPatientsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.FichesPublieesJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LotsDePatientsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.ProgrammationPublieeJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PatientsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PrisesEnChargeJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/** Contrat du langage publié sur les tables réelles (H2 et PostgreSQL 16 : sous-classes). */
abstract class LecturesPublieesJdbcContrat extends LecturesPublieesContrat {

    protected abstract JdbcTemplate jdbc();

    /** Vide les lignes des deux centres de test (avant et après chaque test). */
    protected void vider() {
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            jdbc().update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM forfait WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM mouvement_patient WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM patients WHERE center_id = ?", centre);
        }
    }

    @Override
    protected PatientRepository patients() {
        return new PatientsJdbc(jdbc());
    }

    @Override
    protected void poserLesJours(Patient patient, com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation.Jours jours) {
        jdbc().update("UPDATE patients SET jour_dimanche = ?, jour_lundi = ?, jour_mardi = ?, jour_mercredi = ?, jour_jeudi = ?, "
                        + "jour_vendredi = ?, jour_samedi = ? WHERE id = ?", jours.dimanche(), jours.lundi(), jours.mardi(), jours.mercredi(),
                jours.jeudi(), jours.vendredi(), jours.samedi(), patient.id().valeur());
    }

    @Override
    protected PrisesEnCharge pecs() {
        return new PrisesEnChargeJdbc(jdbc());
    }

    @Override
    protected Attestations attestations() {
        return new AttestationsJdbc(jdbc());
    }

    @Override
    protected UUID forfait(CentreId centre, String libelle, BigDecimal prix) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?,?,?,?,?)", id, centre.valeur(),
                "F-" + id.toString().substring(0, 8), libelle, prix);
        return id;
    }

    @Override
    protected FichesDesPatients fiches() {
        return new FichesPublieesJdbc(jdbc());
    }

    @Override
    protected CouvertureDesPatients couverture() {
        return new CouvertureDesPatientsJdbc(jdbc());
    }

    @Override
    protected CompteursDeCouverture compteurs() {
        return new CompteursDeCouvertureJdbc(jdbc());
    }

    @Override
    protected com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ProgrammationDesPatients programmation() {
        return new ProgrammationPublieeJdbc(jdbc());
    }

    @Override
    protected com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients lots() {
        return new LotsDePatientsJdbc(jdbc());
    }
}
