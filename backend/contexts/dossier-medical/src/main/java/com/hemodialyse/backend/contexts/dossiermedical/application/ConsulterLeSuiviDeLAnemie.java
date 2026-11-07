package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AdministrationDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EvaluationDeCible;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Le suivi de l'anémie d'un patient : les 10 derniers bilans (courbe), l'évaluation KDIGO du dernier, la prescription la plus récente et
 * les 10 dernières administrations. Écart décidé (KB-MED-26) : ce sont bien les 10 PLUS RÉCENTES (le legacy rendait les 10 premières
 * lues, sans tri). Droits : MEDECIN et ADMIN.
 */
public class ConsulterLeSuiviDeLAnemie {

    /** Le suivi. */
    public record Suivi(LocalDate dateDernierBilan, List<EvaluationDeCible> evaluations, List<BilanBiologique> courbe,
                        Optional<PrescriptionDetaillee> prescriptionActive, List<AdministrationDeTraitement> administrationsRecentes) {
    }

    private final SessionCourante session;
    private final BilansBiologiques bilans;
    private final Prescriptions prescriptions;
    private final Administrations administrations;
    private final DetaillerLesPrescriptions details;

    public ConsulterLeSuiviDeLAnemie(SessionCourante session, BilansBiologiques bilans, Prescriptions prescriptions,
                                     Administrations administrations, CatalogueDesArticles catalogue) {
        this.session = session;
        this.bilans = bilans;
        this.prescriptions = prescriptions;
        this.administrations = administrations;
        this.details = new DetaillerLesPrescriptions(catalogue);
    }

    public Suivi executer(UUID centreDemande, UUID patientDemande) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        List<BilanBiologique> courbe = bilans.lesPlusRecents(centre, patient, 10);
        BilanBiologique dernier = courbe.isEmpty() ? null : courbe.get(0);
        List<EvaluationDeCible> evaluations = dernier == null ? List.of() : List.of(
                EvaluationDeCible.hemoglobine(dernier.hemoglobine()), EvaluationDeCible.ferritine(dernier.ferritine()),
                EvaluationDeCible.coefficientDeSaturation(dernier.cst()), EvaluationDeCible.albumine(dernier.albumine()),
                EvaluationDeCible.ktV(dernier.ktV()));
        return new Suivi(dernier == null ? null : dernier.date(), evaluations, courbe,
                prescriptions.laPlusRecente(centre, patient).map(p -> details.de(centre, p)),
                administrations.lesPlusRecentes(centre, patient, 10));
    }
}
