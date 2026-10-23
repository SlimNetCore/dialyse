package com.hemodialyse.backend.contexts.stock.application.reception;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.CalculDuPmp;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock.MiseAJourDeMouvement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rejeu du PMP d'un article (RG-STK-060, 062) : tout l'historique est rejoué dans l'ordre (date, heure de saisie, entrées avant
 * sorties) ; {@code pmp_apres} de chaque mouvement, PMP et quantité de l'article sont réécrits, et chaque sortie est revalorisée
 * au PMP qui la précède, sauf si l'historique rejoué ne donne aucun PMP (le prix de sortie enregistré est alors conservé,
 * KB-STK-01). À appeler dans la transaction de l'appelant, l'article étant verrouillé.
 */
public final class RejeuDuPmp {

    private final LecturesDuStock lectures;
    private final EcrituresDuStock ecritures;

    public RejeuDuPmp(LecturesDuStock lectures, EcrituresDuStock ecritures) {
        this.lectures = lectures;
        this.ecritures = ecritures;
    }

    public CalculDuPmp.Etat rejouer(CentreId centre, UUID articleId) {
        List<MouvementDeStock> historique = lectures.mouvementsDeLArticle(centre, articleId);
        List<CalculDuPmp.Etape> etapes = CalculDuPmp.expliquer(historique);
        List<MiseAJourDeMouvement> mises = new ArrayList<>(etapes.size());
        CalculDuPmp.Etat fin = CalculDuPmp.Etat.vide();
        for (CalculDuPmp.Etape etape : etapes) {
            fin = etape.apres();
            boolean sortie = etape.mouvement().type() == MouvementDeStock.Type.SORTIE;
            mises.add(new MiseAJourDeMouvement(etape.mouvement().id(), etape.apres().pmp(),
                    sortie && etape.avant().pmp().signum() > 0 ? etape.avant().pmp() : null));
        }
        ecritures.appliquerLeRejeu(centre, articleId, mises, fin.pmp(), fin.quantite());
        return fin;
    }
}

