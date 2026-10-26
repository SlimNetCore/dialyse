package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié (besoin du Stock, fourni par la plateforme : centre de notifications, hors contexte) : l'alerte durable « lots
 * proches de la péremption » du centre, destinée au pharmacien et à l'administrateur. Une seule alerte par centre et par jour du
 * centre (clé déterministe, sûre en multi-instance).
 */
public interface NotificationsDesPeremptions {

    /**
     * @param jour jour du centre du contrôle (la clé de l'alerte avec le centre)
     * @param lots les lots à signaler, les plus proches d'abord (jamais vide)
     * @return vrai si l'alerte a été émise, faux si celle de ce jour existait déjà
     */
    boolean signaler(UUID centre, LocalDate jour, List<LotSignale> lots);

    record LotSignale(String numeroLot, String codeArticle, String libelleArticle, LocalDate datePeremption, BigDecimal quantite,
                      boolean perime) {
    }
}
