package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AccesRefuse;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SousLaGardeDuDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AnalyseIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeIllisible;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivantIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilanIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaireIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.OrdonnanceIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Les refus des ordonnances, prescriptions, demandes d'examen et sérologies (sérologie introuvable : 400 reproduit ; écriture
 * concurrente : 409 {@code SEROLOGIE_ECRITURE_CONCURRENTE}), au format commun de la plateforme ({@code ProblemDetail} + {@code code} stable) :
 * 400 {@code BAD_REQUEST} (corps, date, identifiant ou paramètre illisible, ordonnance introuvable : reproduit) ou
 * {@code VALIDATION_ERROR} (valeur hors borne : reproduit), 403 {@code ACCESS_DENIED}, 404 {@code PATIENT_INTROUVABLE} /
 * {@code PRESCRIPTION_INTROUVABLE}, 409 {@code ECRITURE_CONCURRENTE}, 422 (règle de gestion, code de la règle). Aucun message ne
 * reprend de donnée de santé.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {OrdonnancesControleur.class, PrescriptionsControleur.class, AnemieControleur.class,
        DemandesDExamenControleur.class, SerologiesControleur.class, AntecedentsControleur.class,
        AllergiesControleur.class, AbordsVasculairesControleur.class, ObservationsBiologiquesControleur.class, AnalysesControleur.class,
        BilanPreGreffeControleur.class, EtapesDeBilanControleur.class, DonneursVivantsControleur.class, DossierMedicalDeBaseControleur.class,
        ExportFhirControleur.class, EvaluationKdigoControleur.class, GreffeExportControleur.class, SousLaGardeDuDossierMedical.class})
