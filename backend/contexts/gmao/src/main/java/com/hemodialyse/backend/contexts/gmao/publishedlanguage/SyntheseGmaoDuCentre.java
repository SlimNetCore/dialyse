package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du contexte GMAO : la synthèse GMAO d'un centre sur une période, lue par le pilotage de la direction
 * (tableau de bord, alertes). Lecture seule ; le module fournit les chiffres et l'analyse de chaque équipement (coût de
 * maintenance, indisponibilité, réforme recommandée), le consommateur décide du classement et des alertes.
 */
public interface SyntheseGmaoDuCentre {

    /** @param du début de la période (inclus) ; @param au fin de la période (exclue) */
    Synthese duCentre(UUID centre, Instant du, Instant au);

    /**
     * @param coutDeMaintenance           des interventions démarrées sur la période
     * @param indisponibiliteHeures       cumulée de tous les équipements sur la période
     * @param equipements                 dans l'ordre de lecture du parc, qui n'est pas garanti (KB-GMAO-78)
     */
    record Synthese(long nombreDEquipements, long horsService, long enMaintenance, long reformes, long interventionsEnCours,
                    BigDecimal coutDeMaintenance, double indisponibiliteHeures, List<Equipement> equipements) {
    }

    /** @param ratioMaintenance nul si le prix d'acquisition est inconnu */
    record Equipement(UUID id, String code, String designation, String statut, BigDecimal coutDeLaPeriode,
                      BigDecimal coutCumule, BigDecimal prixAcquisition, BigDecimal ratioMaintenance,
                      double indisponibiliteHeures, boolean reformeRecommandee) {
    }
}
