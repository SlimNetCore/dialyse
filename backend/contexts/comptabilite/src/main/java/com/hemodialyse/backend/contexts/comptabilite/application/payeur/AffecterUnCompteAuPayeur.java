package com.hemodialyse.backend.contexts.comptabilite.application.payeur;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeur;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeurRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurIntrouvable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;

/**
 * Affecte un compte client au payeur du centre ; un compte vide, blanc ou absent le retire (retour au compte client par
 * défaut du centre). Le payeur est vérifié avant le compte.
 */
public final class AffecterUnCompteAuPayeur {

    private final LectureDesPayeurs payeurs;
    private final CompteClientDuPayeurRepository comptesClients;
    private final PlanDuCentre plan;
    private final UniteDeTravail uniteDeTravail;

    public AffecterUnCompteAuPayeur(LectureDesPayeurs payeurs, CompteClientDuPayeurRepository comptesClients, PlanDuCentre plan,
                                    UniteDeTravail uniteDeTravail) {
        this.payeurs = payeurs;
        this.comptesClients = comptesClients;
        this.plan = plan;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PayeurVue executer(CentreId centre, PayeurId payeurId, String compte) {
        return uniteDeTravail.executer(() -> {
            PayeurVue payeur = payeurs.trouver(centre, payeurId).orElseThrow(PayeurIntrouvable::new);
            if (compte == null || compte.isBlank()) {
                comptesClients.retirer(centre, payeurId);
                return new PayeurVue(payeurId.valeur(), payeur.code(), payeur.nom(), null);
            }
            NumeroDeCompte numero = NumeroDeCompte.de(compte);
            comptesClients.enregistrer(CompteClientDuPayeur.affecter(centre, payeurId, numero, plan));
            return new PayeurVue(payeurId.valeur(), payeur.code(), payeur.nom(), numero.valeur());
        });
    }
}
