package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PatientFacture;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PolitiqueDeForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL de lecture sur les séances, les patients et les prises en charge (modules Séance, Patient et Prise en charge) : les
 * séances facturables du centre (RG-FAC-020 : statut {@code VALIDEE} ou {@code SIGNEE}, comparé en majuscules exactes en SQL,
 * KB-FAC-17 ; non facturée ; date dans la période), le payeur de leur patient (instantané de la facture) et leur forfait appliqué
 * (RG-FAC-021). Les prises en charge d'un patient ne sont
 * lues qu'une fois par simulation.
 */
public class SeancesFacturablesJdbc implements SeancesFacturables {

    private final JdbcTemplate jdbc;

    public SeancesFacturablesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SeanceFacturable> dansLaPeriode(CentreId centre, PeriodeDeFacturation periode) {
        List<LigneDeSeance> lignes = jdbc.query("""
                        SELECT s.id AS seance_id, s.patient_id, s.date_seance, s.statut,
                               s.forfait_override_id, s.forfait_override_nom, s.forfait_override_prix,
                               p.code_patient, p.nom, p.prenom, p.etat_patient,
                               p.numero_assurance, p.centre_payeur_id, cp.agence_id
                        FROM seances s
                        INNER JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id
                        LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id AND cp.center_id = p.center_id
                        WHERE s.center_id = ?
                          AND s.date_seance BETWEEN ? AND ?
                          AND s.statut IN ('VALIDEE', 'SIGNEE')
                          AND s.facture_id IS NULL
                        ORDER BY p.nom, p.prenom, s.date_seance, s.id
                        """,
                (rs, ligne) -> new LigneDeSeance(rs.getObject("seance_id", UUID.class),
                        rs.getDate("date_seance").toLocalDate(), rs.getString("statut"),
                        rs.getObject("forfait_override_id", UUID.class) == null ? Optional.empty()
                                : Optional.of(new ForfaitApplique(rs.getObject("forfait_override_id", UUID.class),
                                rs.getString("forfait_override_nom"), rs.getBigDecimal("forfait_override_prix"))),
                        new PatientFacture(rs.getObject("patient_id", UUID.class), rs.getString("code_patient"),
                                rs.getString("nom"), rs.getString("prenom"), rs.getString("etat_patient"),
                                rs.getString("numero_assurance"), rs.getObject("centre_payeur_id", UUID.class),
                                rs.getObject("agence_id", UUID.class))),
                centre.valeur(), Date.valueOf(periode.debut()), Date.valueOf(periode.fin()));

        Map<UUID, List<PriseEnChargeTarifee>> prisesEnChargeParPatient = new HashMap<>();
        List<SeanceFacturable> seances = new ArrayList<>();
        for (LigneDeSeance ligne : lignes) {
            List<PriseEnChargeTarifee> prisesEnCharge = prisesEnChargeParPatient.computeIfAbsent(ligne.patient().id(),
                    patient -> prisesEnCharge(centre, patient));
            ForfaitApplique forfait = PolitiqueDeForfaitApplique.pour(ligne.date(), ligne.surcharge(), prisesEnCharge);
            seances.add(new SeanceFacturable(ligne.id(), ligne.patient(), ligne.date(), new StatutDeSeance(ligne.statut()), forfait));
        }
        return seances;
    }

    private List<PriseEnChargeTarifee> prisesEnCharge(CentreId centre, UUID patient) {
        return jdbc.query("""
                        SELECT p.forfait_effectif_id, f.id AS forfait_id, f.libelle, f.prix,
                               p.date_debut_effectif, p.date_fin_effectif, p.date_debut_demande, p.date_fin_demande, p.created_at
                        FROM prise_en_charge p
                        INNER JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id)
                                             AND f.center_id = p.center_id
                        WHERE p.center_id = ? AND p.patient_id = ?
                        """,
                (rs, ligne) -> {
                    Timestamp creeeLe = rs.getTimestamp("created_at");
                    return new PriseEnChargeTarifee(
                            new ForfaitApplique(rs.getObject("forfait_id", UUID.class), rs.getString("libelle"), rs.getBigDecimal("prix")),
                            rs.getObject("forfait_effectif_id", UUID.class) != null,
                            jour(rs.getDate("date_debut_effectif")), jour(rs.getDate("date_fin_effectif")),
                            jour(rs.getDate("date_debut_demande")), jour(rs.getDate("date_fin_demande")),
                            creeeLe == null ? null : creeeLe.toInstant());
                },
                centre.valeur(), patient);
    }

    private static LocalDate jour(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private record LigneDeSeance(UUID id, LocalDate date, String statut, Optional<ForfaitApplique> surcharge,
                                 PatientFacture patient) {
    }
}
