package com.hemodialyse.backend.characterization.seance.se7e;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE7e lot e4 : la reprise de données (route {@code /api/v1/admin/migration}, ADMIN, centre de la session) lit et écrit {@code seances}
 * par des noms de table CALCULÉS, invisibles à la garde {@code TableDesSeancesTest} (qui ne voit que les littéraux SQL) :
 * <ul>
 *   <li>{@code HistoricalRecordJdbcAdapter} : {@code findExisting} ({@code :94}, {@code SELECT id FROM seances WHERE center_id = ? AND
 *   date_seance = ? AND patient_id = ? ORDER BY created_at}, sans statut) et {@code insert} ({@code :118}) à l'import du fichier des
 *   séances ({@code POST /batches/{id}/import/seances}) ;</li>
 *   <li>{@code MigrationRollbackJdbcAdapter} à l'annulation du lot ({@code POST /batches/{id}/cancel}) : séances du patient repris
 *   ({@code :83}, {@code FROM seances WHERE patient_id = ?}, SANS centre), séance reprise facturée ({@code :106}, {@code facture_id}, sans
 *   centre) ou complétée (volets), puis suppression ({@code :143}, {@code DELETE FROM seances WHERE id = ? AND center_id = ?}).</li>
 * </ul>
 * Observation : réponses HTTP (rapport d'import réduit aux compteurs et codes, refus d'annulation) et état SQL de la table réelle.
 * <p>Lot L5a (2026-11-10, ADR 0117, KB-SEA-89) : {@code findExisting} ignore désormais les séances {@code ANNULEE} (golden
 * {@code deja_presente_et_annulation_bloquee_nouveau_code}, ancien archivé sous {@code seance/se7e/_ecarts-legacy/reprise}).
 */
class LesSeancesDeLaRepriseDeDonneesCharacterizationIntegrationTest extends Se7eCharacterizationSupport {

    private static final String PATIENTS_CSV = "Identifiant d'origine;N° d'assurance;Nom;Prénom;Sexe;Date de naissance;Date d'admission\n"
            + "P1;ZSE7E1;SEPTE;Karim;M;15/03/1962;02/01/2019\n";

    @Override
    protected String dossier() {
        return "reprise";
    }

