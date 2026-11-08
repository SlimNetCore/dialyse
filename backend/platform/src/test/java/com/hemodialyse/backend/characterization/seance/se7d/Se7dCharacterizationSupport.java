package com.hemodialyse.backend.characterization.seance.se7d;

import com.hemodialyse.backend.characterization.seance.se7b.Se7bCharacterizationSupport;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Harnais de la sous-slice SE7d (lectures de la table {@code seances} par Absences patients, Planification et Dossier médical : les cinq
 * lecteurs SQL de l'allow-list {@code TableDesSeancesTest} hors plateforme), au-dessus de SE7b (donc SE7a à SE1 et Planification).
 * <p>
 * Points d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, filtre d'écart de 2 ms, H2
 * {@code test}) pour {@code GET /api/v1/absences-patients/semaine}, {@code GET /api/v1/absences-patients/{id}/seances-rattrapage},
 * {@code PUT /api/v1/absences-patients/{id}/annulation}, {@code GET /api/v1/planning/semaine}, {@code GET /api/v1/patients/{id}/constantes} ;
 * à défaut (matrice de casse des statuts, que la route ne peut pas isoler), les ports câblés en production, tirés du contexte Spring et
 * appelés sur la vraie base. Aucun mock du code caractérisé, aucun seam, aucun code de production modifié.
 * <p>
 * Dates : FIXES uniquement ({@code <D+n>} = 2026-01-01 + n ; la semaine observée est celle du dimanche 2026-03-01 = {@code <D+59>}).
 * Aucun test ne lit l'heure ni la zone système : les bornes « aujourd'hui » des routes (rattrapage) sont toujours postérieures aux dates
 * fixes posées. DONNÉES FICTIVES uniquement.
 */
public abstract class Se7dCharacterizationSupport extends Se7bCharacterizationSupport {

    protected static final String ABSENCES_API = "/api/v1/absences-patients";
    protected static final String PATIENTS_API = "/api/v1/patients";
    /** Dimanche 2026-03-01 : premier jour de la semaine observée. */
    protected static final int DIMANCHE_OBSERVE = 59;
    private static final String SOCIETE_SE7D = "ZT-SE7D-S";

    @Override
    protected String tranche() {
        return "se7d";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", centre);
        }
        super.nettoyerSe1();
    }

    // ------------------------------------------------------------------ dates

    /** Nomme les huit jours de la semaine observée ({@code <D+59>} dimanche à {@code <D+66>} dimanche suivant) ; rend le dimanche. */
    protected LocalDate semaineObservee() {
        for (int i = 0; i <= 7; i++) fixe(DIMANCHE_OBSERVE + i);
        return fixe(DIMANCHE_OBSERVE);
    }

    /** Jour de la semaine observée : 0 = dimanche, 1 = lundi, ... 6 = samedi. */
    protected LocalDate jourObserve(int rang) {
        semaineObservee();
        return fixe(DIMANCHE_OBSERVE + rang);
    }

    // ------------------------------------------------------------------ fixtures

    /** Ligne {@code absence_patient} complète (valorisée, déclarée), à qualifier : annulable par tout profil d'accès. */
    protected UUID absenceComplete(UUID centre, String alias, UUID patient, LocalDate jour) {
        UUID id = UUID.nameUUIDFromBytes(("se7d-absence-" + alias + "-" + centre).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        Timestamp declaree = Timestamp.valueOf("2026-01-15 08:00:00");
        jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, motif, commentaire, forfait_id, "
                        + "forfait_libelle, prix_ttc, taux_tva, montant_ht, declaree_par, declaree_le, qualifiee_par, qualifiee_le, "
                        + "date_rattrapage, modifiee_par, modifiee_le) VALUES (?, ?, ?, ?, 'AUTOMATIQUE', 'A_QUALIFIER', NULL, NULL, NULL, "
                        + "'Forfait HD', ?, ?, ?, NULL, ?, NULL, NULL, NULL, NULL, NULL)",
                id, centre, patient, Date.valueOf(jour), new BigDecimal("11900.00"), new BigDecimal("19.00"), new BigDecimal("10000.00"),
                declaree);
        return id;
    }

    /** Pose le lien d'exclusion ({@code exclusion_absence_id}) sur une séance existante, tel que la Facturation l'écrit. */
    protected void lienDExclusion(UUID seance, UUID absence) {
        jdbc.update("UPDATE seances SET exclusion_absence_id = ? WHERE id = ?", absence, seance);
    }

    /** Société et centres de test présents dans {@code centers} (certains écrans lisent le référentiel) ; retirés par leur code. */
    protected void referentielDesCentres() {
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-SE7D-%'");
        jdbc.update("DELETE FROM societes WHERE code = ?", SOCIETE_SE7D);
        UUID societe = UUID.nameUUIDFromBytes(SOCIETE_SE7D.getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)", societe,
                SOCIETE_SE7D, "Societe SE7d");
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            Integer deja = jdbc.queryForObject("SELECT COUNT(*) FROM centers WHERE id = ?", Integer.class, centre);
            if (deja == null || deja == 0) {
                jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", centre,
                        "ZT-SE7D-" + (centre.equals(CENTRE_A) ? "A" : "B"), "Centre SE7d", societe);
            }
        }
    }

    protected void retirerLeReferentielDesCentres() {
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-SE7D-%'");
        jdbc.update("DELETE FROM societes WHERE code = ?", SOCIETE_SE7D);
    }

    // ------------------------------------------------------------------ appels

    protected Resp semaineDesAbsences(LocalDate date, RequestPostProcessor who) throws Exception {
        return doGet(ABSENCES_API + "/semaine?date=" + date, who);
    }

    protected Resp seancesDeRattrapage(UUID absence, RequestPostProcessor who) throws Exception {
        return doGet(ABSENCES_API + "/" + absence + "/seances-rattrapage", who);
    }

    protected Resp annulerLAbsence(UUID absence, RequestPostProcessor who) throws Exception {
        return doPut(ABSENCES_API + "/" + absence + "/annulation", "{\"commentaire\":\"Erreur de saisie\"}", who);
    }

    protected Resp semaineDuPlanning(LocalDate date, RequestPostProcessor who) throws Exception {
        return doGet(PLANNING + "/semaine?date=" + date, who);
    }

    protected Resp constantesDuPatient(UUID patient, String parametres, RequestPostProcessor who) throws Exception {
        return doGet(PATIENTS_API + "/" + patient + "/constantes" + (parametres == null ? "" : "?" + parametres), who);
    }

    /** Statut de l'absence en base (rien d'autre). */
    protected String statutDeLAbsence(UUID absence) {
        return jdbc.queryForObject("SELECT statut FROM absence_patient WHERE id = ?", String.class, absence);
    }
}
