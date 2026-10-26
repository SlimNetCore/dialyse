package com.hemodialyse.backend.characterization.stock.referentiel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Filet de caractérisation S8 — dernières lectures d'articles faites par la Séance via {@code ArticleRepositoryPort} (module
 * {@code bc-article}) : raccourcis de consommables ({@code SeanceRaccourciDomainService.replace}, {@code PUT
 * /api/v1/seances/raccourcis-consommables}) et ajout d'un consommable à une séance validée ({@code
 * SeanceDomainService.addConsommableSeance}, {@code POST /api/v1/seances/{id}/consommables}) : article inconnu, d'un autre centre,
 * inactif, doublons et plafond. Les lots ne sont plus lus par la Séance (langage publié {@code ConsommationsDeSeance}, filet S6b).
 * Point d'observation : HTTP, SQL de {@code seance_raccourcis_articles}.
 */
class ArticlesDeLaSeanceCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String RACCOURCIS = "/api/v1/seances/raccourcis-consommables";
    private static final UUID PATIENT = UUID.fromString("99997500-0000-0000-0004-0000000000f1");

    @Override
    protected String theme() {
        return "articles-de-la-seance";
    }

    @BeforeEach
    @AfterEach
    void vider() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM seance_raccourcis_articles WHERE center_id = ?", c);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ? AND id = ?", c, PATIENT);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
        }
    }

    private UUID art(UUID centre, String alias, boolean actif) {
        return article(centre, alias, alias, "Article " + alias, null, null, actif, null, "10", "5");
    }

    private UUID seance(String alias, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)", id,
                PATIENT, CENTRE_A, LocalDate.now().minusDays(2), statut, OffsetDateTime.now(ZoneOffset.UTC));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    private static String ids(List<UUID> articles) {
        StringBuilder sb = new StringBuilder("{'articleIds':[");
        for (int i = 0; i < articles.size(); i++) {
            sb.append(i > 0 ? "," : "").append("'").append(articles.get(i)).append("'");
        }
        return json(sb.append("]}").toString());
    }

    private Resp remplacer(List<UUID> articles) throws Exception {
        return send(put(java.net.URI.create(RACCOURCIS + "?centerId=" + CENTRE_A)).contentType(MediaType.APPLICATION_JSON)
                .content(ids(articles)).with(adminA()));
    }

    private Resp ajouter(UUID seance, UUID article, String quantite) throws Exception {
        return send(post(java.net.URI.create("/api/v1/seances/" + seance + "/consommables")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{'centerId':'" + CENTRE_A + "','articleId':'" + article + "','quantite':" + quantite + "}")).with(adminA()));
    }

    @Test
    void les_raccourcis_ne_retiennent_que_des_articles_actifs_du_centre_sans_doublon_dans_la_limite_de_douze() throws Exception {
        UUID a1 = art(CENTRE_A, "ART_1", true);
        UUID a2 = art(CENTRE_A, "ART_2", true);
        UUID inactif = art(CENTRE_A, "ART_INACTIF", false);
        UUID autreCentre = art(CENTRE_B, "ART_AUTRE_CENTRE", true);
        UUID inconnu = UUID.fromString("99997500-0000-0000-0001-999999999999");
        aliasNommes.put(inconnu.toString(), "<ART_INCONNU>");
        golden("raccourcis_nominal_ordre_conserve", remplacer(List.of(a2, a1)));
        goldenSql("raccourcis_nominal_sql", "SELECT a.code, r.ordre FROM seance_raccourcis_articles r JOIN articles a ON a.id = r.article_id "
                + "WHERE r.center_id = '" + CENTRE_A + "' ORDER BY r.ordre");
        golden("raccourcis_article_inconnu", remplacer(List.of(a1, inconnu)));
        golden("raccourcis_article_inactif_nouveau_code", remplacer(List.of(a1, inactif)));
        golden("raccourcis_article_d_un_autre_centre", remplacer(List.of(a1, autreCentre)));
        golden("raccourcis_doublon", remplacer(List.of(a1, a1)));
        golden("raccourcis_liste_vide", remplacer(List.of()));
        List<UUID> douze = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            douze.add(art(CENTRE_A, "ART_L" + i, true));
        }
        golden("raccourcis_douze_acceptes", remplacer(douze));
        List<UUID> treize = new ArrayList<>(douze);
        treize.add(a1);
        golden("raccourcis_treize_refuses", remplacer(treize));
        golden("raccourcis_lecture_apres_refus", send(get(java.net.URI.create(RACCOURCIS + "?centerId=" + CENTRE_A)).with(adminA())));
    }

    @Test
    void l_ajout_d_un_consommable_exige_une_seance_validee_et_un_article_actif_du_centre() throws Exception {
        UUID actif = art(CENTRE_A, "ART_1", true);
        UUID inactif = art(CENTRE_A, "ART_INACTIF", false);
        UUID autreCentre = art(CENTRE_B, "ART_AUTRE_CENTRE", true);
        UUID inconnu = UUID.fromString("99997500-0000-0000-0001-999999999999");
        aliasNommes.put(inconnu.toString(), "<ART_INCONNU>");
        UUID creee = seance("SEANCE_CREE", "CREE");
        UUID validee = seance("SEANCE_VALIDEE", "VALIDEE");
        UUID facturee = seance("SEANCE_FACTUREE", "FACTUREE");
        UUID absente = seance("SEANCE_ABSENTE", "ABSENT");
        UUID inconnue = UUID.fromString("99997500-0000-0000-0005-999999999999");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        golden("consommable_seance_inconnue", ajouter(inconnue, actif, "1"));
        golden("consommable_seance_non_validee", ajouter(creee, actif, "1"));
        golden("consommable_seance_facturee", ajouter(facturee, actif, "1"));
        golden("consommable_seance_absente", ajouter(absente, actif, "1"));
        golden("consommable_article_inconnu", ajouter(validee, inconnu, "1"));
        golden("consommable_article_inactif_nouveau_code", ajouter(validee, inactif, "1"));
        golden("consommable_article_d_un_autre_centre", ajouter(validee, autreCentre, "1"));
        golden("consommable_quantite_nulle", ajouter(validee, actif, "0"));
    }

    @Test
    void la_validation_d_une_seance_refuse_un_article_inactif_ou_inconnu_en_400() throws Exception {
        UUID inactif = art(CENTRE_A, "ART_INACTIF", false);
        UUID inconnu = UUID.fromString("99997500-0000-0000-0001-999999999999");
        aliasNommes.put(inconnu.toString(), "<ART_INCONNU>");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                + "type_patient, created_at, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, "
                + "jour_dimanche) VALUES (?, ?, 'ZT-S8-P', 'Patient', 'S8', 'M', DATE '2020-01-01', 'ASS-ZT-S8', 'NON_VACANCIER', "
                + "CURRENT_TIMESTAMP, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE)", PATIENT, CENTRE_A);
        UUID creee = seance("SEANCE_CREE", "CREE");
        // KB-STK-57 corrigé : un article inactif à la validation répond 400 comme à l'ajout d'un consommable (c'était 422)
        golden("validation_article_inactif_nouveau_code", valider(creee, inactif));
        golden("validation_article_inconnu", valider(creee, inconnu));
    }

    private Resp valider(UUID seance, UUID article) throws Exception {
        return send(post(java.net.URI.create("/api/v1/seances/" + seance + "/valider")).contentType(MediaType.APPLICATION_JSON)
                .content(json("{'centerId':'" + CENTRE_A + "','userId':'zt-s8','consommations':[{'articleId':'" + article + "','quantite':1}]}"))
                .with(adminA()));
    }
}
