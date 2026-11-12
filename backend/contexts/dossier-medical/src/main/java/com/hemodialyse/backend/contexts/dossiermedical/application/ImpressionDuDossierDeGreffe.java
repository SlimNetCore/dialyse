package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.DossierDeGreffe;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : la mise en page PDF du dossier de préparation à la greffe rénale (receveur et donneurs vivants candidats). */
public interface ImpressionDuDossierDeGreffe {

    /** @param carte absente : l'en-tête du patient est omis. */
    byte[] enPdf(CentreId centre, Optional<CartesDesPatients.Carte> carte, DossierDeGreffe dossier);
}
