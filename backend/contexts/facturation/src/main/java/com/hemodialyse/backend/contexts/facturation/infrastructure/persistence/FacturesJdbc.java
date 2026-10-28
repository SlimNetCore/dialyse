package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Facture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Factures;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.NumeroDeFactureDejaPris;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.UUID;

/**
 * Les factures émises : tables {@code factures} (instantanés du patient, du payeur, de la période, du taux et des totaux, auteur
 * de l'émission) et {@code facture_lignes} (forfait, libellé, prix et quantité figés). Le numéro est unique par centre (contrainte
 * {@code UNIQUE (center_id, numero_facture)}) : un doublon devient {@link NumeroDeFactureDejaPris}.
 */
public class FacturesJdbc implements Factures {

    private final JdbcTemplate jdbc;

    public FacturesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(Facture facture) {
        try {
            jdbc.update("""
                            INSERT INTO factures (
                              id, center_id, patient_id, numero_facture, patient_code, patient_full_name,
                              patient_status_snapshot, numero_immatriculation_snapshot, centre_payeur_id_snapshot,
                              agence_id_snapshot, period_start, period_end, date_facturation, tva_rate,
                              total_ht, total_tva, total_ttc, emise_par, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                            """,
                    facture.id(), facture.centre().valeur(), facture.patient().id(), facture.numero(),
                    facture.patient().codeAffiche(), facture.patient().nomComplet(), facture.patient().etatAffiche(),
                    facture.patient().numeroDImmatriculation(), facture.patient().centrePayeur(), facture.patient().agence(),
                    Date.valueOf(facture.periode().debut()), Date.valueOf(facture.periode().fin()),
                    Date.valueOf(facture.dateDeFacturation()), facture.taux().pourcentage(), facture.totalHt(),
                    facture.totalTva(), facture.totalTtc(), facture.emisePar());
        } catch (DuplicateKeyException numeroPris) {
            throw new NumeroDeFactureDejaPris(facture.numero());
        }
        for (LigneFacture ligne : facture.lignes()) {
            jdbc.update("""
                            INSERT INTO facture_lignes (
                              id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, seance_count, line_ht
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    UUID.randomUUID(), facture.id(), facture.centre().valeur(), ligne.forfait(), ligne.libelle(),
                    ligne.prixUnitaireHt(), ligne.nombreDeSeances(), ligne.montantHt());
        }
    }

    @Override
    public boolean numeroDejaPris(CentreId centre, String numero) {
        Integer nombre = jdbc.queryForObject("SELECT COUNT(*) FROM factures WHERE center_id = ? AND numero_facture = ?",
                Integer.class, centre.valeur(), numero);
        return nombre != null && nombre > 0;
    }
}
