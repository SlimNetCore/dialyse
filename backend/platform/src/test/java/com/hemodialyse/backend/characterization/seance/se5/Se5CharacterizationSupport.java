package com.hemodialyse.backend.characterization.seance.se5;

import com.hemodialyse.backend.characterization.seance.se4.Se4CharacterizationSupport;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice SE5 (forfait surchargé d'une séance : {@code PUT /api/v1/seances/{id}/forfait}, et lecture du forfait courant dans
 * {@code GET /api/v1/seances/{id}} et {@code GET /api/v1/seances}), au-dessus du harnais SE4 (donc SE3, SE2 et SE1).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}), état SQL des colonnes {@code forfait_override_*} de la table réelle {@code seances}, audit. Aucun mock du code
 * caractérisé, aucun seam. Les séances sont posées à des dates fixes {@code <D+n>} ; aucun test ne lit l'heure (l'instant de la
 * surcharge est masqué, sa variation est observée par comparaison de deux lectures brutes).
 * <p>
 * DONNEES FICTIVES uniquement.
 */
public abstract class Se5CharacterizationSupport extends Se4CharacterizationSupport {

    @Override
    protected String tranche() {
        return "se5";
    }

    // ------------------------------------------------------------------ chemins et corps

    protected String forfaitApi(UUID seance) {
        return SEANCES_API + "/" + seance + "/forfait";
    }

    protected String surcharge(UUID forfait, String utilisateur) {
        return corps("centerId", CENTRE_A, "forfaitId", forfait, "userId", utilisateur);
    }

    protected Resp surcharger(UUID seance, UUID forfait, String utilisateur, RequestPostProcessor who) throws Exception {
        return doPut(forfaitApi(seance), surcharge(forfait, utilisateur), who);
    }

    // ------------------------------------------------------------------ fixtures

    /** Forfait fictif du référentiel du centre ; {@code prix} nul = prix absent (la colonne l'accepte). */
    protected UUID forfaitSe5(UUID centre, String alias, String prix) {
        UUID id = idStable("forfait5", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", id, centre, "SE5-" + alias,
                "Forfait " + alias, prix == null ? null : new BigDecimal(prix));
        return id;
    }

    /** Prise en charge validée (ou autre statut) avec ses deux forfaits et ses deux périodes ; {@code creation} départage les plus récentes. */
    protected void pecSe5(UUID centre, UUID patient, String statut, UUID forfaitDemande, String debutDemande, String finDemande,
                          UUID forfaitEffectif, String debutEffectif, String finEffectif, OffsetDateTime creation) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, date_debut_effectif, "
                        + "date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre, patient, date5(debutDemande), date5(finDemande), date5(debutEffectif), date5(finEffectif),
                forfaitDemande, forfaitEffectif, statut, creation);
    }

    private static java.sql.Date date5(String d) {
        return d == null ? null : java.sql.Date.valueOf(d);
    }

    /** Supprime les prises en charge d'un patient (pour reconstruire une situation dans un même scénario). */
    protected void sansPec(UUID patient) {
        jdbc.update("DELETE FROM prise_en_charge WHERE patient_id = ?", patient);
    }

    // ------------------------------------------------------------------ état SQL

    /** Colonnes de surcharge d'une séance ; l'instant est masqué (« null » ou « renseigne »). */
    protected List<Map<String, Object>> surchargeEnBase(UUID seance) {
        return lignes("SELECT statut, forfait_override_id, forfait_override_code, forfait_override_nom, forfait_override_prix, "
                + "forfait_override_updated_by, (CASE WHEN forfait_override_updated_at IS NULL THEN 'null' ELSE 'renseigne' END) AS mise_a_jour "
                + "FROM seances WHERE id = ?", seance);
    }

    /** Empreinte brute des colonnes de surcharge : dit si une requête les a réécrites. */
    protected String empreinteDeLaSurcharge(UUID seance) {
        return jdbc.queryForList("SELECT forfait_override_id, forfait_override_code, forfait_override_nom, forfait_override_prix, "
                + "CAST(forfait_override_updated_at AS VARCHAR(80)) AS instant, forfait_override_updated_by FROM seances WHERE id = ?", seance)
                .toString();
    }

    /** Instant brut de la dernière surcharge (pour dire si une nouvelle surcharge l'a modifié). */
    protected String instantDeLaSurcharge(UUID seance) {
        return jdbc.queryForObject("SELECT CAST(forfait_override_updated_at AS VARCHAR(80)) FROM seances WHERE id = ?", String.class, seance);
    }

    /** Une réponse de refus ou de succès sur une ligne, plus l'état de la surcharge : la forme des matrices. */
    protected String issueEtSurcharge(Resp r, UUID seance) {
        return refus(r) + " ; " + surchargeEnBase(seance);
    }
}
