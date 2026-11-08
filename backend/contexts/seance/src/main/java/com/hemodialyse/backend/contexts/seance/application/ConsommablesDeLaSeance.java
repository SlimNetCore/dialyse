package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.QuantiteConsommee;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : la déclaration des consommables de la séance au Stock. Le Stock décide (imputation sur la sortie pour séances du jour,
 * alertes, lots, rapprochement) ; la Séance ne touche ni lot ni mouvement : KB-SEA-22, aucune sortie de stock n'a lieu à la déclaration
 * (la Javadoc du legacy qui promettait « FEFO » et « restitution » était fausse).
 * <p>
 * Refus du Stock : article inconnu, d'un autre centre ou inactif, quantité invalide : {@code SaisieInvalide} (400, un seul statut pour la
 * même cause sur les trois routes, KB-SEA-25) ; séance facturée, pièce déjà rapprochée : {@link ConsommationRefusee} (422, code du Stock).
 * <p>
 * KB-SEA-33 (risque médico-légal, reproduit) : l'auteur est transmis, mais l'adapter du Stock ne l'enregistre PAS.
 */
public interface ConsommablesDeLaSeance {

    /** Ajoute {@code quantite} aux consommables de l'article sur la séance (cumul, KB-SEA-23). */
    void ajouter(CentreId centre, Seance seance, ArticleId article, QuantiteConsommee quantite, String auteur);

    /** Fixe la quantité TOTALE de l'article sur la séance ; l'article jamais déclaré est créé (KB-SEA-23). */
    void remplacer(CentreId centre, Seance seance, ArticleId article, QuantiteConsommee quantite, String auteur);

    /** Met à zéro la quantité de l'article sur la séance ; un article non déclaré est sans effet (KB-SEA-23) ; aucune restitution de stock. */
    void retirer(CentreId centre, Seance seance, ArticleId article, String auteur);
}
