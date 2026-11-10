package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture.ForfaitsDuCentreParLeCatalogue;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance.SeancesFacturablesPubliees;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.CouvertureDesPatientsJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le contrat du forfait appliqué exécuté contre les adapters JDBC RÉELS (revue SE7c2, constat m1) : {@link ForfaitsDuCentreParLeCatalogue} (catalogue
 * de forfaits de Patient et couverture, table {@code forfait}) et la politique de forfait de bout en bout avec le vrai {@link CouvertureDesPatientsJdbc} (table {@code prise_en_charge}).
 * Aucun SQL sur {@code seances} : la Facturation ne touche plus cette table (ADR 0097). Les séances arrivent par le port
 * {@link SeancesAFacturerFournies}, que la plateforme implémente avec le traducteur {@code SeancesFacturablesParLaSeance} (lecture prouvée sur
 * H2 et PostgreSQL 16 par le contrat de la Séance) ; le module Facturation ne peut pas dépendre de la Séance (cycle Maven) : un double de ce
 * port, qui respecte son contrat (casse exacte, non facturées, période incluse, tri nom, prénom, date, identifiant), le remplace ici. Les
 * lignes {@code patients} ne sont posées que pour les clés étrangères de {@code prise_en_charge} du schéma réel.
 */
public abstract class ForfaitAppliqueJdbcContrat extends ForfaitAppliqueContrat {

    private final SeancesFournies seancesFournies = new SeancesFournies();

    protected abstract JdbcTemplate jdbc();

    /**
     * Supprime les lignes de test des deux centres du contrat (jamais {@code seances}).
     */
    protected void viderLesLignesDeTest() {
        for (String table : new String[]{"prise_en_charge", "patients", "forfait"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
        }
    }

    @Override
    protected SeancesFacturables facturables() {
        return new SeancesFacturablesPubliees(seancesFournies, new CouvertureDesPatientsJdbc(jdbc()));
    }

    @Override
    protected ForfaitsDuCentre forfaits() {
        return new ForfaitsDuCentreParLeCatalogue(
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.ForfaitsJdbc(jdbc(), centre -> { }));
    }

    @Override
    protected void patient(UUID id, CentreId centre, String code, String nom, String prenom, String etat) {
        jdbc().update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient) VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER', ?)",
                id, centre.valeur(), code, nom, prenom, Date.valueOf(LocalDate.of(2026, 1, 10)), "ASS-" + code, etat);
        seancesFournies.patients.put(id, new SeancesAFacturerFournies.PatientFourni(id, code, nom, prenom, etat, "ASS-" + code, null, null));
        seancesFournies.centreDuPatient.put(id, centre.valeur());
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
        seancesFournies.seances.put(id, new SeanceDuDouble(id, centre.valeur(), patient, date, statut, facturee));
    }

    @Override
    protected void surcharge(UUID seance, ForfaitApplique forfait) {
        seancesFournies.surcharges.put(seance, forfait);
    }

    private static Date jour(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private record SeanceDuDouble(UUID id, UUID centre, UUID patient, LocalDate date, String statut, boolean facturee) {
    }

    /** Double du port fourni par la plateforme : respecte le contrat documenté de {@link SeancesAFacturerFournies}. */
    private static final class SeancesFournies implements SeancesAFacturerFournies {

        private final Map<UUID, PatientFourni> patients = new HashMap<>();
        private final Map<UUID, UUID> centreDuPatient = new HashMap<>();
        private final Map<UUID, SeanceDuDouble> seances = new HashMap<>();
        private final Map<UUID, ForfaitApplique> surcharges = new HashMap<>();

        @Override
        public List<SeanceAFacturerFournie> dansLaPeriode(UUID centre, LocalDate debut, LocalDate fin) {
            List<SeanceAFacturerFournie> lues = new ArrayList<>();
            for (SeanceDuDouble s : seances.values()) {
                boolean eligible = s.centre().equals(centre) && centre.equals(centreDuPatient.get(s.patient()))
                        && ("VALIDEE".equals(s.statut()) || "SIGNEE".equals(s.statut())) && !s.facturee()
                        && !s.date().isBefore(debut) && !s.date().isAfter(fin);
                if (eligible) {
                    ForfaitApplique impose = surcharges.get(s.id());
                    lues.add(new SeanceAFacturerFournie(s.id(), s.date(), s.statut(), impose == null ? null : impose.id(),
                            impose == null ? null : impose.libelle(), impose == null ? null : impose.prixHt(), patients.get(s.patient())));
                }
            }
            lues.sort(Comparator.comparing((SeanceAFacturerFournie l) -> l.patient().nom())
                    .thenComparing(l -> l.patient().prenom()).thenComparing(SeanceAFacturerFournie::date)
                    .thenComparing(SeanceAFacturerFournie::id));
            return lues;
        }
    }
}
