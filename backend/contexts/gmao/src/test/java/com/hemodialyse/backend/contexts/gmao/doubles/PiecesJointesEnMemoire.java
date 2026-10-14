package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.LecturePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PageDePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PieceJointeVue;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Double in-memory du repository et du read model des pièces jointes. Fidèle au contrat partagé ({@code PieceJointeContrat}) :
 * les mêmes tests s'exécutent contre l'adapter JPA. Les octets sont copiés à l'écriture et à la lecture (comme une base) ; la
 * liste est triée de la plus récente à la plus ancienne, ex aequo par identifiant décroissant, et ne contient aucun octet.
 */
public class PiecesJointesEnMemoire implements PieceJointeRepository, LecturePiecesJointes {

    private final Map<PieceJointeId, PieceJointe> pieces = new LinkedHashMap<>();

    @Override
    public void enregistrer(PieceJointe piece) {
        pieces.put(piece.id(), copie(piece));
    }

    @Override
    public Optional<PieceJointe> trouverParId(PieceJointeId id) {
        return Optional.ofNullable(pieces.get(id)).map(PiecesJointesEnMemoire::copie);
    }

    @Override
    public Optional<PieceJointe> trouverPourRetrait(PieceJointeId id) {
        return Optional.ofNullable(pieces.get(id)).map(p -> PieceJointe.reconstituer(p.id(), p.interventionId(), p.centreId(), p.type(),
                p.nom(), p.contentType(), p.taille(), new byte[0], p.ajoutePar(), p.ajouteLe()));
    }

    @Override
    public long compter(InterventionId intervention) {
        return pieces.values().stream().filter(p -> p.interventionId().equals(intervention)).count();
    }

    @Override
    public void supprimer(PieceJointeId id) {
        pieces.remove(id);
    }

    @Override
    public PageDePiecesJointes deLIntervention(InterventionId intervention, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("Page index must not be less than zero");
        if (size < 1) throw new IllegalArgumentException("Page size must not be less than one");
        List<PieceJointeVue> toutes = pieces.values().stream()
                .filter(p -> p.interventionId().equals(intervention))
                .sorted(Comparator.comparing(PieceJointe::ajouteLe).thenComparing(p -> p.id().valeur()).reversed())
                .map(p -> new PieceJointeVue(p.id().valeur(), p.interventionId().valeur(), p.type(), p.nom(), p.contentType(),
                        p.taille(), p.ajouteLe()))
                .toList();
        long debut = (long) page * size;
        List<PieceJointeVue> items = debut >= toutes.size() ? List.of()
                : toutes.subList((int) debut, (int) Math.min(toutes.size(), debut + size));
        return new PageDePiecesJointes(items, toutes.size(), page, size);
    }

    private static PieceJointe copie(PieceJointe p) {
        return PieceJointe.reconstituer(p.id(), p.interventionId(), p.centreId(), p.type(), p.nom(), p.contentType(), p.taille(),
                p.octets().clone(), p.ajoutePar(), p.ajouteLe());
    }
}