    private UUID patientRepris(UUID lot) throws Exception {
        importer(lot, "patients", PATIENTS_CSV, adminA());
        UUID p1 = jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ? AND code_patient IS NOT NULL AND nom = 'SEPTE'", UUID.class,
                CENTRE_A);
        aliasNommes.put(p1.toString(), "<P1_REPRIS>");
        return p1;
    }

    /** Rapport d'import réduit : compteurs, application, codes et lignes des erreurs et avertissements. */
    private Map<String, Object> rapport(org.springframework.mock.web.MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        JsonNode c = mapper.readTree(r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        for (String cle : List.of("totalRows", "created", "updated", "applied")) {
            if (c.has(cle)) m.put(cle, c.path(cle));
        }
        for (String liste : List.of("errors", "warnings")) {
            List<String> l = new ArrayList<>();
            for (JsonNode i : c.path(liste)) l.add("ligne " + i.path("row") + " " + i.path("code").asString() + " | " + i.path("message").asString());
            m.put(liste, l);
        }
        return m;
    }

    private Map<String, Object> annulation(UUID lot) throws Exception {
        Resp r = doPost(MIGRATION_API + "/batches/" + lot + "/cancel", "{}", adminA());
        Map<String, Object> m = refusOuStatut(r.status(), r.raw());
        if (r.status() == 200) m.put("statut du lot", corps(r).path("status").asString());
        Object detail = m.get("detail");
        if (detail instanceof String d && d.contains(" — ")) {
            // les bloquants suivent l'ordre des correspondances (migration_id_map ORDER BY created_at, sans départage) : triés par le test
            List<String> bloquants = new ArrayList<>(List.of(d.substring(d.indexOf(" — ") + 3).split(" ; ")));
            bloquants.sort(java.util.Comparator.naturalOrder());
            m.put("detail", d.substring(0, d.indexOf(" — ")));
            m.put("bloquants (tries par le test)", bloquants);
        }
        return m;
    }

    @Test
    void une_seance_deja_presente_le_meme_jour_dans_le_centre_est_conservee_sauf_si_elle_est_annulee() throws Exception {
        UUID lot = ouvrirUnLot();
        UUID p1 = patientRepris(lot);
        // séances posées par la plateforme AVANT l'import : une ANNULEE au centre A le 5 janvier 2026, une VALIDEE au centre B le 7 janvier,
        // une VALIDEE au centre A le 8 janvier (L5a, KB-SEA-89 : seule la séance tenue bloque la reprise du même jour)
        seanceEnBase(CENTRE_A, p1, "PLATEFORME_A_ANNULEE", fixe(4), "ANNULEE");
        seanceEnBase(CENTRE_B, p1, "PLATEFORME_B_VALIDEE", fixe(6), "VALIDEE");
        seanceEnBase(CENTRE_A, p1, "PLATEFORME_A_VALIDEE", fixe(7), "VALIDEE");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("import", rapport(importer(lot, "seances", "Patient;Date de séance;Facturée\n"
                + "P1;05/01/2026;oui\n"     // même jour qu'une séance ANNULEE du centre A : importée (une annulée n'est pas une séance tenue)
                + "P1;07/01/2026;non\n"     // même jour qu'une séance du centre B : importée au centre A
                + "P1;08/01/2026;oui\n"     // même jour qu'une séance VALIDEE du centre A : conservée, pas d'import
                + "P1;30/09/2025;oui\n", adminA())));   // antérieure à la période reprise (depuis le 2025-10-01) : ignorée
        m.put("seances apres import", etatDesSeances());
        m.put("annulation (une seance de la plateforme du patient repris bloque)", annulation(lot));
        m.put("seances apres annulation refusee", etatDesSeances());
        arbre("deja_presente_et_annulation_bloquee_nouveau_code", m);
    }

    @Test
    void l_annulation_est_refusee_par_une_seance_reprise_completee_ou_facturee_dans_la_plateforme() throws Exception {
        UUID lot = ouvrirUnLot();
        patientRepris(lot);
        importer(lot, "seances", "Patient;Date de séance;Facturée\nP1;05/01/2026;non\nP1;07/01/2026;non\nP1;08/01/2026;non\n", adminA());
        UUID completee = jdbc.queryForObject("SELECT id FROM seances WHERE center_id = ? AND date_seance = ?", UUID.class, CENTRE_A,
                java.sql.Date.valueOf(fixe(4)));
        UUID facturee = jdbc.queryForObject("SELECT id FROM seances WHERE center_id = ? AND date_seance = ?", UUID.class, CENTRE_A,
                java.sql.Date.valueOf(fixe(6)));
        fixe(7);
        aliasNommes.put(completee.toString(), "<REPRISE_COMPLETEE>");
        aliasNommes.put(facturee.toString(), "<REPRISE_FACTUREE_ET_COMPLETEE>");
        voletParamedical(CENTRE_A, completee, "70.00", "68.00", "13/8", "2000");
        voletParamedical(CENTRE_A, facturee, "71.00", "69.00", "12/8", "2000");
        jdbc.update("UPDATE seances SET facture_id = ? WHERE id = ?", UUID.nameUUIDFromBytes("se7e-facture".getBytes()), facturee);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("annulation", annulation(lot));
        m.put("seances apres annulation refusee", etatDesSeances());
        arbre("completee_ou_facturee", m);
    }

    @Test
    void l_annulation_est_refusee_par_une_seance_du_patient_repris_posee_dans_un_autre_centre() throws Exception {
        UUID lot = ouvrirUnLot();
        UUID p1 = patientRepris(lot);
        importer(lot, "seances", "Patient;Date de séance;Facturée\nP1;05/01/2026;oui\n", adminA());
        fixe(4);
        seanceEnBase(CENTRE_B, p1, "PLATEFORME_B_ANNULEE", fixe(20), "ANNULEE");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("annulation par le centre A (la seance du centre B porte le meme patient_id)", annulation(lot));
        jdbc.update("DELETE FROM seances WHERE center_id = ?", CENTRE_B);
        m.put("annulation apres retrait de la seance du centre B", annulation(lot));
        m.put("seances apres annulation", etatDesSeances());
        m.put("patients repris restants au centre A", jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ?", Integer.class, CENTRE_A));
        arbre("autre_centre_puis_annulation", m);
    }
}
