package com.hemodialyse.backend.contexts.absencespatients.contrat;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.LectureDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.seance.SeancesRealiseesPubliees;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.LectureDesAbsencesJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contrat des lectures du suivi contre les adaptateurs JDBC : H2 (tables minimales) et PostgreSQL 16 (schéma Flyway). Les
 * insertions n'utilisent que des colonnes qui existent dans les deux.
 */
abstract class LecturesDuSuiviJdbcContrat extends LecturesDuSuiviContrat {

    protected abstract JdbcTemplate jdbc();

    @Override
    protected LectureDesAbsences lecture() {
        return new LectureDesAbsencesJdbc(jdbc());
    }

    @Override
    protected com.hemodialyse.backend.contexts.absencespatients.application.NomsDesPatients noms() {
        return new com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.patientcouverture.NomsDesPatientsDeLaFiche(
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.FichesPublieesJdbc(jdbc()));
    }

    /** Depuis SE7d (ADR 0098) : l'ACL de production au-dessus d'un double du langage fourni qui lit la table semée par ce contrat. */
    @Override
    protected SeancesRealisees seances() {
        return new SeancesRealiseesPubliees(new SeancesRealiseesSurLaTable(jdbc()));
    }

    /**
     * Vide les lignes de test (centres fixes du contrat) avant chaque test.
     */
    protected void viderLesLignesDeTest() {
        for (CentreId centre : new CentreId[]{CENTRE, AUTRE_CENTRE, new CentreId(new UUID(0x7000, 0xc))}) {
            jdbc().update("DELETE FROM absence_patient WHERE center_id = ?", centre.valeur());
            jdbc().update("DELETE FROM seances WHERE center_id = ?", centre.valeur());
            jdbc().update("DELETE FROM patients WHERE center_id = ?", centre.valeur());
        }
    }

    @Override
    protected void poserLePatient(CentreId centre, PatientId id, String prenom, String nom) {
        jdbc().update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient) VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER')",
                id.valeur(), centre.valeur(), "P-" + id.valeur().getLeastSignificantBits(), nom, prenom,
                Date.valueOf(LocalDate.of(2026, 1, 10)), "ASS-" + id.valeur().getLeastSignificantBits());
    }

    @Override
    protected void poserLAbsence(CentreId centre, AbsenceSuivie a) {
        jdbc().update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, motif, commentaire, "
                        + "forfait_libelle, prix_ttc, taux_tva, montant_ht, declaree_le, qualifiee_le, date_rattrapage) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                a.id().valeur(), centre.valeur(), a.patient().valeur(), Date.valueOf(a.dateDeSeance()), a.source().name(),
                a.statut().name(), a.motif() == null ? null : a.motif().name(), a.commentaire(), a.libelleDuForfait(),
                a.montantTtc(), a.tauxDeTva(), a.montantHt(),
                a.declareeLe() == null ? null : Timestamp.from(a.declareeLe()),
                a.qualifieeLe() == null ? null : Timestamp.from(a.qualifieeLe()),
                a.dateDeRattrapage() == null ? null : Date.valueOf(a.dateDeRattrapage()));
    }

    @Override
    protected void poserLaSeance(CentreId centre, PatientId patient, LocalDate date, String statut) {
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre.valeur(), patient.valeur(), Date.valueOf(date), statut);
    }
}
