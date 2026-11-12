package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.DossierDeGreffe;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Export PDF du dossier de préparation à la greffe rénale, destiné au transfert vers le centre de transplantation (PL2, ex
 * {@code GreffePdfService}). Garde du centre de la session (403), puis du patient (404 {@code PATIENT_INTROUVABLE}, DM7 KB-MED-86/97,
 * par {@link DossierDeGreffePublie}), puis lecture de la carte du patient et mise en page.
 */
public class ExporterLeDossierDeGreffe {

    private final SessionCourante session;
    private final DossierDeGreffePublie dossiers;
    private final CartesDesPatients cartes;
    private final ImpressionDuDossierDeGreffe impression;

    public ExporterLeDossierDeGreffe(SessionCourante session, DossierDeGreffePublie dossiers, CartesDesPatients cartes,
                                     ImpressionDuDossierDeGreffe impression) {
        this.session = session;
        this.dossiers = dossiers;
        this.cartes = cartes;
        this.impression = impression;
    }

    public byte[] executer(UUID centreDemande, UUID patient) {
        CentreId centre = session.centre(centreDemande);
        DossierDeGreffe dossier = dossiers.duPatient(centre.valeur(), patient);
        return impression.enPdf(centre, cartes.duPatient(centre, patient), dossier);
    }
}
