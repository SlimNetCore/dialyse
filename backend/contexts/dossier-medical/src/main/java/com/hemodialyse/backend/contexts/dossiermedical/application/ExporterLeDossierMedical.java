package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Rassemble le dossier médical du patient pour l'export interopérable (FHIR R4) : l'identité, les antécédents, les allergies, les bilans
 * d'analyses, les observations biologiques, les ordonnances et les abords vasculaires, du plus récent au plus ancien dans chaque famille.
 * Lecture seule, un seul patient du centre de la session ; patient inconnu ou d'un autre centre : 404 {@code PATIENT_INTROUVABLE}
 * (écart décidé KB-MED-102, avant : 400 sans code). Une fiche illisible en base est ignorée (WARN, identifiant seul) et n'échoue plus
 * tout l'export. REPRODUIT, à faire confirmer par les médecins et le DPO (risque clinique, ADR 0087) : seules les 500 observations et les
 * 500 ordonnances les plus récentes sont exportées, sans indication de troncature (KB-MED-101). Droit : MEDECIN. Export non journalisé
 * en audit (KB-MED-100, reproduit).
 */
public class ExporterLeDossierMedical {
    /** Plafond REPRODUIT du legacy : observations et ordonnances (KB-MED-101). */
    public static final int PLAFOND_DES_OBSERVATIONS_ET_DES_ORDONNANCES = 500;
    private static final int TOUT = Integer.MAX_VALUE;

    private final SessionCourante session;
    private final IdentitesDesPatients identites;
    private final Antecedents antecedents;
    private final Allergies allergies;
    private final Analyses analyses;
    private final ObservationsBiologiques observations;
    private final Ordonnances ordonnances;
    private final AbordsVasculaires abords;
    private final HorlogeDuCentre horloge;

    public ExporterLeDossierMedical(SessionCourante session, IdentitesDesPatients identites, Antecedents antecedents, Allergies allergies,
                                    Analyses analyses, ObservationsBiologiques observations, Ordonnances ordonnances, AbordsVasculaires abords,
                                    HorlogeDuCentre horloge) {
        this.session = session;
        this.identites = identites;
        this.antecedents = antecedents;
        this.allergies = allergies;
        this.analyses = analyses;
        this.observations = observations;
        this.ordonnances = ordonnances;
        this.abords = abords;
        this.horloge = horloge;
    }

    public DossierAExporter executer(UUID centreDemande, UUID patientDemande) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        var identite = identites.duPatient(centre, patient).orElseThrow(PatientIntrouvable::new);
        return new DossierAExporter(patientDemande, identite,
                antecedents.duPatient(centre, patient, 0, TOUT).items(),
                allergies.duPatient(centre, patient, 0, TOUT).items(),
                analyses.duPatient(centre, patient, Periode.ILLIMITEE, 0, TOUT).items(),
                observations.duPatient(centre, patient, null, null, null, 0, PLAFOND_DES_OBSERVATIONS_ET_DES_ORDONNANCES).items(),
                ordonnances.duPatient(centre, patient, 0, PLAFOND_DES_OBSERVATIONS_ET_DES_ORDONNANCES).items(),
                abords.duPatient(centre, patient, 0, TOUT).items(), horloge.maintenant());
    }

    /** Le dossier lu, prêt à être traduit dans le format d'échange par l'interface. */
    public record DossierAExporter(UUID patient, IdentitesDesPatients.Identite identite, List<Antecedent> antecedents, List<Allergie> allergies,
                                   List<Analyse> analyses, List<ObservationBiologique> observations, List<Ordonnance> ordonnances,
                                   List<AbordVasculaire> abords, Instant exporteLe) {
    }
}
