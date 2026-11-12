package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.CartesDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.application.ExporterLeDossierDeGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.ImpressionDuDossierDeGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.patientcouverture.CartesDesPatientsDeLaFiche;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.impression.DossierDeGreffeEnPdf;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.GreffeExportControleur;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeDossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerLeDossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.application.GenerateurDIdentifiants;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.application.ExporterLeDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.application.IdentitesDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DossiersMedicauxDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.patientcouverture.IdentitesDesPatientsDeLaFiche;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.ExportFhirControleur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DossiersMedicauxDeBaseJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.DossierMedicalDeBaseControleur;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Composition DM8 (ADR 0087) : dossier médical de base, export FHIR, statistiques médicales du patient. Mêmes tables et mêmes URL que
 * le legacy ; seul endroit qui connaît toutes les couches de ces cas d'usage.
 */
@Configuration(proxyBeanMethods = false)
public class LecturesPublieesConfiguration {

    /**
     * Horloge du dossier de base : le jour UTC (celui de la JVM, comme le legacy qui lisait l'instant système) ; seul l'instant est
     * utilisé (création et modification).
     */
    @Bean
    HorlogeDuCentre horlogeDuDossierDeBase() {
        return new HorlogeDuCentre(Clock.systemUTC(), ZoneOffset.UTC);
    }

    @Bean
    DossiersMedicauxDeBase dossiersMedicauxDeBaseDuDossierMedical(JdbcTemplate jdbc) {
        return new DossiersMedicauxDeBaseJdbc(jdbc);
    }

    @Bean
    ConsulterLeDossierMedicalDeBase consulterLeDossierMedicalDeBase(SessionCourante session, DossiersMedicauxDeBase dossiers) {
        return new ConsulterLeDossierMedicalDeBase(session, dossiers);
    }

    @Bean
    EnregistrerLeDossierMedicalDeBase enregistrerLeDossierMedicalDeBase(SessionCourante session, PatientsDuCentre patients,
                                                                        DossiersMedicauxDeBase dossiers, UniteDeTravail uniteDeTravail,
                                                                        GenerateurDIdentifiants identifiants,
                                                                        @Qualifier("horlogeDuDossierDeBase") HorlogeDuCentre horloge) {
        return new EnregistrerLeDossierMedicalDeBase(session, patients, dossiers, uniteDeTravail, identifiants, horloge);
    }

    // ------------------------------------------------------------------ export FHIR

    @Bean
    IdentitesDesPatients identitesDesPatientsDuDossierMedical(FichesDesPatients fiches) {
        return new IdentitesDesPatientsDeLaFiche(fiches);
    }

    @Bean
    ExporterLeDossierMedical exporterLeDossierMedical(SessionCourante session, IdentitesDesPatients identites, Antecedents antecedents,
                                                      Allergies allergies, Analyses analyses, ObservationsBiologiques observations,
                                                      Ordonnances ordonnances, AbordsVasculaires abords,
                                                      @Qualifier("horlogeDuDossierDeBase") HorlogeDuCentre horloge) {
        return new ExporterLeDossierMedical(session, identites, antecedents, allergies, analyses, observations, ordonnances, abords, horloge);
    }

    @Bean
    ExportFhirControleur exportFhirControleur(ExporterLeDossierMedical exporter) {
        return new ExportFhirControleur(exporter);
    }

    // ------------------------------------------------------------------ export PDF du dossier de greffe (PL2)

    @Bean
    CartesDesPatients cartesDesPatientsDuDossierMedical(FichesDesPatients fiches) {
        return new CartesDesPatientsDeLaFiche(fiches);
    }

    /** Le papier à en-tête du centre est fourni par la plateforme (moteur de documents, identité imprimée). */
    @Bean
    ImpressionDuDossierDeGreffe impressionDuDossierDeGreffe(PapierEnTeteDuCentre papierEnTete) {
        return new DossierDeGreffeEnPdf(papierEnTete);
    }

    @Bean
    ExporterLeDossierDeGreffe exporterLeDossierDeGreffe(SessionCourante session, DossierDeGreffePublie dossiers, CartesDesPatients cartes,
                                                        ImpressionDuDossierDeGreffe impression) {
        return new ExporterLeDossierDeGreffe(session, dossiers, cartes, impression);
    }

    @Bean
    GreffeExportControleur greffeExportControleur(ExporterLeDossierDeGreffe exporter) {
        return new GreffeExportControleur(exporter);
    }

    @Bean
    DossierMedicalDeBaseControleur dossierMedicalDeBaseControleur(ConsulterLeDossierMedicalDeBase consulter,
                                                                  EnregistrerLeDossierMedicalDeBase enregistrer) {
        return new DossierMedicalDeBaseControleur(consulter, enregistrer);
    }
}
