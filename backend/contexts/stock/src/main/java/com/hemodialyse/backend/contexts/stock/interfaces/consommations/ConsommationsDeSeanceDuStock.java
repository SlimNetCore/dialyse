package com.hemodialyse.backend.contexts.stock.interfaces.consommations;

import com.hemodialyse.backend.contexts.stock.application.consommations.GestionDesConsommations;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant du langage publié {@link ConsommationsDeSeance} : la consommation de séance imputée sur la sortie pour séances (sans
 * sortie de stock). Appelé par la Séance, par l'aiguillage de la plateforme quand le centre a activé la sortie pour séances.
 */
public class ConsommationsDeSeanceDuStock implements ConsommationsDeSeance {
    private final GestionDesConsommations consommations;

    public ConsommationsDeSeanceDuStock(GestionDesConsommations consommations) {
        this.consommations = consommations;
    }

    @Override
    public void ajouter(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur) {
        consommations.ajouter(new CentreId(centre), seance, patient, dateSeance, article, quantite);
    }

    @Override
    public void fixer(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur) {
        consommations.fixer(new CentreId(centre), seance, patient, dateSeance, article, quantite);
    }

    @Override
    public void annuler(UUID centre, UUID seance, String auteur) {
        consommations.annuler(new CentreId(centre), seance);
    }

    @Override
    public void administrer(UUID centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite, String auteur) {
        consommations.ajouter(new CentreId(centre), seance, patient, jour, article, quantite);
    }

    @Override
    public void enregistrerALaValidation(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, List<Ligne> lignes,
                                         String auteur) {
        for (Ligne ligne : lignes) {
            consommations.ajouter(new CentreId(centre), seance, patient, dateSeance, ligne.article(), ligne.quantite());
        }
    }
}