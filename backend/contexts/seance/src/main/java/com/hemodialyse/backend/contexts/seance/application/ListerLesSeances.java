package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.RechercheDeSeances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Liste les séances du centre de la session (GET /api/v1/seances) : filtres facultatifs, tri en liste blanche, pagination bornée, forfait
 * courant de chaque ligne. Garde de centre d'abord (403), puis lecture des filtres (400 : statut inconnu, période inversée). Les droits
 * sont portés par l'interface.
 */
public class ListerLesSeances {

    private final SessionCourante session;
    private final HistoriqueDesSeances historique;
    private final ForfaitsDeSeance forfaits;

    public ListerLesSeances(SessionCourante session, HistoriqueDesSeances historique, ForfaitsDeSeance forfaits) {
        this.session = session;
        this.historique = historique;
        this.forfaits = forfaits;
    }

    public PagedResult<LigneDeLHistorique> executer(Demande demande) {
        var centre = session.centre(demande.centre());
        var recherche = new RechercheDeSeances(demande.du(), demande.au(), RechercheDeSeances.lireLesStatuts(demande.statuts()),
                demande.texte(), RechercheDeSeances.OrdreDeTri.lire(demande.tri()), !"asc".equalsIgnoreCase(demande.sens()),
                demande.deverrouillee());
        var page = historique.rechercher(centre, recherche, new Pagination(demande.page(), demande.taille()));
        List<LigneDeLHistorique> lignes = page.items().stream()
                .map(l -> new LigneDeLHistorique(l, forfaits.courant(centre, l.id(), l.patient(), l.jour())))
                .toList();
        return PagedResult.of(lignes, page.total(), page.page(), page.size());
    }

    /**
     * @param centre   centre demandé (nul : celui de la session)
     * @param statuts  statuts séparés par des virgules (texte du client)
     * @param tri      colonne de tri (texte du client)
     * @param sens     {@code asc}, tout autre texte ou absent : décroissant
     */
    public record Demande(UUID centre, LocalDate du, LocalDate au, String statuts, String texte, String tri, String sens,
                          Boolean deverrouillee, int page, int taille) {
    }

    public record LigneDeLHistorique(HistoriqueDesSeances.LigneDeSeance ligne, Optional<ForfaitsDeSeance.ForfaitCourant> forfait) {
    }
}
