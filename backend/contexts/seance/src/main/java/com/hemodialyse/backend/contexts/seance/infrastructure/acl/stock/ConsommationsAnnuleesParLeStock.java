package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.application.AnnulationDesConsommations;
import com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;

/**
 * ACL vers le langage publié du Stock ({@link ConsommationsDeSeance#annuler}) : l'annulation des consommations d'une séance supprimée.
 * Un refus du Stock (séance facturée, pièce de sortie déjà rapprochée) devient {@link ConsommationRefusee} : 422 avec le code du Stock.
 * L'auteur est transmis tel quel (le Stock ne l'enregistre pas, KB-SEA-33).
 */
public class ConsommationsAnnuleesParLeStock implements AnnulationDesConsommations {

    private final ConsommationsDeSeance stock;

    public ConsommationsAnnuleesParLeStock(ConsommationsDeSeance stock) {
        this.stock = stock;
    }

    @Override
    public void annuler(CentreId centre, SeanceId seance, String auteur) {
        try {
            stock.annuler(centre.valeur(), seance.valeur(), auteur);
        } catch (com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee refus) {
            throw new ConsommationRefusee(refus.code(), refus.getMessage());
        }
    }
}
