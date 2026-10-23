package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesFournisseursEtEmplacements;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReferentielJson.ArticleReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReferentielJson.EmplacementReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReferentielJson.EmplacementRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReferentielJson.FournisseurReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReferentielJson.FournisseurRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReferentielJson.PageReponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP du référentiel du Stock : {@code /api/v1/stock/referentiel}, mêmes routes, rôles et JSON que le legacy
 * ({@code StockReferentialRestController}, slice S1). Toutes les routes confrontent le centre demandé à celui de la session (403
 * sinon, KB-STK-04 corrigé) et prennent celui de la session quand il est absent (KB-STK-05 corrigé).
 */
@RestController
@RequestMapping("/api/v1/stock/referentiel")
public class ReferentielStockControleur {

    private final SessionCourante session;
    private final ReferentielDesArticles articles;
    private final ReferentielDesFournisseursEtEmplacements referentiel;

    public ReferentielStockControleur(SessionCourante session, ReferentielDesArticles articles,
                                      ReferentielDesFournisseursEtEmplacements referentiel) {
        this.session = session;
        this.articles = articles;
        this.referentiel = referentiel;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/articles")
    public ResponseEntity<ArticleReponse> referencer(@RequestBody @Valid FicheArticleRequete requete) {
        CentreId centre = session.centre(requete.centerId());
        return ResponseEntity.ok(ArticleReponse.depuis(articles.referencer(centre, requete.fiche())));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PutMapping("/articles/{articleId}")
    public ResponseEntity<ArticleReponse> modifier(@PathVariable UUID articleId, @RequestBody @Valid FicheArticleRequete requete) {
        CentreId centre = session.centre(requete.centerId());
        return ResponseEntity.ok(ArticleReponse.depuis(articles.modifierFiche(centre, articleId, requete.fiche())));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','MEDECIN')")
    @GetMapping("/articles/{articleId}")
    public ResponseEntity<ArticleReponse> consulter(@PathVariable UUID articleId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(ArticleReponse.depuis(articles.consulter(session.centre(centerId), articleId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PatchMapping("/articles/{articleId}/active")
    public ResponseEntity<ArticleReponse> changerActivite(@PathVariable UUID articleId,
                                                          @RequestParam(required = false) UUID centerId,
                                                          @RequestParam(required = false) Boolean active) {
        if (active == null) {
            throw new ValeurInvalide("ARTICLE_ACTIVITE_REQUISE", "Le paramètre active (true ou false) est obligatoire");
        }
        return ResponseEntity.ok(ArticleReponse.depuis(articles.changerActivite(session.centre(centerId), articleId, active)));
    }

    /**
     * Fiches paginées (écran « Articles ») : recherche sur code, libellé, DCI ou code-barres.
     */
    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @GetMapping("/articles/page")
    public ResponseEntity<PageReponse> rechercher(@RequestParam(required = false) UUID centerId,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Boolean active,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageReponse.depuis(articles.rechercher(session.centre(centerId), q, active, page, size)));
    }

    /**
     * Articles du centre ; {@code typeTraitementAnemie} filtre EPO / FER_INJECTABLE (listes de prescription).
     */
    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','MEDECIN')")
    @GetMapping("/articles")
    public ResponseEntity<List<ArticleReponse>> lister(@RequestParam(required = false) UUID centerId,
                                                       @RequestParam(required = false) String typeTraitementAnemie) {
        CentreId centre = session.centre(centerId);
        TypeTraitementAnemie type = FicheArticleRequete.typeDeTraitement(typeTraitementAnemie);
        return ResponseEntity.ok(ArticleReponse.depuis(type == null ? articles.lister(centre)
                : articles.listerParTypeDeTraitement(centre, type)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/fournisseurs")
    public ResponseEntity<FournisseurReponse> creerUnFournisseur(@RequestBody @Valid FournisseurRequete requete) {
        CentreId centre = session.centre(requete.centerId());
        return ResponseEntity.ok(FournisseurReponse.depuis(referentiel.creerUnFournisseur(centre, requete.code(),
                requete.raisonSociale(), requete.contact(), requete.telephone(), requete.email())));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/fournisseurs")
    public ResponseEntity<List<FournisseurReponse>> listerLesFournisseurs(@RequestParam(required = false) UUID centerId,
                                                                          @RequestParam(required = false) String q) {
        return ResponseEntity.ok(referentiel.chercherDesFournisseurs(session.centre(centerId), q).stream()
                .map(FournisseurReponse::depuis).toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/emplacements")
    public ResponseEntity<EmplacementReponse> creerUnEmplacement(@RequestBody @Valid EmplacementRequete requete) {
        CentreId centre = session.centre(requete.centerId());
        return ResponseEntity.ok(EmplacementReponse.depuis(referentiel.creerUnEmplacement(centre, requete.code(),
                requete.libelle())));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/emplacements")
    public ResponseEntity<List<EmplacementReponse>> listerLesEmplacements(@RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(referentiel.listerLesEmplacements(session.centre(centerId)).stream()
                .map(EmplacementReponse::depuis).toList());
    }
}

