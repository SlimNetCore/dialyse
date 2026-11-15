package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.dossiermedical.application.CatalogueDesArticles;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DossierEtTraitementDuCahier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link DossierEtTraitementDuCahier} (DOC5a, ADR 0193) : le dossier médical de base et la
 * prescription en vigueur le jour d'une séance, lus dans les tables du Dossier médical. Reprend MOT POUR MOT les deux jointures de l'ancien
 * gabarit du cahier : le dossier du patient du centre ; la prescription la plus récente datée au plus tard du jour (date puis création
 * décroissantes ; l'identifiant décroissant ne fait que rendre déterministe une égalité que l'ancienne requête laissait au SGBD). Les
 * articles viennent du catalogue du Stock (libellé, à défaut code ; inconnu du centre : aucun, reproduit).
 */
public class DossierEtTraitementPourLeCahier implements DossierEtTraitementDuCahier {

    private final JdbcTemplate jdbc;
    private final CatalogueDesArticles articles;

    public DossierEtTraitementPourLeCahier(JdbcTemplate jdbc, CatalogueDesArticles articles) {
        this.jdbc = jdbc;
        this.articles = articles;
    }

    @Override
    public Optional<DossierDeBase> dossier(UUID centre, UUID patient) {
        return jdbc.query("SELECT nephropathie_initiale, date_mise_en_dialyse, hepatite_b_statut, hepatite_c_statut, observation_globale, "
                        + "conclusion_medicale FROM dossier_medical_patient WHERE patient_id = ? AND center_id = ?",
                (rs, n) -> new DossierDeBase(rs.getString("nephropathie_initiale"),
                        rs.getDate("date_mise_en_dialyse") == null ? null : rs.getDate("date_mise_en_dialyse").toLocalDate(),
                        rs.getString("hepatite_b_statut"), rs.getString("hepatite_c_statut"), rs.getString("observation_globale"),
                        rs.getString("conclusion_medicale")), patient, centre).stream().findFirst();
    }

    @Override
    public Optional<TraitementEnVigueur> traitementEnVigueur(UUID centre, UUID patient, LocalDate jour) {
        CentreId centreId = new CentreId(centre);
        return jdbc.query("SELECT poids_sec_cible_kg, epo_article_id, epo_dose_ui, epo_voie, epo_frequence_valeur, epo_frequence_unite, "
                        + "fer_article_id, fer_dose_mg, fer_voie, fer_frequence_valeur, fer_frequence_unite FROM prescriptions_medicales "
                        + "WHERE patient_id = ? AND center_id = ? AND date_prescription <= ? "
                        + "ORDER BY date_prescription DESC, created_at DESC, id DESC FETCH FIRST 1 ROWS ONLY",
                (rs, n) -> new TraitementEnVigueur(rs.getBigDecimal("poids_sec_cible_kg"),
                        new Traitement(libelle(centreId, rs.getObject("epo_article_id", UUID.class)), entier(rs.getObject("epo_dose_ui")),
                                rs.getString("epo_voie"), entier(rs.getObject("epo_frequence_valeur")), rs.getString("epo_frequence_unite")),
                        new Traitement(libelle(centreId, rs.getObject("fer_article_id", UUID.class)), entier(rs.getObject("fer_dose_mg")),
                                rs.getString("fer_voie"), entier(rs.getObject("fer_frequence_valeur")), rs.getString("fer_frequence_unite"))),
                patient, centre, Date.valueOf(jour)).stream().findFirst();
    }

    private String libelle(CentreId centre, UUID article) {
        if (article == null) return null;
        return articles.article(centre, article).map(a -> a.libelle() != null ? a.libelle() : a.code()).orElse(null);
    }

    private static Integer entier(Object valeur) {
        return valeur == null ? null : ((Number) valeur).intValue();
    }
}
