package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.groupes.GestionDesGroupesDArticles;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.interfaces.http.GroupesDArticlesJson.GroupeReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.GroupesDArticlesJson.GroupeRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.GroupesDArticlesJson.PageReponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP des groupes d'articles : {@code /api/v1/stock/groupes-articles}, mêmes routes, rôle ({@code ADMIN}) et
 * JSON que le legacy ({@code GroupeArticleRestController}, slice S2). Centre de la session, un autre centre demandé est
 * refusé (403). Pagination : mêmes refus que le legacy (page négative, taille nulle : 400), taille non bornée (KB-STK-11).
 */
@RestController
@RequestMapping("/api/v1/stock/groupes-articles")
@PreAuthorize("hasRole('ADMIN')")
public class GroupesDArticlesControleur {

    private final SessionCourante session;
    private final GestionDesGroupesDArticles groupes;

    public GroupesDArticlesControleur(SessionCourante session, GestionDesGroupesDArticles groupes) {
        this.session = session;
        this.groupes = groupes;
    }

    @GetMapping
    public PageReponse lister(@RequestParam(required = false) UUID centerId,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        if (page < 0) {
            throw new ValeurInvalide("BAD_REQUEST", "Page index must not be less than zero");
        }
        if (size < 1) {
            throw new ValeurInvalide("BAD_REQUEST", "Page size must not be less than one");
        }
        return PageReponse.depuis(groupes.lister(centre, page, size));
    }

    @GetMapping("/{id}")
    public GroupeReponse consulter(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return GroupeReponse.depuis(groupes.consulter(session.centre(centerId), id));
    }

    @PostMapping
    public ResponseEntity<GroupeReponse> creer(@RequestParam(required = false) UUID centerId,
                                               @Valid @RequestBody GroupeRequete requete) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupeReponse.depuis(
                groupes.creer(centre, requete.nom(), requete.description(), requete.articleIds())));
    }

    @PutMapping("/{id}")
    public GroupeReponse modifier(@PathVariable UUID id, @RequestParam(required = false) UUID centerId,
                                  @Valid @RequestBody GroupeRequete requete) {
        CentreId centre = session.centre(centerId);
        return GroupeReponse.depuis(groupes.modifier(centre, id, requete.nom(), requete.description(), requete.articleIds()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        groupes.supprimer(session.centre(centerId), id);
        return ResponseEntity.noContent().build();
    }
}

