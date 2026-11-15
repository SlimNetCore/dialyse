package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.CalculerLesIndicateursCliniques;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConstituerLeDossierDeGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesStatistiquesMedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesStatistiquesParamedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.ExporterLesStatistiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.ImpressionDesStatistiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.SeancesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance.SeancesDuPatientPubliees;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.impression.StatistiquesDuPatientEnPdf;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.StatistiquesDuPatientControleur;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import java.time.Clock;
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
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance.PoidsSecsPourLaSeance;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.PoidsSecsPrescritsJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PoidsSecsPrescritsPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DossierEtTraitementDuCahier;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PoidsSecDesPrescriptions;
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

    /** Fournit à la Planification les patients à risque infectieux (ADR 0190, inversion par port fourni : pas de cycle Maven). */
    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis planificationRisquesInfectieuxFournis(
            RisquesInfectieuxPublies risques) {
        return new com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.planification.RisquesInfectieuxParLeDossierMedical(
                risques);
    }

    /** Fournit à la Planification les porteurs d'un cathéter (ADR 0190). */
    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.AbordsVasculairesFournis planificationAbordsVasculairesFournis(
            AbordsVasculairesPublies abords) {
        return new com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.planification.AbordsVasculairesParLeDossierMedical(
                abords);
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

    // ------------------------------------------------------------------ statistiques du patient (PL4)

    /** ACL vers {@code seance.v1} : les séances du patient et leur volet paramédical. */
    @Bean
    SeancesDuPatient seancesDuPatientDuDossierMedical(StatistiquesDesSeancesPubliees statistiques) {
        return new SeancesDuPatientPubliees(statistiques);
    }

    /** Le papier à en-tête du centre est fourni par la plateforme ; l'heure « Généré le » est celle du serveur, en UTC (règle d'architecture de la plateforme). */
    @Bean
    ImpressionDesStatistiquesDuPatient impressionDesStatistiquesDuPatient(PapierEnTeteDuCentre papierEnTete) {
        return new StatistiquesDuPatientEnPdf(papierEnTete, Clock.systemUTC());
    }

    @Bean
    ConsulterLesStatistiquesParamedicales consulterLesStatistiquesParamedicales(SessionCourante session, PatientsDuCentre patients,
                                                                                SeancesDuPatient seances, StatistiquesMedicalesPubliees medicales,
                                                                                @Qualifier("horlogeDuDossierDeBase") HorlogeDuCentre horloge) {
        return new ConsulterLesStatistiquesParamedicales(session, patients, seances, medicales, horloge);
    }

    @Bean
    ConsulterLesStatistiquesMedicales consulterLesStatistiquesMedicales(SessionCourante session, PatientsDuCentre patients,
                                                                        StatistiquesMedicalesPubliees medicales) {
        return new ConsulterLesStatistiquesMedicales(session, patients, medicales);
    }

    @Bean
    ExporterLesStatistiquesDuPatient exporterLesStatistiquesDuPatient(SessionCourante session, PatientsDuCentre patients, SeancesDuPatient seances,
                                                                      StatistiquesMedicalesPubliees medicales,
                                                                      @Qualifier("horlogeDuDossierDeBase") HorlogeDuCentre horloge,
                                                                      ImpressionDesStatistiquesDuPatient impression) {
        return new ExporterLesStatistiquesDuPatient(session, patients, seances, medicales, horloge, impression);
    }

    @Bean
    StatistiquesDuPatientControleur statistiquesDuPatientControleur(ConsulterLesStatistiquesParamedicales paramedicales,
                                                                    ConsulterLesStatistiquesMedicales medicales,
                                                                    ExporterLesStatistiquesDuPatient exporter) {
        return new StatistiquesDuPatientControleur(paramedicales, medicales, exporter);
    }

    /** dossier-medical.v1 : poids secs cibles prescrits (LEC-PLN-GMAO c, ADR 0180). */
    @Bean
    PoidsSecsPrescritsPublies poidsSecsPrescritsPublies(JdbcTemplate jdbc) {
        return new PoidsSecsPrescritsJdbc(jdbc);
    }

    /** seance.v1 : fourni à la Séance pour le poids sec en vigueur le jour de chaque séance (LEC-PLN-GMAO c, ADR 0180). */
    @Bean
    PoidsSecDesPrescriptions poidsSecDesPrescriptionsDeLaSeance(PoidsSecsPrescritsPublies prescrits) {
        return new PoidsSecsPourLaSeance(prescrits);
    }

    /** seance.v1 : fourni à la Séance pour le jeu de données du cahier de dialyse imprimé (DOC5a, ADR 0193). */
    @Bean
    DossierEtTraitementDuCahier dossierEtTraitementDuCahierPourLaSeance(JdbcTemplate jdbc,
                                                                        com.hemodialyse.backend.contexts.dossiermedical.application.CatalogueDesArticles articles) {
        return new com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance.DossierEtTraitementPourLeCahier(jdbc, articles);
    }

    /** seance.v1 : fourni à la Séance pour la suppression d'une séance (SE6). */
    @Bean
    SaisiesDeSeanceFournies saisiesDeSeanceParLeDossierMedical(Administrations administrations) {
        return new SaisiesDeSeanceParLeDossierMedical(administrations);
    }
}
