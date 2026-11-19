package com.hemodialyse.backend.contexts.stock.application.consultation.seances;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.PagesDesSortiesPourSeances.FiltresDesPieces;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.PagesDesSortiesPourSeances.LigneDetaillee;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.PagesDesSortiesPourSeances.ResumeDePiece;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;

import java.util.Set;
import java.util.UUID;

/**
 * Sorties pour séances paginées (RG-STK-091). Pièces : tri {@code reference}, {@code jour}, {@code statut}, {@code lignes},
 * {@code enregistreeLe} (défaut : enregistrée le décroissant puis référence décroissante), filtres {@code reference} (texte) et
 * {@code statut} (énumération {@code ENREGISTREE}, {@code RAPPROCHEE}). Lignes d'une pièce : tri {@code article} (libellé),
 * {@code sortie}, {@code consomme}, {@code ecart} (défaut : libellé de l'article), filtre {@code article} (code ou libellé) ; une
 * pièce inconnue du centre est refusée comme sa consultation ({@code PIECE_INTROUVABLE}).
 */
public final class ConsulterLesSortiesPourSeances {

    public static final Set<String> TRIS_DES_PIECES = Set.of("reference", "jour", "statut", "lignes", "enregistreeLe");
    public static final Set<String> TRIS_DES_LIGNES = Set.of("article", "sortie", "consomme", "ecart");

    private final PagesDesSortiesPourSeances pages;

    public ConsulterLesSortiesPourSeances(PagesDesSortiesPourSeances pages) {
        this.pages = pages;
    }

    /** @throws com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide paramètres refusés */
    public PageDeResultats<ResumeDePiece> pieces(CentreId centre, Integer page, Integer taille, String tri, String reference,
                                                 String statut) {
        DemandeDePage demande = DemandeDePage.lire(page, taille, tri, TRIS_DES_PIECES, "enregistreeLe", true);
        FiltresDesPieces filtres = new FiltresDesPieces(FiltresDePage.texte(reference),
                FiltresDePage.enumeration("statut", statut, SortiePourSeances.Statut.class));
        return pages.pieces(centre, filtres, demande);
    }

    /**
     * @throws com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide paramètres refusés
     * @throws RefusDuReferentiel {@code PIECE_INTROUVABLE} (pièce inconnue ou d'un autre centre)
     */
    public PageDeResultats<LigneDetaillee> lignes(CentreId centre, UUID piece, Integer page, Integer taille, String tri,
                                                  String article) {
        DemandeDePage demande = DemandeDePage.lire(page, taille, tri, TRIS_DES_LIGNES, "article", false);
        var terme = FiltresDePage.texte(article);
        if (!pages.existe(centre, piece)) {
            throw new RefusDuReferentiel("PIECE_INTROUVABLE", "Sortie pour séances introuvable");
        }
        return pages.lignes(centre, piece, terme, demande);
    }
}
