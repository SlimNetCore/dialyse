package com.hemodialyse.backend.characterization.facturation.simulation;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice seance-se7c2-ecritures-facturation, filet COMPLÉMENTAIRE (étape 0, ARGENT, OBSERVE sans corriger) : ce que la Facturation
 * écrit et lit dans la table {@code seances} aux endroits que l'audit {@code .ddd/safety-net/seance-se7c2-ecritures-facturation.md}
 * listait comme NON verrouillés. Les ports de la Facturation (ACL JDBC câblées en production) sont appelés directement sur la
 * base (H2) : l'état réel de la table est le point d'observation (les routes HTTP sont déjà verrouillées par les filets F2 à F7).
 * La course de deux émissions est dans le contrat PostgreSQL 16 (LiaisonConcurrentePostgresContractIT, H2 n'est pas fiable pour la
 * concurrence). Indépendant de l'horloge : instants et dates fixes, aucun {@code now()}.
 */
class EcrituresEtLecturesDeSeancesParLaFacturationCharacterizationIntegrationTest extends SimulationCharacterizationSupport {

    private static final PeriodeDeFacturation SEPTEMBRE =
            new PeriodeDeFacturation(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    private static final Instant LE_20 = Instant.parse("2026-10-20T08:00:00Z");

    @Autowired
    private SeancesDeFacturation seances;
    @Autowired
    private LiaisonDesSeances liaison;
    @Autowired
    private SeancesFacturables facturables;
    @Autowired
    private LectureDuTableauDeBord tableauDeBord;
    @Autowired
    private LectureDeLaSynthese synthese;

    private int rang;

    @Override
    protected String theme() {
        return "se7c2-ecritures";
    }

    @AfterEach
    void nettoyerLesFactures() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM factures WHERE center_id = ?", centre);
        }
    }

    // ------------------------------------------------------------------ outils

    private CentreId centreA() {
        return new CentreId(CENTRE_A);
    }

    /** Une séance de septembre 2026 (une date différente par appel), avec la mémoire d'exclusion et la facture données. */
    private UUID ligne(String nom, String statut, String memoire, UUID facture) {
        rang++;
        UUID id = seance(nom, P_EFFECTIF, CENTRE_A, LocalDate.of(2026, 9, 1).plusDays(rang % 28).toString(), statut);
        jdbc.update("UPDATE seances SET statut_avant_exclusion = ?, facture_id = ? WHERE id = ?", memoire, facture, id);
        return id;
    }

    private ObjectNode etat(UUID id) {
        Map<String, Object> ligne = jdbc.queryForMap("SELECT statut, facture_id, statut_avant_exclusion, exclusion_absence_id, "
                + "forfait_override_id, forfait_override_code, forfait_override_nom, forfait_override_prix, "
                + "forfait_override_updated_by, (forfait_override_updated_at IS NULL) AS override_date_nulle "
                + "FROM seances WHERE id = ?", id);
        ObjectNode o = mapper.createObjectNode();
        ligne.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : String.valueOf(v)));
        return o;
    }

    private static String essai(Runnable ecriture) {
        try {
            ecriture.run();
            return "ACCEPTEE";
        } catch (IllegalStateException e) {
            return "REFUSEE (" + e.getMessage() + ")";
        }
    }

    // ------------------------------------------------------------------ (a) KB-SEA-47

    @Test
    void le_changement_de_forfait_de_la_facturation_ne_touche_pas_le_code_du_forfait_de_la_surcharge() {
        UUID avecCode = seance("avec_surcharge_de_code_F_HD", P_EFFECTIF, CENTRE_A, "2026-09-02", "VALIDEE");
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = 'F-HD', forfait_override_nom = 'Forfait HD', "
                + "forfait_override_prix = 3500.00 WHERE id = ?", FORFAIT_HD, avecCode);
        UUID sansSurcharge = seance("sans_surcharge", P_EFFECTIF, CENTRE_A, "2026-09-03", "SIGNEE");
        ForfaitApplique premium = new ForfaitApplique(FORFAIT_PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00"));
        ObjectNode racine = mapper.createObjectNode();
        racine.set("avant_avec_surcharge", etat(avecCode));
        racine.set("avant_sans_surcharge", etat(sansSurcharge));

        seances.poserLeForfait(centreA(), avecCode, premium, "medecin", LE_20);
        seances.poserLeForfait(centreA(), sansSurcharge, premium, "medecin", LE_20);

        racine.set("apres_avec_surcharge", etat(avecCode));
        racine.set("apres_sans_surcharge", etat(sansSurcharge));
        assertThat(etat(avecCode).path("forfait_override_code").asString()).as("code périmé F-HD, nom et prix du premium")
                .isEqualTo("F-HD");
        assertThat(etat(avecCode).path("forfait_override_nom").asString()).isEqualTo("Forfait HD Premium");
        assertThat(etat(sansSurcharge).path("forfait_override_code").isNull()).as("aucun code écrit sur une séance sans surcharge")
                .isTrue();
        goldenNode("kb_sea_47_poser_le_forfait_n_ecrit_pas_le_code", racine);
    }

    // ------------------------------------------------------------------ (a bis) lierALAbsence : garde de facture (KB-SEA-71)

    /**
     * ÉCART DÉCIDÉ (ADR 0097, KB-SEA-71, à confirmer métiers) : le legacy acceptait de lier une absence à une séance {@code ABSENT} déjà
     * rattachée à une facture ; le port de la Séance ajoute la garde {@code facture_id IS NULL} comme {@code reinclure}. Le golden d'origine est
     * archivé dans {@code _ecarts-legacy/se7c2-ecritures/..._avant_se7c2.legacy.json} (R100) ; seul ce golden change (deux champs).
     */
    @Test
    void lier_une_absence_a_une_seance_absente_deja_rattachee_a_une_facture_est_refuse() {
        UUID facture = UUID.randomUUID();
        nommer(facture, "<FACTURE_X>");
        UUID absence = UUID.randomUUID();
        nommer(absence, "<ABSENCE_X>");
        UUID absenteFacturee = ligne("absente_avec_memoire_et_facture", "ABSENT", "VALIDEE", facture);
        UUID absenteLibre = ligne("absente_avec_memoire_sans_facture", "ABSENT", "VALIDEE", null);
        UUID factureeAvecMemoire = ligne("facturee_avec_memoire", "FACTUREE", "VALIDEE", facture);
        ObjectNode racine = mapper.createObjectNode();
        racine.set("avant_absente_facturee", etat(absenteFacturee));

        racine.put("lierALAbsence_absente_facturee", essai(() -> seances.lierALAbsence(centreA(), absenteFacturee, absence)));
        racine.put("lierALAbsence_absente_sans_facture", essai(() -> seances.lierALAbsence(centreA(), absenteLibre, absence)));
        racine.put("lierALAbsence_facturee", essai(() -> seances.lierALAbsence(centreA(), factureeAvecMemoire, absence)));
        racine.put("reinclure_absente_facturee", essai(() -> seances.reinclure(centreA(), absenteFacturee, StatutDeSeance.VALIDEE)));

        racine.set("apres_absente_facturee", etat(absenteFacturee));
        racine.set("apres_absente_sans_facture", etat(absenteLibre));
        racine.set("apres_facturee", etat(factureeAvecMemoire));
        goldenNode("lier_a_l_absence_une_seance_absente_deja_facturee_nouveau_code", racine);
    }

    // ------------------------------------------------------------------ (b) KB-FAC-17 : statut en minuscules

    @Test
    void un_statut_en_minuscules_n_est_ni_facturable_ni_liable_ni_forfaitable_ni_reinclusible_mais_le_sql_accepte_son_propre_statut() {
        String[] statuts = {"VALIDEE", "ABSENT", "validee", "signee", "Signee", "absent", "facturee"};
        ObjectNode racine = mapper.createObjectNode();
        UUID facture = UUID.randomUUID();
        for (String statut : statuts) {
            ObjectNode o = racine.putObject(statut);
            UUID lue = ligne("lue_" + statut, statut, "VALIDEE", null);
            SeanceDeFacturation trouvee = seances.trouver(centreA(), lue).orElseThrow();
            o.put("trouver_statut", trouvee.statut().valeur());
            o.put("trouver_facturee", trouvee.facturee());
            o.put("trouver_estFacturable", trouvee.statut().estFacturable());
            o.put("facturable_en_septembre", facturables.dansLaPeriode(centreA(), SEPTEMBRE).stream()
                    .map(SeanceFacturable::id).anyMatch(lue::equals));

            UUID exclue = ligne("exclure_" + statut, statut, null, null);
            o.put("exclure_VALIDEE", essai(() -> seances.exclure(centreA(), exclue, StatutDeSeance.VALIDEE)));
            o.put("exclure_VALIDEE_statut_apres", statutDe(exclue));
            UUID exclueSonStatut = ligne("exclure_propre_" + statut, statut, null, null);
            o.put("exclure_avec_le_statut_lu", essai(() -> seances.exclure(centreA(), exclueSonStatut, new StatutDeSeance(statut))));
            o.put("exclure_avec_le_statut_lu_statut_apres", statutDe(exclueSonStatut));

            UUID liee = ligne("absence_" + statut, statut, "VALIDEE", null);
            o.put("lierALAbsence", essai(() -> seances.lierALAbsence(centreA(), liee, UUID.randomUUID())));
            UUID reincluse = ligne("reinclure_" + statut, statut, "VALIDEE", null);
            o.put("reinclure_VALIDEE", essai(() -> seances.reinclure(centreA(), reincluse, StatutDeSeance.VALIDEE)));
            o.put("reinclure_VALIDEE_statut_apres", statutDe(reincluse));
            UUID forfaitee = ligne("forfait_" + statut, statut, null, null);
            o.put("poserLeForfait", essai(() -> seances.poserLeForfait(centreA(), forfaitee,
                    new ForfaitApplique(FORFAIT_PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00")), "medecin", LE_20)));
            UUID liable = ligne("lier_" + statut, statut, null, null);
            o.put("lier", essai(() -> liaison.lier(centreA(), facture, List.of(liable))));
            o.put("lier_statut_apres", statutDe(liable));
        }
        goldenNode("statut_en_minuscules_matrice_des_ecritures_et_lectures", racine);
    }

    private String statutDe(UUID id) {
        return jdbc.queryForObject("SELECT statut FROM seances WHERE id = ?", String.class, id);
    }

    // ------------------------------------------------------------------ (d) statut_avant_exclusion blanc

    @Test
    void une_memoire_d_exclusion_blanche_est_lue_comme_absente_mais_le_sql_la_tient_pour_presente() {
        ObjectNode racine = mapper.createObjectNode();
        String[][] cas = {{"nulle", null}, {"vide", ""}, {"espaces", "   "}};
        for (String[] unCas : cas) {
            String nomDuCas = unCas[0];
            String memoire = unCas[1];
            ObjectNode o = racine.putObject(nomDuCas);
            UUID lue = ligne("lue_" + nomDuCas, "ABSENT", memoire, null);
            SeanceDeFacturation trouvee = seances.trouver(centreA(), lue).orElseThrow();
            o.put("trouver_statutAvantExclusion", String.valueOf(trouvee.statutAvantExclusion()));
            o.put("domaine_reinclure", essai(trouvee::reinclure));

            UUID liee = ligne("absence_" + nomDuCas, "ABSENT", memoire, null);
            o.put("lierALAbsence_sql", essai(() -> seances.lierALAbsence(centreA(), liee, UUID.randomUUID())));
            o.set("lierALAbsence_etat_apres", etat(liee));
            UUID reincluse = ligne("reinclure_" + nomDuCas, "ABSENT", memoire, null);
            o.put("reinclure_sql_VALIDEE", essai(() -> seances.reinclure(centreA(), reincluse, StatutDeSeance.VALIDEE)));
            o.set("reinclure_etat_apres", etat(reincluse));
        }
        goldenNode("statut_avant_exclusion_blanc", racine);
    }

    // ------------------------------------------------------------------ (e) deux définitions de « séance facturée »

    @Test
    void le_tableau_de_bord_compte_les_seances_par_statut_et_date_et_la_synthese_par_facture_deux_definitions_de_facturee() {
        UUID facture = UUID.randomUUID();
        UUID factureB = UUID.randomUUID();
        nommer(facture, "<FACTURE_F1>");
        nommer(factureB, "<FACTURE_DU_CENTRE_B>");
        facture(facture, CENTRE_A, P_EFFECTIF, "FEV-1");
        facture(factureB, CENTRE_B, P_CENTRE_B, "FEV-B1");
        UUID coherente = seanceDeFevrier("coherente_facturee_et_liee", "FACTUREE", facture, "2026-02-03");
        UUID liee = seanceDeFevrier("liee_mais_validee", "VALIDEE", facture, "2026-02-04");
        UUID sansLien = seanceDeFevrier("facturee_sans_facture", "FACTUREE", null, "2026-02-05");
        UUID minuscule = seanceDeFevrier("liee_statut_facturee_en_minuscules", "facturee", facture, "2026-02-06");
        UUID deplacee = seanceDeFevrier("facturee_et_liee_deplacee_en_mars", "FACTUREE", facture, "2026-03-02");
        UUID autreCentre = seanceDeFevrier("facturee_liee_a_la_facture_d_un_autre_centre", "FACTUREE", factureB, "2026-02-07");
        UUID absenteLiee = seanceDeFevrier("absente_liee", "ABSENT", facture, "2026-02-08");
        ObjectNode racine = mapper.createObjectNode();
        ArrayNode etats = racine.putArray("seances");
        for (UUID id : List.of(coherente, liee, sansLien, minuscule, deplacee, autreCentre, absenteLiee)) {
            ObjectNode o = mapper.createObjectNode();
            o.put("id", id.toString());
            jdbc.queryForMap("SELECT statut, facture_id, date_seance FROM seances WHERE id = ?", id)
                    .forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : String.valueOf(v)));
            etats.add(o);
        }

        TableauDeBordDuMois tableau = tableauDeBord.du(centreA(), YearMonth.of(2026, 2));
        ObjectNode t = racine.putObject("tableau_de_bord_de_fevrier");
        t.put("seancesFacturees_par_statut_et_date", tableau.seancesFacturees());
        t.put("facturesEmises", tableau.facturesEmises());
        ArrayNode parCaisse = t.putArray("parCaisse");
        tableau.parCaisse().forEach(r -> parCaisse.addObject().put("code", r.code()).put("seances_par_facture", r.seances())
                .put("patients", r.patients()));
        ArrayNode parSituation = t.putArray("parSituation");
        tableau.parSituation().forEach(r -> parSituation.addObject().put("code", r.code()).put("seances_par_facture", r.seances()));

        SyntheseMensuelle s = synthese.de(centreA(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        ObjectNode y = racine.putObject("synthese_de_fevrier");
        y.put("factures", s.factures());
        y.put("seances_par_facture", s.seances());
        ArrayNode sCaisse = y.putArray("parCaisse");
        s.parCaisse().forEach(r -> sCaisse.addObject().put("code", r.code()).put("seances_par_facture", r.seances())
                .put("patients", r.patients()));
        ArrayNode sSituation = y.putArray("parSituation");
        s.parSituation().forEach(r -> sSituation.addObject().put("code", r.code()).put("seances_par_facture", r.seances())
                .put("patients", r.patients()));

        assertThat(tableau.seancesFacturees()).as("statut FACTUREE exact et date de février : coherente, sans lien, autre centre")
                .isEqualTo(3);
        assertThat(s.seances()).as("facture_id de la facture de février, même centre : toutes les séances liées quel que soit "
                + "leur statut ou leur date").isEqualTo(5);
        goldenNode("deux_definitions_de_seance_facturee_tableau_de_bord_contre_synthese", racine);
    }

    private UUID seanceDeFevrier(String nom, String statut, UUID facture, String jour) {
        UUID id = seance(nom, P_EFFECTIF, CENTRE_A, jour, statut);
        jdbc.update("UPDATE seances SET facture_id = ? WHERE id = ?", facture, id);
        return id;
    }

    private void facture(UUID id, UUID centre, UUID patient, String numero) {
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_full_name, period_start, period_end, "
                        + "date_facturation, tva_rate, total_ht, total_tva, total_ttc) "
                        + "VALUES (?, ?, ?, ?, 'Fixture', ?, ?, ?, 9.00, 100.00, 9.00, 109.00)",
                id, centre, patient, numero, Date.valueOf(LocalDate.of(2026, 2, 1)), Date.valueOf(LocalDate.of(2026, 2, 28)),
                Date.valueOf(LocalDate.of(2026, 2, 28)));
    }
}
