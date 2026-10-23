package com.hemodialyse.backend.infrastructure.stock;

import com.hemodialyse.backend.contexts.stock.interfaces.consommations.ConsommationsDeSeanceDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ParametresDuStockDuCentre;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aiguillage par centre du port publié {@link ConsommationsDeSeance} (ADR 0045) : un centre qui a activé la sortie pour séances
 * consomme sur la pièce du périmètre (aucun mouvement de stock) ; sinon la séance garde la sortie immédiate FEFO. Retour arrière :
 * désactiver le paramètre du centre (les pièces déjà enregistrées doivent être rapprochées).
 */
@Component
@Primary
public class ConsommationsDeSeanceAiguillage implements ConsommationsDeSeance {
    private final ParametresDuStockDuCentre parametres;
    private final ConsommationsDeSeanceDuStock nouveau;
    private final ConsommationsDeSeanceLegacy historique;

    public ConsommationsDeSeanceAiguillage(ParametresDuStockDuCentre parametres, ConsommationsDeSeanceDuStock nouveau,
                                           ConsommationsDeSeanceLegacy historique) {
        this.parametres = parametres;
        this.nouveau = nouveau;
        this.historique = historique;
    }

    private ConsommationsDeSeance cible(UUID centre) {
        return parametres.sortiePourSeancesActivee(centre) ? nouveau : historique;
    }

    @Override
    public void ajouter(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur) {
        cible(centre).ajouter(centre, seance, patient, dateSeance, article, quantite, auteur);
    }

    @Override
    public void fixer(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur) {
        cible(centre).fixer(centre, seance, patient, dateSeance, article, quantite, auteur);
    }

    @Override
    public void annuler(UUID centre, UUID seance, String auteur) {
        cible(centre).annuler(centre, seance, auteur);
    }

    @Override
    public void administrer(UUID centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite, String auteur) {
        cible(centre).administrer(centre, seance, patient, jour, article, quantite, auteur);
    }

    @Override
    public void enregistrerALaValidation(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, List<Ligne> lignes,
                                         String auteur) {
        cible(centre).enregistrerALaValidation(centre, seance, patient, dateSeance, lignes, auteur);
    }
}