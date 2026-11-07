package com.hemodialyse.backend.characterization.seance.se2;

import com.hemodialyse.backend.characterization.seance.se1.Se1CharacterizationSupport;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice SE2 (validation par l'infirmier {@code POST /api/v1/seances/{id}/valider}, signature du médecin
 * {@code POST /api/v1/seances/{id}/signer-medecin}, déverrouillage pour régularisation
 * {@code POST /api/v1/seances/{id}/deverrouiller-regularisation}), au-dessus du harnais SE1 (patients, séances posées en base,
 * profils, diffusions, audit, état SQL de la table réelle {@code seances}).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}), état SQL de {@code seances} et des tables de consommations du Stock, diffusions temps réel, audit. Aucun mock du
 * code caractérisé, aucun code de production modifié (aucun seam).
 * <p>
 * Dates : la validation compare le jour de la séance au jour de la JVM ({@code LocalDate.now()}), le déverrouillage aussi, alors que la
 * création sans date prend le jour du centre (KB-SEA-01) : les séances relatives sont posées au jour du centre {@code <J>} (garde de
 * minuit sur les deux fuseaux, héritée de SE1). Dates fixes {@code <D+n>} pour les séances sans enjeu d'horloge.
 */
public abstract class Se2CharacterizationSupport extends Se1CharacterizationSupport {

    @Override
    protected String tranche() {
        return "se2";
    }

