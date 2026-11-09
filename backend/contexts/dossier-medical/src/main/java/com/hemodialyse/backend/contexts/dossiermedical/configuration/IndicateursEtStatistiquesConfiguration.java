package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.CalculerLesIndicateursCliniques;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConstituerLeDossierDeGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.EvaluerLeCandidatALaGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.IdentitesDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.application.LecturesDesIndicateursCliniques;
import com.hemodialyse.backend.contexts.dossiermedical.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance.SaisiesDeSeanceParLeDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.IndicateursCliniquesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.PatientsARisqueInfectieuxJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.PorteursDUnCatheterJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.StatistiquesMedicalesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.TraitementDeLAnemieJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.EvaluationKdigoControleur;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.AbordsVasculairesPublies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RisquesInfectieuxPublies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.TraitementDeLAnemiePublie;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SaisiesDeSeanceFournies;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Composition DM9 (ADR 0101) : lectures publiées des statistiques, des indicateurs cliniques, du traitement de l'anémie, du dossier de
 * greffe, du risque infectieux et des cathéters ; évaluation KDIGO du candidat ; administrations d'une séance supprimée. Mêmes tables et
 * mêmes URL que le legacy.
 */
@Configuration(proxyBeanMethods = false)
public class IndicateursEtStatistiquesConfiguration {

    @Bean
    RisquesInfectieuxPublies risquesInfectieuxPublies(JdbcTemplate jdbc) {
        return new PatientsARisqueInfectieuxJdbc(jdbc);
    }

    @Bean
    AbordsVasculairesPublies abordsVasculairesPublies(JdbcTemplate jdbc) {
        return new PorteursDUnCatheterJdbc(jdbc);
    }

    @Bean
    StatistiquesMedicalesPubliees statistiquesMedicalesPubliees(JdbcTemplate jdbc) {
        return new StatistiquesMedicalesJdbc(jdbc);
    }

    @Bean
    TraitementDeLAnemiePublie traitementDeLAnemiePublie(JdbcTemplate jdbc) {
        return new TraitementDeLAnemieJdbc(jdbc);
    }

    @Bean
    LecturesDesIndicateursCliniques lecturesDesIndicateursCliniques(JdbcTemplate jdbc) {
        return new IndicateursCliniquesJdbc(jdbc);
    }

    @Bean
    IndicateursCliniquesPublies indicateursCliniquesPublies(LecturesDesIndicateursCliniques lectures) {
        return new CalculerLesIndicateursCliniques(lectures);
    }

    @Bean
    DossierDeGreffePublie dossierDeGreffePublie(PatientsDuCentre patients, BilansPreGreffe bilans, EtapesDeBilan etapes, Serologies serologies,
                                                Antecedents antecedents, DonneursVivants donneurs) {
        return new ConstituerLeDossierDeGreffe(patients, bilans, etapes, serologies, antecedents, donneurs);
    }

    /** Âge du candidat au jour UTC de la JVM, comme le legacy ({@code LocalDate.now()}). */
    @Bean
    EvaluerLeCandidatALaGreffe evaluerLeCandidatALaGreffe(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans,
                                                          IdentitesDesPatients identites, DerniereCreatininePubliee derniereCreatinine,
                                                          Serologies serologies, @Qualifier("horlogeDuDossierDeBase") HorlogeDuCentre horloge) {
        return new EvaluerLeCandidatALaGreffe(session, patients, bilans, identites, derniereCreatinine, serologies, horloge);
    }

    @Bean
    EvaluationKdigoControleur evaluationKdigoControleur(EvaluerLeCandidatALaGreffe evaluer) {
        return new EvaluationKdigoControleur(evaluer);
    }

    /** seance.v1 : fourni à la Séance pour la suppression d'une séance (SE6). */
    @Bean
    SaisiesDeSeanceFournies saisiesDeSeanceParLeDossierMedical(Administrations administrations) {
        return new SaisiesDeSeanceParLeDossierMedical(administrations);
    }
}
