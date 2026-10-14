package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Jalon;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutTache;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Tache;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue de lecture d'une intervention (jamais l'agrégat). {@code enRetard} et {@code echeanceDepassee} sont calculés à
 * l'instant de la lecture (KB-GMAO-50). Les montants gardent l'échelle de leurs facteurs (KB-GMAO-44) : celle reçue pour la
 * réponse d'une commande, celle de la base pour les lectures.
 */
public record InterventionVue(UUID id, UUID equipementId, UUID centreId, TypeIntervention type, StatutIntervention statut,
                              Instant debut, Instant fin, UUID intervenantId, StatutEquipement etatAvant,
                              StatutEquipement etatApres, PrioriteIntervention priorite, Instant echeance, String symptome,
                              String cause, boolean enRetard, boolean echeanceDepassee, int nombreDeRectifications,
                              String description, String actions, String pieceRemplacee, List<LigneVue> lignes,
                              BigDecimal coutTotal, String observations, Instant creeLe, Instant modifieLe, UUID creePar,
                              UUID modifiePar, List<TacheVue> taches) {

    public static InterventionVue depuis(Intervention intervention, Instant maintenant) {
        Jalon modification = intervention.modification();
        return new InterventionVue(intervention.id().valeur(), intervention.equipementId().valeur(),
                intervention.centreId().valeur(), intervention.type(), intervention.statut(), intervention.debut(),
                intervention.fin(), intervention.intervenantId() == null ? null : intervention.intervenantId().valeur(),
                intervention.etatAvant(), intervention.etatApres(), intervention.priorite(), intervention.echeance(),
                intervention.symptome(), intervention.cause(), intervention.enRetard(maintenant),
                intervention.echeanceDepassee(maintenant), intervention.rectifications().size(), intervention.description(),
                intervention.actions(), intervention.pieceRemplacee(),
                intervention.lignes().stream().map(LigneVue::depuis).toList(), intervention.coutTotal(),
                intervention.observations(), intervention.creation().le(), modification == null ? null : modification.le(),
                intervention.creation().par() == null ? null : intervention.creation().par().valeur(),
                modification == null || modification.par() == null ? null : modification.par().valeur(),
                intervention.taches().stream().map(TacheVue::depuis).toList());
    }

    /** Tâche vue (R21) : statut et jalons de démarrage et d'achèvement (absents tant qu'ils n'ont pas eu lieu). */
    public record TacheVue(UUID id, String description, StatutTache statut, Instant creeLe, UUID creePar, Instant demarreLe,
                           UUID demarrePar, Instant acheveLe, UUID achevePar) {

        static TacheVue depuis(Tache tache) {
            return new TacheVue(tache.id().valeur(), tache.description(), tache.statut(), tache.creation().le(),
                    par(tache.creation()), le(tache.demarrage()), par(tache.demarrage()), le(tache.achevement()),
                    par(tache.achevement()));
        }

        private static UUID par(Jalon jalon) {
            return jalon == null || jalon.par() == null ? null : jalon.par().valeur();
        }

        private static Instant le(Jalon jalon) {
            return jalon == null ? null : jalon.le();
        }
    }

    /** Ligne de coût vue : {@code montant} est quantité x prix, sans arrondi. */
    public record LigneVue(UUID id, TypeLigneCout type, String libelle, BigDecimal quantite, BigDecimal prixUnitaire,
                           BigDecimal montant, UUID articleStockId, boolean automatique) {

        static LigneVue depuis(LigneDeCout ligne) {
            return new LigneVue(ligne.id().valeur(), ligne.type(), ligne.libelle(), ligne.quantite(), ligne.prixUnitaire(),
                    ligne.montant(), ligne.articleStockId(), ligne.estAutomatique());
        }
    }
}
