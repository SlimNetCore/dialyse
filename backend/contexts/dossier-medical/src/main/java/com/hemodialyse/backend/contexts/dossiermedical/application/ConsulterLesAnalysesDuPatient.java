package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Consulte les analyses d'un patient du centre de la session, du prélèvement le plus récent au plus ancien. Liste : bornes REPRODUITES du
 * legacy (KB-MED-83) page négative = 0, taille nulle ou négative = 20, plafond 200. Recherche ({@code POST analyses/search}) : AUCUN critère
 * hors patient et période (reproduit) ; centre ou patient absent, page négative, taille hors 1 à 200 : 400 {@code VALIDATION_ERROR}
 * (reproduit, message du legacy conservé), page ou taille absente : 400 {@code BAD_REQUEST} (avant : 500) ; ces contrôles précèdent le
 * contrôle du centre de la session (comme le legacy). Période inversée : 400 dans les deux cas. Patient inconnu ou d'un autre centre :
 * page vide (200). La recherche est une LECTURE que le journal d'audit inscrit comme une création (KB-MED-81 reproduit, à revoir avec le
 * DPO). Droits : MEDECIN et ADMIN.
 */
public class ConsulterLesAnalysesDuPatient {
    static final String MESSAGE_DE_VALIDATION = "Invalid request content.";

    private final SessionCourante session;
    private final Analyses analyses;

    public ConsulterLesAnalysesDuPatient(SessionCourante session, Analyses analyses) {
        this.session = session;
        this.analyses = analyses;
    }

    public PageDeFiches<Analyse> liste(UUID centreDemande, UUID patient, LocalDate du, LocalDate au, int page, int taille) {
        var centre = session.centre(centreDemande);
        var periode = new Periode(du, au);
        return analyses.duPatient(centre, new PatientId(patient), periode, BornesDePage.page(page), BornesDePage.taille(taille));
    }

    public PageDeFiches<Analyse> recherche(UUID centreDemande, UUID patient, Integer page, Integer taille, LocalDate du, LocalDate au) {
        if (page == null || taille == null) throw new SaisieInvalide("La page et la taille sont obligatoires");
        if (centreDemande == null || patient == null || page < 0 || taille < 1 || taille > BornesDePage.TAILLE_MAXIMALE) {
            throw new ValeurHorsBorne(MESSAGE_DE_VALIDATION);
        }
        var centre = session.centre(centreDemande);
        var periode = new Periode(du, au);
        return analyses.duPatient(centre, new PatientId(patient), periode, page, taille);
    }
}
