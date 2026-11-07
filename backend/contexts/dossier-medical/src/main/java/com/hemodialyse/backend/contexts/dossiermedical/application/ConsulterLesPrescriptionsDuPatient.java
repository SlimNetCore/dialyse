package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Prescriptions d'un patient du centre de la session, de la plus récente à la plus ancienne, filtrées par période et paginées.
 * Deux entrées, deux bornes (reproduites du legacy) : la LISTE normalise (page négative = 0, taille nulle ou négative = 20, plafond
 * 200) ; la RECHERCHE par corps exige page >= 0 et taille de 1 à 200 (400 {@code VALIDATION_ERROR} sinon). Patient inconnu :
 * page vide. Droits : MEDECIN et ADMIN.
 */
public class ConsulterLesPrescriptionsDuPatient {

    public record Page(List<PrescriptionDetaillee> items, long total, int page, int taille) {
    }

    private final SessionCourante session;
    private final Prescriptions prescriptions;
    private final DetaillerLesPrescriptions details;

    public ConsulterLesPrescriptionsDuPatient(SessionCourante session, Prescriptions prescriptions, CatalogueDesArticles catalogue) {
        this.session = session;
        this.prescriptions = prescriptions;
        this.details = new DetaillerLesPrescriptions(catalogue);
    }

    public Page lister(UUID centreDemande, UUID patient, LocalDate du, LocalDate au, int page, int taille) {
        int taillePosee = taille <= 0 ? 20 : Math.min(taille, 200);
        return executer(centreDemande, patient, new Periode(du, au), Math.max(page, 0), taillePosee);
    }

    /** Recherche par corps : le centre et le patient sont obligatoires, page et taille sont bornées sans être corrigées. */
    public Page rechercher(UUID centreDemande, UUID patient, Integer page, Integer taille, LocalDate du, LocalDate au) {
        if (centreDemande == null) throw new ValeurHorsBorne("Le centre est obligatoire");
        if (patient == null) throw new ValeurHorsBorne("Le patient est obligatoire");
        if (page == null || page < 0) throw new ValeurHorsBorne("La page doit être >= 0");
        if (taille == null || taille < 1 || taille > 200) throw new ValeurHorsBorne("La taille doit être comprise entre 1 et 200");
        return executer(centreDemande, patient, new Periode(du, au), page, taille);
    }

    private Page executer(UUID centreDemande, UUID patient, Periode periode, int page, int taille) {
        var centre = session.centre(centreDemande);
        var lue = prescriptions.duPatient(centre, new PatientId(patient), periode, page, taille);
        return new Page(lue.items().stream().map(p -> details.de(centre, p)).toList(), lue.total(), lue.page(), lue.taille());
    }
}