    @Override
    protected void nettoyerSe1() {
        super.nettoyerSe1();
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM consommations_de_seance_imputations WHERE seance_id IN (SELECT seance_id FROM consommations_de_seance "
                    + "WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM consommations_de_seance WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM sorties_pour_seances_lignes WHERE sortie_id IN (SELECT id FROM sorties_pour_seances WHERE center_id = ?)",
                    centre);
            jdbc.update("DELETE FROM sorties_pour_seances WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM deplacement_temporaire WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle IN ('SEQ_SPS', 'SEQ_BS')", centre);
        }
    }

    // ------------------------------------------------------------------ chemins et corps

    protected String validerApi(UUID seance) {
        return SEANCES_API + "/" + seance + "/valider";
    }

    protected String signerApi(UUID seance) {
        return SEANCES_API + "/" + seance + "/signer-medecin";
    }

    protected String deverrouillerApi(UUID seance, UUID centre) {
        return SEANCES_API + "/" + seance + "/deverrouiller-regularisation" + (centre == null ? "" : "?centerId=" + centre);
    }

    protected Resp valider(UUID seance, RequestPostProcessor who) throws Exception {
        return doPost(validerApi(seance), corps("centerId", CENTRE_A, "userId", "inf-01"), who);
    }

    protected Resp signer(UUID seance, RequestPostProcessor who) throws Exception {
        return doPost(signerApi(seance), corps("centerId", CENTRE_A, "userId", "med-01"), who);
    }

    protected Resp deverrouiller(UUID seance, RequestPostProcessor who) throws Exception {
        return doPost(deverrouillerApi(seance, CENTRE_A), "", who);
    }

    protected static Map<String, Object> ligne(UUID article, Object quantite) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("articleId", article == null ? null : article.toString());
        m.put("quantite", quantite);
        return m;
    }

    @SafeVarargs
    protected final String validationAvecConsommations(Map<String, Object>... lignes) {
        return corps("centerId", CENTRE_A, "userId", "inf-01", "consommations", List.of(lignes));
    }

    // ------------------------------------------------------------------ fixtures

    /** Séance posée en base, éventuellement déjà signée ou déverrouillée, pour tester les statuts et les reliquats de colonnes. */
    protected UUID seanceValidee(UUID centre, UUID patient, String alias, LocalDate jour, String statut) {
        UUID id = seanceEnBase(centre, patient, alias, jour, statut);
        if (!"CREE".equals(statut)) {
            jdbc.update("UPDATE seances SET validated_at = ?, signed_infirmier_at = ?, signed_infirmier_by = 'inf-initiale' WHERE id = ?",
                    CREATION_FIXE, CREATION_FIXE, id);
        }
        if ("SIGNEE".equals(statut)) {
            jdbc.update("UPDATE seances SET signed_medecin_at = ?, signed_medecin_by = 'med-initial' WHERE id = ?", CREATION_FIXE, id);
        }
        return id;
    }

    protected void deverrouillerEnBase(UUID seance) {
        jdbc.update("UPDATE seances SET regularisation_deverrouillee_at = ?, regularisation_deverrouillee_by = 'admin-initial' WHERE id = ?",
                CREATION_FIXE, seance);
    }

    /** Place habituelle du patient (salle, créneau, générateur) lue à la validation. */
    protected void placerLePatient(UUID patient, UUID salle, UUID creneau, UUID generateur) {
        jdbc.update("UPDATE patients SET salle_id = ?, position_id = ?, generateur_id = ? WHERE id = ?", salle, creneau, generateur, patient);
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom)).ifPresent(org.springframework.cache.Cache::clear));
    }

    protected UUID articleSe2(UUID centre, String alias, boolean actif, String stock) {
        UUID id = idStable("article", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot, active, "
                        + "type_traitement_anemie, created_at, dci, code_barres, peremption_obligatoire, produit_dangereux, dechet_dasri) "
                        + "VALUES (?, ?, ?, ?, 'piece', ?, 0, 5, false, ?, NULL, CURRENT_TIMESTAMP, NULL, NULL, false, false, false)",
                id, centre, "SE2-" + alias, "Article " + alias, new BigDecimal(stock), actif);
        return id;
    }

    protected UUID lotSe2(UUID centre, UUID article, String alias, String quantite) {
        UUID id = idStable("lot", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        java.time.OffsetDateTime avant = CREATION_FIXE;
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, DATE '2099-12-31', ?, ?, 5, ?)", id, centre, article, alias,
                new BigDecimal(quantite), new BigDecimal(quantite), avant);
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, 5, 'test', ?, ?)", UUID.randomUUID(), centre, article, id,
                new BigDecimal(quantite), avant, avant.plusHours(1));
        return id;
    }

    // ------------------------------------------------------------------ état SQL

    /** Séances des deux centres, avec les colonnes de validation, de signature, de déverrouillage et de place. */
    protected List<Map<String, Object>> etatSe2() {
        return lignes("SELECT s.center_id, s.patient_id, s.date_seance, s.statut, s.signed_infirmier_by, s.signed_medecin_by, "
                + "s.regularisation_deverrouillee_by, s.facture_id, s.salle_id, s.creneau_id, s.generateur_id, "
                + "(CASE WHEN s.validated_at IS NULL THEN 'null' WHEN s.validated_at = TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00:00' "
                + "THEN 'pose par le harnais' ELSE 'renseigne' END) AS validation, "
                + "(CASE WHEN s.signed_infirmier_at IS NULL THEN 'null' WHEN s.signed_infirmier_at = TIMESTAMP WITH TIME ZONE "
                + "'2026-01-01 10:00:00+00:00' THEN 'pose par le harnais' ELSE 'renseigne' END) AS signature_infirmier, "
                + "(CASE WHEN s.signed_medecin_at IS NULL THEN 'null' WHEN s.signed_medecin_at = TIMESTAMP WITH TIME ZONE "
                + "'2026-01-01 10:00:00+00:00' THEN 'pose par le harnais' ELSE 'renseigne' END) AS signature_medecin, "
                + "(CASE WHEN s.regularisation_deverrouillee_at IS NULL THEN 'null' WHEN s.regularisation_deverrouillee_at = TIMESTAMP "
                + "WITH TIME ZONE '2026-01-01 10:00:00+00:00' THEN 'pose par le harnais' ELSE 'renseigne' END) AS deverrouillage "
                + "FROM seances s WHERE s.center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') "
                + "ORDER BY s.center_id, s.patient_id, s.date_seance, s.statut, s.id");
    }

    /** Statut et colonnes d'une seule séance, forme compacte pour les matrices. */
    protected String etatCourt(UUID seance) {
        List<Map<String, Object>> l = lignes("SELECT statut, signed_infirmier_by, signed_medecin_by, regularisation_deverrouillee_by, "
                + "(CASE WHEN validated_at IS NULL THEN 'null' ELSE 'renseigne' END) AS validation, "
                + "(CASE WHEN signed_medecin_at IS NULL THEN 'null' ELSE 'renseigne' END) AS signature_medecin FROM seances WHERE id = ?", seance);
        return l.isEmpty() ? "absente" : l.getFirst().toString();
    }

    /** Consommations imputées par le Stock : (séance, article, lot, quantité, alerte). */
    protected List<Map<String, Object>> consommationsImputees() {
        return lignes("SELECT a.code AS article, c.sortie_id IS NOT NULL AS sur_une_piece, i.lot_id, i.quantite, i.alerte "
                + "FROM consommations_de_seance_imputations i "
                + "JOIN consommations_de_seance c ON c.seance_id = i.seance_id "
                + "JOIN articles a ON a.id = i.article_id "
                + "WHERE c.center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY a.code, i.quantite, i.id");
    }

    /** Horodatage brut de validation (pour dire si une seconde validation l'a modifié). */
    protected String validatedAtBrut(UUID seance) {
        return jdbc.queryForObject("SELECT CAST(validated_at AS VARCHAR(80)) FROM seances WHERE id = ?", String.class, seance);
    }

    protected String deverrouillageBrut(UUID seance) {
        return jdbc.queryForObject("SELECT CAST(regularisation_deverrouillee_at AS VARCHAR(80)) FROM seances WHERE id = ?", String.class, seance);
    }

    protected int mouvementsDeStock() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B);
    }

    protected List<Map<String, Object>> stocksDesArticles() {
        return lignes("SELECT code, stock_quantity FROM articles WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY code");
    }

    /** Une réponse de refus ou de succès sur une ligne, plus le statut SQL de la séance : la forme des matrices. */
    protected String issue(Resp r, UUID seance) {
        return refus(r) + " ; " + etatCourt(seance);
    }

    protected static List<String> noms(Map<String, ?> m) {
        return new ArrayList<>(m.keySet());
    }
}