class ErreursDuDossierMedicalAdvice {

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail, String code) {
        ProblemDetail probleme = ProblemDetail.forStatus(statut);
        probleme.setTitle(titre);
        probleme.setDetail(detail);
        probleme.setProperty("code", code);
        return probleme;
    }

    @ExceptionHandler(SaisieInvalide.class)
    ProblemDetail saisieInvalide(SaisieInvalide erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(ValeurHorsBorne.class)
    ProblemDetail valeurHorsBorne(ValeurHorsBorne erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Validation", erreur.getMessage(), "VALIDATION_ERROR");
    }

    /**
     * Corps ou paramètre illisible : 400, SAUF si le profil n'a pas le droit d'appeler la route. La lecture du corps précède le contrôle
     * {@code @PreAuthorize} de la méthode ; sans cette garde, un profil refusé recevrait 400 (et non 403) et apprendrait la forme de la
     * route. Les rôles sont ceux de l'annotation {@code @PreAuthorize} du contrôleur ciblé.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail illisible(Exception erreur, HttpServletRequest requete) {
        if (!droitsSuffisants(requete)) {
            return probleme(HttpStatus.FORBIDDEN, "Acces refuse", "Accès refusé", "ACCESS_DENIED");
        }
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", "Corps ou paramètre illisible", "BAD_REQUEST");
    }

    private static boolean droitsSuffisants(HttpServletRequest requete) {
        if (!(requete.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod methode)) return true;
        PreAuthorize garde = methode.getMethodAnnotation(PreAuthorize.class);
        if (garde == null) return true;
        return DroitsDeLaRoute.suffisants(garde.value(), SecurityContextHolder.getContext().getAuthentication());
    }

    @ExceptionHandler(OrdonnanceIntrouvable.class)
    ProblemDetail ordonnanceIntrouvable(OrdonnanceIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(PatientIntrouvable.class)
    ProblemDetail patientIntrouvable(PatientIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Patient introuvable", erreur.getMessage(), "PATIENT_INTROUVABLE");
    }

    @ExceptionHandler(PrescriptionIntrouvable.class)
    ProblemDetail prescriptionIntrouvable(PrescriptionIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Prescription introuvable", erreur.getMessage(), "PRESCRIPTION_INTROUVABLE");
    }

    @ExceptionHandler(AccesRefuse.class)
    ProblemDetail accesRefuse(AccesRefuse erreur) {
        return probleme(HttpStatus.FORBIDDEN, "Acces refuse", erreur.getMessage(), "ACCESS_DENIED");
    }

    @ExceptionHandler(EcritureConcurrente.class)
    ProblemDetail ecritureConcurrente(EcritureConcurrente erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUneDemandeDExamen.class)
    ProblemDetail ecritureConcurrenteSurUneDemandeDExamen(EcritureConcurrenteSurUneDemandeDExamen erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "DEMANDE_EXAMEN_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUneSerologie.class)
    ProblemDetail ecritureConcurrenteSurUneSerologie(EcritureConcurrenteSurUneSerologie erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "SEROLOGIE_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUnAntecedent.class)
    ProblemDetail ecritureConcurrenteSurUnAntecedent(EcritureConcurrenteSurUnAntecedent erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ANTECEDENT_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUneAllergie.class)
    ProblemDetail ecritureConcurrenteSurUneAllergie(EcritureConcurrenteSurUneAllergie erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ALLERGIE_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUnAbordVasculaire.class)
    ProblemDetail ecritureConcurrenteSurUnAbordVasculaire(EcritureConcurrenteSurUnAbordVasculaire erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ABORD_VASCULAIRE_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUneObservation.class)
    ProblemDetail ecritureConcurrenteSurUneObservation(EcritureConcurrenteSurUneObservation erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "OBSERVATION_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUneAnalyse.class)
    ProblemDetail ecritureConcurrenteSurUneAnalyse(EcritureConcurrenteSurUneAnalyse erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ANALYSE_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUnBilanPreGreffe.class)
    ProblemDetail ecritureConcurrenteSurUnBilanPreGreffe(EcritureConcurrenteSurUnBilanPreGreffe erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "BILAN_PRE_GREFFE_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUneEtapeDeBilan.class)
    ProblemDetail ecritureConcurrenteSurUneEtapeDeBilan(EcritureConcurrenteSurUneEtapeDeBilan erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "ETAPE_BILAN_ECRITURE_CONCURRENTE");
    }

    @ExceptionHandler(EcritureConcurrenteSurUnDonneurVivant.class)
    ProblemDetail ecritureConcurrenteSurUnDonneurVivant(EcritureConcurrenteSurUnDonneurVivant erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "DONNEUR_VIVANT_ECRITURE_CONCURRENTE");
    }

    /** Écart décidé (KB-MED-98) : écriture simultanée ou dossier déjà tenu pour ce patient dans un autre centre : 409 (avant : 500). */
    @ExceptionHandler(EcritureConcurrenteSurUnDossierMedical.class)
    ProblemDetail ecritureConcurrenteSurUnDossierMedical(EcritureConcurrenteSurUnDossierMedical erreur) {
        return probleme(HttpStatus.CONFLICT, "Ecriture concurrente", erreur.getMessage(), "DOSSIER_MEDICAL_ECRITURE_CONCURRENTE");
    }

    /** Écart décidé (KB-MED-88) : l'étape inconnue, d'un autre patient ou d'un autre centre est refusée en 404 (avant : 400). */
    @ExceptionHandler(EtapeDeBilanIntrouvable.class)
    ProblemDetail etapeDeBilanIntrouvable(EtapeDeBilanIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Etape introuvable", erreur.getMessage(), "ETAPE_BILAN_INTROUVABLE");
    }

    /** Écart décidé (KB-MED-88) : le donneur inconnu, d'un autre patient ou d'un autre centre est refusé en 404 (avant : 400). */
    @ExceptionHandler(DonneurVivantIntrouvable.class)
    ProblemDetail donneurVivantIntrouvable(DonneurVivantIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Donneur introuvable", erreur.getMessage(), "DONNEUR_VIVANT_INTROUVABLE");
    }

    /** Statut du bilan illisible en base (KB-MED-90) : 422, sans donnée de santé ni nom de classe dans le message. */
    @ExceptionHandler(BilanPreGreffeIllisible.class)
    ProblemDetail bilanPreGreffeIllisible(BilanPreGreffeIllisible erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Bilan illisible", erreur.getMessage(), "BILAN_PRE_GREFFE_ILLISIBLE");
    }

    /** Reproduit du legacy (KB-MED-76) : une observation introuvable est une requête invalide (400), pas un 404. */
    @ExceptionHandler(ObservationIntrouvable.class)
    ProblemDetail observationIntrouvable(ObservationIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    /** Écart décidé (KB-MED-74) : le PUT d'une analyse inconnue, d'un autre patient ou d'un autre centre est refusé en 404 (avant : upsert). */
    @ExceptionHandler(AnalyseIntrouvable.class)
    ProblemDetail analyseIntrouvable(AnalyseIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Analyse introuvable", erreur.getMessage(), "ANALYSE_INTROUVABLE");
    }

    /** Écart décidé (KB-MED-62) : le PUT d'un abord inconnu, d'un autre patient ou d'un autre centre est refusé en 404 (avant : upsert). */
    @ExceptionHandler(AbordVasculaireIntrouvable.class)
    ProblemDetail abordVasculaireIntrouvable(AbordVasculaireIntrouvable erreur) {
        return probleme(HttpStatus.NOT_FOUND, "Abord vasculaire introuvable", erreur.getMessage(), "ABORD_VASCULAIRE_INTROUVABLE");
    }

    /** Reproduit du legacy (KB-MED-60) : une allergie introuvable est une requête invalide (400), pas un 404. */
    @ExceptionHandler(AllergieIntrouvable.class)
    ProblemDetail allergieIntrouvable(AllergieIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    /** Reproduit du legacy (KB-MED-60) : un antécédent introuvable est une requête invalide (400), pas un 404. */
    @ExceptionHandler(AntecedentIntrouvable.class)
    ProblemDetail antecedentIntrouvable(AntecedentIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(SerologieIntrouvable.class)
    ProblemDetail serologieIntrouvable(SerologieIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(DemandeExamenIntrouvable.class)
    ProblemDetail demandeExamenIntrouvable(DemandeExamenIntrouvable erreur) {
        return probleme(HttpStatus.BAD_REQUEST, "Requete invalide", erreur.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(RegleDeGestionViolee.class)
    ProblemDetail regleDeGestion(RegleDeGestionViolee erreur) {
        return probleme(HttpStatus.UNPROCESSABLE_CONTENT, "Regle de gestion", erreur.getMessage(), erreur.code());
    }
}
