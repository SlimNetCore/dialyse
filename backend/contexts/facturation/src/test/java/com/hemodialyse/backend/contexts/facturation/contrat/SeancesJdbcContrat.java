package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance.ForfaitsDuCentreJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance.LiaisonDesSeancesJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance.SeancesDeFacturationJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance.SeancesFacturablesJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Le contrat des séances exécuté contre les ACL JDBC : pose du décor en SQL sur les tables du module Séance, Patient,
 * Référentiel et Prise en charge. Les variantes H2 et PostgreSQL 16 ne diffèrent que par leur base.
 */
public abstract class SeancesJdbcContrat extends SeancesContrat {

    protected abstract JdbcTemplate jdbc();

    /**
     * Supprime les lignes de test des deux centres du contrat.
     */
    protected void viderLesLignesDeTest() {
        for (String table : new String[]{"seances", "prise_en_charge", "patients", "centre_payeur", "forfait"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
        }
    }

    @Override
    protected SeancesDeFacturation seances() {
        return new SeancesDeFacturationJdbc(jdbc());
    }

    @Override
    protected SeancesFacturables facturables() {
        // l'ACL de Facturation au-dessus de l'adapter RÉEL du langage publié : la politique de forfait est prouvée de bout en bout
        return new SeancesFacturablesJdbc(jdbc(),
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.CouvertureDesPatientsJdbc(jdbc()));
    }

    @Override
    protected ForfaitsDuCentre forfaits() {
        return new ForfaitsDuCentreJdbc(jdbc());
    }

    @Override
    protected LiaisonDesSeances liaison() {
        return new LiaisonDesSeancesJdbc(jdbc());
    }

    @Override
    protected void payeur(UUID patient, CentreId centre, UUID centrePayeur, UUID agence) {
        jdbc().update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, 'CNAS Alger')", centrePayeur,
                centre.valeur(), agence, "CP-" + centrePayeur.toString().substring(30));
        jdbc().update("UPDATE patients SET centre_payeur_id = ? WHERE id = ?", centrePayeur, patient);
    }

    @Override
    protected String statutDe(UUID seance) {
        return jdbc().queryForObject("SELECT statut FROM seances WHERE id = ?", String.class, seance);
    }

    @Override
    protected UUID factureDe(UUID seance) {
        return jdbc().queryForObject("SELECT facture_id FROM seances WHERE id = ?", UUID.class, seance);
    }

    @Override
    protected void patient(UUID id, CentreId centre, String code, String nom, String prenom, String etat) {
        jdbc().update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient) VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER', ?)",
                id, centre.valeur(), code, nom, prenom, Date.valueOf(LocalDate.of(2026, 1, 10)), "ASS-" + code, etat);
    }

    @Override
    protected void forfait(UUID id, CentreId centre, String libelle, String prix) {
        jdbc().update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", id, centre.valeur(),
                "F-" + id.toString().substring(30), libelle, new BigDecimal(prix));
    }

    @Override
    protected void priseEnCharge(UUID patient, CentreId centre, PriseEnChargeTarifee pec) {
        UUID forfait = pec.forfait().id();
        jdbc().update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, "
                        + "date_debut_effectif, date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALIDEE', ?)",
                UUID.randomUUID(), centre.valeur(), patient, jour(pec.debutDemande()), jour(pec.finDemande()),
                jour(pec.debutEffectif()), jour(pec.finEffectif()), pec.forfaitEffectif() ? null : forfait,
                pec.forfaitEffectif() ? forfait : null, Timestamp.from(pec.creeeLe()));
    }

    @Override
    protected void seance(UUID id, CentreId centre, UUID patient, LocalDate date, String statut, boolean facturee) {
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, facture_id) VALUES (?, ?, ?, ?, ?, ?)",
                id, centre.valeur(), patient, Date.valueOf(date), statut, facturee ? UUID.randomUUID() : null);
    }

    @Override
    protected void surcharge(UUID seance, ForfaitApplique forfait) {
        jdbc().update("UPDATE seances SET forfait_override_id = ?, forfait_override_nom = ?, forfait_override_prix = ? WHERE id = ?",
                forfait.id(), forfait.libelle(), forfait.prixHt(), seance);
    }

    private static Date jour(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }
}
