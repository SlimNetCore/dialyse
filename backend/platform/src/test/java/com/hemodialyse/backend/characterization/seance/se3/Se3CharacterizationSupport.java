package com.hemodialyse.backend.characterization.seance.se3;

import com.hemodialyse.backend.characterization.seance.se2.Se2CharacterizationSupport;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice SE3 (consommables déclarés sur une séance : {@code POST /api/v1/seances/{id}/consommables},
 * {@code PUT /api/v1/seances/{id}/consommables/{articleId}}, {@code DELETE /api/v1/seances/{id}/consommables/{articleId}} ; raccourcis de
 * consommables du poste infirmier : {@code GET|PUT /api/v1/seances/raccourcis-consommables}), au-dessus du harnais SE2 (séances, articles,
 * lots, consommations imputées par le Stock).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}), état SQL des consommations (tables du Stock), de la table {@code seance_raccourcis_articles}, diffusions temps réel,
 * audit. Aucun mock du code caractérisé, aucun seam. Les séances relatives sont posées au jour du centre {@code <J>} ; aucun test ne lit
 * l'heure.
 */
public abstract class Se3CharacterizationSupport extends Se2CharacterizationSupport {

    @Override
    protected String tranche() {
        return "se3";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM seance_raccourcis_articles WHERE center_id = ?", centre);
        }
        viderLesCaches();
        super.nettoyerSe1();
    }

    protected void viderLesCaches() {
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom)).ifPresent(org.springframework.cache.Cache::clear));
    }

    // ------------------------------------------------------------------ chemins et corps

    protected String consommablesApi(UUID seance) {
        return SEANCES_API + "/" + seance + "/consommables";
    }

    protected String consommableApi(UUID seance, UUID article, UUID centre) {
        return consommablesApi(seance) + "/" + article + (centre == null ? "" : "?centerId=" + centre);
    }

    protected String raccourcisApi(UUID centre) {
        return SEANCES_API + "/raccourcis-consommables" + (centre == null ? "" : "?centerId=" + centre);
    }

    protected String ajout(UUID article, Object quantite) {
        return corps("centerId", CENTRE_A, "articleId", article, "quantite", quantite);
    }

    protected String modification(Object quantite) {
        return corps("centerId", CENTRE_A, "userId", "inf-01", "quantite", quantite);
    }

    protected Resp ajouter(UUID seance, UUID article, Object quantite, RequestPostProcessor who) throws Exception {
        return doPost(consommablesApi(seance), ajout(article, quantite), who);
    }

    protected Resp modifier(UUID seance, UUID article, Object quantite, RequestPostProcessor who) throws Exception {
        return doPut(consommableApi(seance, article, null), modification(quantite), who);
    }

    protected Resp retirer(UUID seance, UUID article, RequestPostProcessor who) throws Exception {
        return doDelete(consommableApi(seance, article, CENTRE_A), who);
    }

    protected Resp remplacerLesRaccourcis(UUID centre, List<?> articles, RequestPostProcessor who) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("articleIds", articles == null ? null : articles.stream().map(a -> a == null ? null : a.toString()).toList());
        return doPut(raccourcisApi(centre), mapper.writeValueAsString(m), who);
    }

    protected Resp lireLesRaccourcis(UUID centre, RequestPostProcessor who) throws Exception {
        return doGet(raccourcisApi(centre), who);
    }

    // ------------------------------------------------------------------ état SQL

    /** Raccourcis enregistrés des deux centres : centre, article (code), rang. */
    protected List<Map<String, Object>> etatDesRaccourcis() {
        return lignes("SELECT r.center_id, a.code AS article, r.ordre FROM seance_raccourcis_articles r "
                + "JOIN articles a ON a.id = r.article_id WHERE r.center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') "
                + "ORDER BY r.center_id, r.ordre");
    }

    /** Une réponse de refus ou de succès, plus les consommations imputées : la forme des matrices de SE3. */
    protected String issueEtConsommations(Resp r) {
        return refus(r) + " ; " + consommationsImputees();
    }
}
