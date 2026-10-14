package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PageDInterventions;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutTache;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Réponse JSON d'une intervention : mêmes champs, même ordre et mêmes types sérialisés que le legacy
 * ({@code InterventionResponse}) ; les instants sont exposés en {@link OffsetDateTime} UTC. Les montants gardent leur échelle
 * (KB-GMAO-44 : jusqu'à cinq décimales, reproduit). Seul ajout au contrat observé : {@code taches} (R21, KB-GMAO-55), présent
 * seulement quand l'intervention en porte, donc invisible pour les clients qui ne les utilisent pas.
 */
public record InterventionReponse(
        UUID id,
        UUID equipementId,
        UUID centreId,
        TypeIntervention type,
        StatutIntervention statut,
        OffsetDateTime dateDebut,
        OffsetDateTime dateFin,
        UUID intervenantId,
        StatutEquipement etatEquipementAvant,
        StatutEquipement etatEquipementApres,
        PrioriteIntervention priorite,
        OffsetDateTime echeance,
        String symptome,
        String cause,
        boolean enRetard,
        boolean echeanceDepassee,
        int nbRectifications,
        String description,
        String actions,
        String pieceRemplacee,
        List<LigneDeCoutReponse> lignesCout,
        BigDecimal coutTotal,
        String observations,
        OffsetDateTime dateCreation,
        OffsetDateTime dateModification,
        UUID creePar,
        UUID modifiePar,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TacheReponse> taches) {

    static InterventionReponse depuis(InterventionVue vue) {
        return new InterventionReponse(vue.id(), vue.equipementId(), vue.centreId(), vue.type(), vue.statut(),
                utc(vue.debut()), utc(vue.fin()), vue.intervenantId(), vue.etatAvant(), vue.etatApres(), vue.priorite(),
                utc(vue.echeance()), vue.symptome(), vue.cause(), vue.enRetard(), vue.echeanceDepassee(),
                vue.nombreDeRectifications(), vue.description(), vue.actions(), vue.pieceRemplacee(),
                vue.lignes().stream().map(LigneDeCoutReponse::depuis).toList(), vue.coutTotal(), vue.observations(),
                utc(vue.creeLe()), utc(vue.modifieLe()), vue.creePar(), vue.modifiePar(),
                vue.taches().stream().map(TacheReponse::depuis).toList());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /** Tâche d'intervention (R21) : statut et jalons de démarrage et d'achèvement (absents tant qu'ils n'ont pas eu lieu). */
    public record TacheReponse(UUID id, String description, StatutTache statut, OffsetDateTime dateCreation, UUID creePar,
                               OffsetDateTime dateDemarrage, UUID demarrePar, OffsetDateTime dateAchevement, UUID achevePar) {

        static TacheReponse depuis(InterventionVue.TacheVue tache) {
            return new TacheReponse(tache.id(), tache.description(), tache.statut(), utc(tache.creeLe()), tache.creePar(),
                    utc(tache.demarreLe()), tache.demarrePar(), utc(tache.acheveLe()), tache.achevePar());
        }
    }

    /** Ligne de coût : mêmes champs que le legacy ({@code LigneCoutResponse}). */
    public record LigneDeCoutReponse(UUID id, TypeLigneCout type, String libelle, BigDecimal quantite,
                                     BigDecimal prixUnitaire, BigDecimal montant, UUID articleStockId, boolean automatique) {

        static LigneDeCoutReponse depuis(InterventionVue.LigneVue ligne) {
            return new LigneDeCoutReponse(ligne.id(), ligne.type(), ligne.libelle(), ligne.quantite(), ligne.prixUnitaire(),
                    ligne.montant(), ligne.articleStockId(), ligne.automatique());
        }
    }

    /** Page d'interventions : même forme que le contrat observé ({@code items}, {@code total}, {@code page}, {@code size}). */
    public record Page(List<InterventionReponse> items, long total, int page, int size) {

        static Page depuis(PageDInterventions<InterventionVue> page) {
            return new Page(page.items().stream().map(InterventionReponse::depuis).toList(), page.total(), page.page(),
                    page.size());
        }
    }
}
