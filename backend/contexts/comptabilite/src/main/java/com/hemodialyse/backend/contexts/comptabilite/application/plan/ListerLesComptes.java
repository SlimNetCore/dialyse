package com.hemodialyse.backend.contexts.comptabilite.application.plan;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;

/**
 * Comptes du centre, triés par numéro : le plan de départ tant qu'il n'a rien enregistré (RG-CPT-018), sans rien enregistrer
 * en lecture. La taille de page est bornée de 1 à 500 (assez pour proposer tout le plan dans une liste de choix).
 */
public final class ListerLesComptes {

    static final int TAILLE_MAX = 500;

    private final PlanDuCentre plan;
    private final LecturePlan lecture;
    private final UniteDeTravail uniteDeTravail;

    public ListerLesComptes(PlanDuCentre plan, LecturePlan lecture, UniteDeTravail uniteDeTravail) {
        this.plan = plan;
        this.lecture = lecture;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PageDe<CompteVue> executer(CentreId centre, String recherche, boolean actifsSeulement, int page, int size) {
        int taille = Math.max(1, Math.min(size, TAILLE_MAX));
        int numero = Math.max(0, page);
        return uniteDeTravail.executer(() -> {
            if (plan.aEnregistreSesComptes(centre)) {
                return lecture.page(centre, recherche, actifsSeulement, numero, taille);
            }
            // plan de départ : quelques dizaines de lignes, filtrées et découpées ici ('actifs' sans effet : tout est actif)
            return PageDe.decoupee(plan.planDeDepart(centre).rechercher(recherche).stream().map(CompteVue::depuis).toList(),
                    numero, taille);
        });
    }
}
