package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice absences-patients-a6-cloture-mensuelle, comportement NOUVEAU (goldens {@code *_nouveau_code}, ADR 0042) : clôture et
 * réouverture d'un mois d'absences par l'ADMIN, avertissement chiffré, refus des cinq écritures dans un mois clos (déclaration,
 * qualification, rattrapage par le mois de l'absence ou par celui de la date de rattrapage, annulation), refus de l'exclusion
 * d'une séance (rollback) et de la réinclusion, lien d'avertissement avec la clôture de facturation. Les mois sont relatifs au jour
 * d'exécution et nommés ({@code <MOIS-1>}) dans les goldens : aucune chaîne AAAA-MM ne s'y trouve.
 */
class ClotureMensuelleDesAbsencesNouveauCodeIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    private static final String MOIS = ABSENCES + "/mois";
    private static final String FACTURATION = "/api/v1/facturation/periodes/";
    private static final String PREVIEW = "/api/v1/facturation/preview/seances/";
    private static final Pattern MOIS_ISOLE = Pattern.compile("(?<![\\d-])(\\d{4})-(\\d{2})(?![\\d-])");

    private static String chaineJson(String s) {
        return "'" + s.replace("'", "\\u0027") + "'";
    }

    private static String court(String motif) {
        return motif.length() > 40 ? "<motif de " + motif.length() + " caractères>" : motif;
    }

    @Override
    protected String theme() {
        return "cloture-mensuelle-a6";
    }

    private YearMonth courant() {
        return YearMonth.from(aujourdhui());
    }

    private YearMonth precedent() {
        return courant().minusMonths(1);
    }

    // ------------------------------------------------------------------ outils

    @BeforeEach
    @AfterEach
    void nettoyerLesMois() {
        for (UUID centre : new UUID[]{CENTRE_A, CENTRE_B}) {
            jdbc.update("DELETE FROM mois_absences_journal WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mois_absences WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_facturation_journal WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_facturation WHERE center_id = ?", centre);
        }
    }

    @BeforeEach
    void parametrageDeLaFacturation() {
        jdbc.update("INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) "
                + "VALUES (?, 'SIM-{SEQ}', TRUE, CURRENT_TIMESTAMP, 'fixture')", CENTRE_A);
        jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, 41)", CENTRE_A,
                aujourdhui().getYear());
    }

    /**
     * Jour de calendrier d'un mois relatif, nommé pour que le golden ne dépende pas de la date d'exécution.
     */
    private LocalDate nomme(YearMonth mois, int jour, String alias) {
        LocalDate d = mois.atDay(jour);
        jourNomme(d, alias);
        return d;
    }

    /**
     * Remplace chaque mois AAAA-MM isolé par son nom relatif ({@code <MOIS-1>}).
     */
    private JsonNode sansMois(JsonNode noeud) {
        String texte = mapper.writeValueAsString(noeud);
        Matcher m = MOIS_ISOLE.matcher(texte);
        StringBuilder sortie = new StringBuilder();
        while (m.find()) {
            YearMonth trouve = YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            long ecart = java.time.temporal.ChronoUnit.MONTHS.between(courant(), trouve);
            String alias = ecart == 0 ? "<MOIS>" : ecart > 0 ? "<MOIS+" + ecart + ">" : "<MOIS" + ecart + ">";
            m.appendReplacement(sortie, Matcher.quoteReplacement(alias));
        }
        m.appendTail(sortie);
        return mapper.readTree(sortie.toString());
    }

    @Override
    protected void goldenNode(String name, JsonNode node) {
        super.goldenNode(name, sansMois(node));
    }

    private Resp cloturer(YearMonth mois) throws Exception {
        return doPost(MOIS + "/" + mois + "/cloturer", json("{}"), admin());
    }

    private Resp rouvrir(YearMonth mois, String motif) throws Exception {
        return doPost(MOIS + "/" + mois + "/rouvrir", motif == null ? json("{}") : json("{'motif':" + chaineJson(motif) + "}"), admin());
    }

    /**
     * L'état du mois dans la liste de l'année (les douze mois sont rendus, un seul est montré).
     */
    private ObjectNode etatDuMois(YearMonth mois) throws Exception {
        Resp r = doGet(MOIS + "?annee=" + mois.getYear(), admin());
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        JsonNode corps = json(r);
        o.put("nombreDeMois", corps.path("mois").size());
        for (JsonNode m : corps.path("mois")) {
            if (m.path("mois").asString().equals(mois.toString())) {
                ObjectNode copie = (ObjectNode) m.deepCopy();
                copie.put("motifDeReouverture", m.path("motifDeReouverture").isNull() ? null : court(m.path("motifDeReouverture").asString()));
                o.set("mois", copie);
            }
        }
        return o;
    }

    private ArrayNode journalDesMois() {
        ArrayNode a = mapper.createArrayNode();
        jdbc.queryForList("SELECT annee, mois, type_acte, effectue_par, motif, a_qualifier_restantes, en_retard "
                + "FROM mois_absences_journal WHERE center_id = ? ORDER BY id", CENTRE_A).forEach(l -> {
            ObjectNode o = mapper.createObjectNode();
            o.put("mois", YearMonth.of((Integer) l.get("ANNEE"), (Integer) l.get("MOIS")).toString());
            o.put("type", String.valueOf(l.get("TYPE_ACTE")));
            o.put("par", String.valueOf(l.get("EFFECTUE_PAR")));
            o.put("motif", l.get("MOTIF") == null ? null : court(String.valueOf(l.get("MOTIF"))));
            o.put("aQualifier", l.get("A_QUALIFIER_RESTANTES") == null ? null : String.valueOf(l.get("A_QUALIFIER_RESTANTES")));
            o.put("enRetard", l.get("EN_RETARD") == null ? null : String.valueOf(l.get("EN_RETARD")));
            a.add(o);
        });
        return a;
    }

    private ObjectNode statut(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        if (r.status() >= 400 && r.raw() != null && !r.raw().isBlank()) {
            o.set("refus", refus(r));
        }
        return o;
    }

    // ------------------------------------------------------------------ clôture et réouverture

    @Test
    void la_cloture_d_un_mois_passe_repond_avec_l_avertissement_chiffre_et_ne_touche_pas_aux_absences() throws Exception {
        abs(1, P_AMRANI, nomme(precedent(), 3, "<03_DU_MOIS-1>")).poser();
        abs(2, P_BELKACEM, nomme(precedent(), 4, "<04_DU_MOIS-1>")).statut("JUSTIFIEE").motif("MALADIE").poser();
        abs(3, P_CHIKHI, nomme(precedent(), 5, "<05_DU_MOIS-1>")).poser();
        abs(4, P_DAHMANI, nomme(courant(), 1, "<01_DU_MOIS>")).poser();

        ObjectNode o = mapper.createObjectNode();
        o.set("avant", etatDuMois(precedent()));
        o.set("cloture", resume(cloturer(precedent())));
        o.set("apres", etatDuMois(precedent()));
        o.set("moisCourant", etatDuMois(courant()));
        o.set("journal", journalDesMois());
        o.set("base", etat(CENTRE_A));
        goldenNode("cloture_nominale_nouveau_code", o);
    }

    @Test
    void la_cloture_est_refusee_pour_le_mois_en_cours_un_mois_futur_un_mois_deja_clos_ou_une_entree_illisible() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.set("moisEnCours", statut(cloturer(courant())));
        o.set("moisSuivant", statut(cloturer(courant().plusMonths(1))));
        o.set("moisLointain", statut(cloturer(courant().plusYears(2))));
        o.set("moisIllisible", statut(doPost(MOIS + "/2026-13/cloturer", json("{}"), admin())));
        o.set("moisSansTirets", statut(doPost(MOIS + "/202609/cloturer", json("{}"), admin())));
        o.set("anneeAbsente", statut(doGet(MOIS, admin())));
        o.set("anneeHorsBornes", statut(doGet(MOIS + "?annee=1999", admin())));
        o.set("premiere", statut(cloturer(precedent())));
        o.set("seconde", statut(cloturer(precedent())));
        o.set("journal", journalDesMois());
        goldenNode("cloture_refus_nouveau_code", o);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mois_absences_journal WHERE center_id = ?", Integer.class, CENTRE_A));
    }

    @Test
    void seul_l_administrateur_cloture_et_rouvre_les_quatre_profils_lisent_et_le_centre_est_celui_de_la_session() throws Exception {
        cloturer(precedent());
        String corps = json("{'motif':'x'}");
        ObjectNode o = mapper.createObjectNode();
        o.set("secretaire", profil(secretaire(), corps));
        o.set("medecin", profil(medecin(), corps));
        o.set("infirmier", profil(infirmier(), corps));
        o.set("direction", statut(doGet(MOIS + "?annee=" + courant().getYear(), direction())));
        o.set("anonyme", statut(doPostAnonyme(MOIS + "/" + precedent() + "/rouvrir", corps)));
        o.set("autreCentre", statut(doPost(MOIS + "/" + precedent().minusMonths(1) + "/cloturer?centerId=" + CENTRE_B, json("{}"),
                admin())));
        o.set("lireAutreCentre", statut(doGet(MOIS + "?annee=" + courant().getYear() + "&centerId=" + CENTRE_B, admin())));
        o.set("adminDeLAutreCentre", statut(doGet(MOIS + "?annee=" + courant().getYear(), adminB())));
        o.set("moisClosDuCentreA", etatDuMois(precedent()));
        goldenNode("roles_et_centres_nouveau_code", o);
    }

    private ObjectNode profil(org.springframework.test.web.servlet.request.RequestPostProcessor who, String corps) throws Exception {
        ObjectNode p = mapper.createObjectNode();
        p.set("cloturer", statut(doPost(MOIS + "/" + precedent().minusMonths(1) + "/cloturer", json("{}"), who)));
        p.set("rouvrir", statut(doPost(MOIS + "/" + precedent() + "/rouvrir", corps, who)));
        p.set("lire", statut(doGet(MOIS + "?annee=" + courant().getYear(), who)));
        return p;
    }

    @Test
    void la_lecture_des_mois_ne_rend_le_motif_et_les_auteurs_qu_a_l_administrateur() throws Exception {
        cloturer(precedent());
        cloturer(precedent().minusMonths(1));
        rouvrir(precedent().minusMonths(1), "motif confidentiel");

        ObjectNode o = mapper.createObjectNode();
        for (var profil : new String[]{"admin", "secretaire", "medecin", "infirmier"}) {
            var who = switch (profil) {
                case "admin" -> admin();
                case "secretaire" -> secretaire();
                case "medecin" -> medecin();
                default -> infirmier();
            };
            ObjectNode p = mapper.createObjectNode();
            for (YearMonth m : new YearMonth[]{precedent(), precedent().minusMonths(1)}) {
                Resp r = doGet(MOIS + "?annee=" + m.getYear(), who);
                for (JsonNode e : json(r).path("mois")) {
                    if (e.path("mois").asString().equals(m.toString()))
                        p.set(m.equals(precedent()) ? "clos" : "rouvert", e);
                }
            }
            o.set(profil, p);
        }
        goldenNode("lecture_selon_le_profil_nouveau_code", o);
    }

    @Test
    void la_reouverture_exige_un_motif_borne_et_un_mois_clos_puis_laisse_ecrire_et_se_cloture_de_nouveau() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.set("moisOuvert", statut(rouvrir(precedent(), "motif")));
        cloturer(precedent());
        o.set("sansMotif", statut(rouvrir(precedent(), null)));
        o.set("motifBlanc", statut(rouvrir(precedent(), "   ")));
        o.set("motifTropLong", statut(rouvrir(precedent(), "x".repeat(501))));
        o.set("encoreClos", etatDuMois(precedent()));
        o.set("motifDe500", resume(rouvrir(precedent(), "m".repeat(500))));
        o.set("rouvert", etatDuMois(precedent()));
        o.set("declarationApres", statut(declarer(P_AMRANI, nomme(precedent(), 6, "<06_DU_MOIS-1>"), "MALADIE")));
        o.set("secondeCloture", statut(cloturer(precedent())));
        o.set("journal", journalDesMois());
        goldenNode("reouverture_nouveau_code", o);
    }

    // ------------------------------------------------------------------ un mois clos refuse les écritures

    @Test
    void un_mois_clos_refuse_la_declaration_la_qualification_le_rattrapage_et_l_annulation_et_laisse_la_base_intacte()
            throws Exception {
        LocalDate aQualifier = nomme(precedent(), 3, "<03_DU_MOIS-1>");
        LocalDate aRattraper = nomme(precedent(), 4, "<04_DU_MOIS-1>");
        LocalDate aAnnuler = nomme(precedent(), 5, "<05_DU_MOIS-1>");
        LocalDate libre = nomme(precedent(), 6, "<06_DU_MOIS-1>");
        LocalDate rattrapage = nomme(courant(), 1, "<01_DU_MOIS>");
        UUID q = abs(1, P_AMRANI, aQualifier).poser();
        UUID r = abs(2, P_BELKACEM, aRattraper).poser();
        UUID a = abs(3, P_CHIKHI, aAnnuler).poser();
        seance(P_BELKACEM, CENTRE_A, rattrapage, "VALIDEE");
        cloturer(precedent());
        int avant = compter(CENTRE_A);

        ObjectNode o = mapper.createObjectNode();
        o.set("declaration", statut(declarer(P_DAHMANI, libre, "MALADIE")));
        o.set("qualification", statut(qualifier(admin(), q, "VOYAGE", null)));
        o.set("rattrapageMoisDeLAbsenceClos", statut(rattraper(admin(), r, rattrapage)));
        o.set("annulation", statut(annuler(admin(), a, "doublon")));
        o.set("refusDetaille", refus(qualifier(admin(), q, "VOYAGE", null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("ecritures_refusees_nouveau_code", o);
        assertEquals(avant, compter(CENTRE_A));
    }

    @Test
    void le_rattrapage_exige_aussi_que_le_mois_de_la_date_de_rattrapage_soit_ouvert() throws Exception {
        LocalDate absence = nomme(precedent().minusMonths(1), 20, "<20_DU_MOIS-2>");
        LocalDate seanceDuMoisClos = nomme(precedent(), 5, "<05_DU_MOIS-1>");
        UUID id = abs(1, P_AMRANI, absence).poser();
        seance(P_AMRANI, CENTRE_A, seanceDuMoisClos, "VALIDEE");
        cloturer(precedent());

        ObjectNode o = mapper.createObjectNode();
        o.set("rattrapageParUneSeanceDUnMoisClos", statut(rattraper(admin(), id, seanceDuMoisClos)));
        o.set("refus", refus(rattraper(admin(), id, seanceDuMoisClos)));
        rouvrir(precedent(), "rattrapage tardif");
        o.set("apresReouverture", statut(rattraper(admin(), id, seanceDuMoisClos)));
        o.set("base", etat(CENTRE_A));
        goldenNode("rattrapage_mois_de_la_date_nouveau_code", o);
    }

    @Test
    void un_mois_clos_ne_gene_ni_le_mois_voisin_ni_un_autre_centre() throws Exception {
        cloturer(precedent());

        ObjectNode o = mapper.createObjectNode();
        o.set("moisVoisin", statut(declarer(P_AMRANI, nomme(courant(), 1, "<01_DU_MOIS>"), "MALADIE")));
        o.set("moisAvant", statut(declarer(P_BELKACEM, nomme(precedent().minusMonths(1), 28, "<28_DU_MOIS-2>"), "MALADIE")));
        o.set("base", etat(CENTRE_A));
        goldenNode("mois_voisins_libres_nouveau_code", o);
    }

    // ------------------------------------------------------------------ Facturation

    private String exclusion(LocalDate jour) {
        return json("{'centerId':'" + CENTRE_A + "','userId':'medecin','motif':'MALADIE','periodStart':'" + jour.minusDays(1)
                + "','periodEnd':'" + jour.plusDays(1) + "','regroupementMultiForfait':true}");
    }

    private String reinclusion(LocalDate jour) {
        return json("{'centerId':'" + CENTRE_A + "','periodStart':'" + jour.minusDays(1) + "','periodEnd':'" + jour.plusDays(1)
                + "','regroupementMultiForfait':true}");
    }

    @Test
    void l_exclusion_d_une_seance_d_un_mois_d_absences_clos_est_refusee_et_annulee_et_la_reinclusion_aussi() throws Exception {
        LocalDate jour = nomme(precedent(), 15, "<15_DU_MOIS-1>");
        UUID s = seance(P_AMRANI, CENTRE_A, jour, "VALIDEE");
        cloturer(precedent());

        ObjectNode o = mapper.createObjectNode();
        Resp exclusion = doPost(PREVIEW + s + "/exclude", exclusion(jour), admin());
        o.set("exclusionDansUnMoisClos", statut(exclusion));
        o.set("refus", refus(exclusion));
        o.set("seancesApresRefus", etatSeances(CENTRE_A));
        o.set("absencesApresRefus", etat(CENTRE_A));

        // rouvert, l'exclusion passe ; clos de nouveau, la réinclusion est refusée et l'absence de l'exclusion reste
        rouvrir(precedent(), "exclusion");
        o.set("exclusionApresReouverture", statut(doPost(PREVIEW + s + "/exclude", exclusion(jour), admin())));
        nommerLesAbsences(CENTRE_A);
        cloturer(precedent());
        Resp reinclusion = doPost(PREVIEW + s + "/include", reinclusion(jour), admin());
        o.set("reinclusionDansUnMoisClos", statut(reinclusion));
        o.set("refusReinclusion", refus(reinclusion));
        o.set("seancesApresReinclusionRefusee", etatSeances(CENTRE_A));
        o.set("absencesApresReinclusionRefusee", etat(CENTRE_A));

        rouvrir(precedent(), "reinclusion");
        o.set("reinclusionApresReouverture", statut(doPost(PREVIEW + s + "/include", reinclusion(jour), admin())));
        o.set("absencesFinales", etat(CENTRE_A));
        goldenNode("exclusion_et_reinclusion_mois_clos_nouveau_code", o);
    }

    @Test
    void la_cloture_de_facturation_avertit_que_le_mois_d_absences_n_est_pas_clos_sans_jamais_etre_refusee() throws Exception {
        abs(1, P_AMRANI, nomme(precedent(), 3, "<03_DU_MOIS-1>")).poser();

        ObjectNode o = mapper.createObjectNode();
        Resp ouvert = doPost(FACTURATION + precedent() + "/cloturer", json("{}"), admin());
        o.put("statutAbsencesOuvertes", ouvert.status());
        o.put("avertissementAbsencesOuvertes", json(ouvert).path("absencesDuMoisNonCloses").asBoolean());
        cloturer(courant().minusMonths(2));
        Resp clos = doPost(FACTURATION + courant().minusMonths(2) + "/cloturer", json("{}"), admin());
        o.put("statutAbsencesCloses", clos.status());
        o.put("avertissementAbsencesCloses", json(clos).path("absencesDuMoisNonCloses").asBoolean(true));
        // la réouverture des absences n'est PAS refusée quand la facturation du mois est close
        cloturer(precedent());
        Resp reouverture = rouvrir(precedent(), "facturation deja close");
        o.put("reouvertureDesAbsencesAlorsQueLaFacturationEstClose", reouverture.status());
        o.set("absences", etat(CENTRE_A));
        goldenNode("cloture_de_facturation_avertissement_nouveau_code", o);
        assertTrue(json(ouvert).has("absencesDuMoisNonCloses"));
    }
}
