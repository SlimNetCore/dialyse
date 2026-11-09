package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteSerologiqueKdigo;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EvaluationKdigoDuCandidat;
import com.hemodialyse.backend.contexts.dossiermedical.domain.FonctionRenale;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RisqueImmunologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Évaluation KDIGO du candidat à la greffe (RG-MED-082) : risque immunologique du bilan, fonction rénale (dernière créatinine, âge au jour
 * de l'appel, sexe de la fiche), alertes sérologiques. Garde du patient dans le cas d'usage : inconnu ou d'un autre centre, 404
 * {@code PATIENT_INTROUVABLE} (DM7, KB-MED-86). La lecture n'ouvre PAS de bilan. REPRODUIT (DM9, ADR 0101) : à plusieurs sérologies d'un
 * même marqueur, le résultat le PLUS ANCIEN l'emporte (parcours par date décroissante, la dernière écriture gagne : KB-MED-94, risque
 * clinique à confirmer) ; l'ordre des alertes est celui d'une {@code HashMap} des marqueurs (legacy). Lecture non tracée (KB-MED-100).
 */
public class EvaluerLeCandidatALaGreffe {

    public record EvaluationDuCandidat(RisqueImmunologique risqueImmunologique, FonctionRenale fonctionRenale,
                                       List<AlerteSerologiqueKdigo> alertesSerologiques) {
    }

    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final BilansPreGreffe bilans;
    private final IdentitesDesPatients identites;
    private final DerniereCreatininePubliee derniereCreatinine;
    private final Serologies serologies;
    private final HorlogeDuCentre horloge;

    public EvaluerLeCandidatALaGreffe(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans, IdentitesDesPatients identites,
                                      DerniereCreatininePubliee derniereCreatinine, Serologies serologies, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.bilans = bilans;
        this.identites = identites;
        this.derniereCreatinine = derniereCreatinine;
        this.serologies = serologies;
        this.horloge = horloge;
    }

    public EvaluationDuCandidat executer(UUID centreDemande, UUID patientDemande) {
        CentreId centre = session.centre(centreDemande);
        PatientId patient = new PatientId(patientDemande);
        if (!patients.existe(centre, patient)) throw new PatientIntrouvable();

        BilanPreGreffe bilan = bilans.trouver(centre, patient).orElse(null);
        RisqueImmunologique risque = EvaluationKdigoDuCandidat.risqueImmunologique(
                bilan == null ? null : bilan.praClasseI(), bilan == null ? null : bilan.praClasseII());

        IdentitesDesPatients.Identite identite = identites.duPatient(centre, patient).orElse(null);
        BigDecimal creatinine = derniereCreatinine.duPatient(centre.valeur(), patient.valeur()).orElse(null);
        Integer age = identite == null ? null : age(identite.dateNaissance(), horloge.aujourdhui(centre));
        String sexe = identite == null ? null : identite.sexe();
        FonctionRenale fonctionRenale = EvaluationKdigoDuCandidat.fonctionRenale(creatinine, age, sexe);

        Map<String, String> marqueurResultats = new HashMap<>();
        for (Serologie s : serologies.toutesDuPatient(centre, patient)) {
            marqueurResultats.put(s.marqueur().name(), s.resultat().name());
        }
        return new EvaluationDuCandidat(risque, fonctionRenale, EvaluationKdigoDuCandidat.alertesSerologiques(marqueurResultats));
    }

    private static Integer age(LocalDate dateNaissance, LocalDate aujourdhui) {
        return dateNaissance == null ? null : Period.between(dateNaissance, aujourdhui).getYears();
    }
}
