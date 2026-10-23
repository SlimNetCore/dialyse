package com.hemodialyse.backend.characterization.absencespatients.detection;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.jobs.ControleDeNuitDesAbsences;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Harnais du filet de caracterisation de la slice absences-patients-a3-detection-de-nuit : controle de nuit (cron 02:30),
 * rattrapage au demarrage, reconciliation, requete « patient attendu » et {@code POST /rattrapage-detection}.
 * <p>
 * Points d'observation : la table {@code absence_patient}, le journal {@code notification_evenement}, les diffusions
 * temps reel du courtier STOMP et la reponse HTTP (MockMvc, securite reelle + RoleScopeFilter, H2). Le cron est declenche par
 * appel direct de la methode du scheduler (aucune attente d'horloge). Aucun mock du code caracterise, aucun seam.
 * <p>
 * Meme methode que A1 / A2 : dates RELATIVES au jour d'execution du fuseau du centre Africa/Algiers (UTC avant A3, decision 7 ; horloge du legacy : {@code Clock.systemUTC}), toutes
 * nommees {@code <J-n>} dans les goldens (le scrubber nomme chaque date de -5 a +430 jours) ; une execution lancee a moins de
 * deux minutes de minuit UTC attend minuit ; aucune dependance au jour de la semaine ; identifiants nommes ; horodatages
 * {@code <HORODATAGE>}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class DetectionAbsencesCharacterizationSupport {

    protected static final String ABSENCES = "/api/v1/absences-patients";
    private static final Path GOLDENS =
            Path.of("src", "test", "resources", "characterization", "absences-patients", "detection");
    private static final Map<UUID, String> NOMS = new ConcurrentHashMap<>();

    protected static final UUID SOC_1 = nom("SOCIETE_1", 0x5001);
    protected static final UUID CENTRE_A = nom("CENTRE_A", 0xa);
    protected static final UUID CENTRE_B = nom("CENTRE_B", 0xb);
    protected static final UUID U_ADMIN = nom("U_ADMIN", 0xf001);
    protected static final UUID U_SECRETAIRE = nom("U_SECRETAIRE", 0xf002);
    protected static final UUID U_MEDECIN = nom("U_MEDECIN", 0xf003);
    protected static final UUID U_INFIRMIER = nom("U_INFIRMIER", 0xf004);
    protected static final UUID U_ADMIN_B = nom("U_ADMIN_B", 0xf00b);
    protected static final UUID P_AMRANI = nom("P_AMRANI", 0xa001);
    protected static final UUID P_BELKACEM = nom("P_BELKACEM", 0xa002);
    protected static final UUID P_CHIKHI = nom("P_CHIKHI", 0xa003);
    protected static final UUID P_DAHMANI = nom("P_DAHMANI", 0xa004);
    protected static final UUID P_CENTRE_B = nom("P_CENTRE_B", 0xb001);
    protected static final UUID F_HD = nom("F_HD", 0xf0a1);
    protected static final UUID F_CENTRE_B = nom("F_CENTRE_B", 0xf0b1);
    protected static final LocalDate ADMISSION_ANCIENNE = LocalDate.of(2025, 1, 10);

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    protected final ObjectMapper mapper =
            JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final Map<UUID, String> generes = new ConcurrentHashMap<>();
    private int compteurGeneres;
    private int compteurSeances;
    /** Messages publies sur le courtier temps reel (STOMP) pendant le test : destination et contenu. */
    protected final List<Map<String, Object>> diffusions = new CopyOnWriteArrayList<>();
    private ChannelInterceptor capteur;
    private AbstractSubscribableChannel canal;
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RoleScopeFilter roleScopeFilter;
    @Autowired
    protected ControleDeNuitDesAbsences scheduler;
    protected MockMvc mvc;

    protected static UUID nom(String label, long suffixe) {
        UUID u = UUID.fromString(String.format("99998300-0000-0000-0000-%012x", suffixe));
        NOMS.put(u, "<" + label + ">");
        return u;
    }

    /** Patient nomme de rang n (rang unique par test). */
    protected static UUID pid(String label, int rang) {
        return nom("P_" + label, 0xc000L + rang);
    }

    protected static UUID idFixture(int rang) {
        return nom(String.format("ABS_FIXTURE_%03d", rang), 0xe00000L + rang);
    }

    protected abstract String theme();

    // ------------------------------------------------------------------ dates

    /** Fuseau du centre (décision 7, A3) : « aujourd'hui » n'est plus le jour UTC. */
    protected static final java.time.ZoneId FUSEAU_DU_CENTRE = java.time.ZoneId.of("Africa/Algiers");

    protected static LocalDate aujourdhui() {
        return LocalDate.now(FUSEAU_DU_CENTRE);
    }

    /** Jour relatif au jour d'execution (n = jours dans le passe ; negatif = futur). */
    protected static LocalDate jour(int n) {
        return aujourdhui().minusDays(n);
    }

    // ------------------------------------------------------------------ identites

    protected static RequestPostProcessor as(UUID utilisateur, UUID centre, String... roles) {
        return user(UserPrincipal.create(utilisateur.toString(), centre == null ? null : centre.toString(),
                "u-" + String.join("-", roles).toLowerCase(), "", List.of(roles), true));
    }

    protected static RequestPostProcessor admin() {
        return as(U_ADMIN, CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor secretaire() {
        return as(U_SECRETAIRE, CENTRE_A, "SECRETAIRE");
    }

    protected static RequestPostProcessor medecin() {
        return as(U_MEDECIN, CENTRE_A, "MEDECIN");
    }

    protected static RequestPostProcessor infirmier() {
        return as(U_INFIRMIER, CENTRE_A, "INFIRMIER");
    }

    protected static RequestPostProcessor adminB() {
        return as(U_ADMIN_B, CENTRE_B, "ADMIN");
    }

    protected static RequestPostProcessor direction() {
        return user(new UserPrincipal(U_ADMIN.toString(), null, "u-direction", "",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DIRECTION")), true,
                SOC_1.toString()));
    }

    // ------------------------------------------------------------------ cycle

    @BeforeEach
    void setUpDetection() throws Exception {
        attendreSiProcheDeMinuitDuCentre();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupDetection();
        generes.clear();
        compteurGeneres = 0;
        compteurSeances = 0;
        diffusions.clear();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_1, "ZT-ABS3-S1", "Societe Absences Trois");
        centre(CENTRE_A, "ZT-ABS3-A", "Centre Alpha");
        centre(CENTRE_B, "ZT-ABS3-B", "Centre Beta");
        forfait(F_HD, CENTRE_A, "F-HD", "Forfait HD", "10000.00");
        forfait(F_CENTRE_B, CENTRE_B, "F-B", "Forfait du centre B", "1000.00");
        tva(CENTRE_A, "19.00", false, "HEMODIALYSE", "2000-01-01", null);
        tva(CENTRE_B, "9.00", false, "HEMODIALYSE", "2000-01-01", null);
        capteurDeDiffusions();
    }

    @AfterEach
    void cleanupDetection() {
        if (canal != null && capteur != null) {
            canal.removeInterceptor(capteur);
            canal = null;
        }
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", c);
            jdbc.update("DELETE FROM factures WHERE center_id = ?", c);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", c);
            jdbc.update("DELETE FROM forfait WHERE center_id = ?", c);
            jdbc.update("DELETE FROM tva_types WHERE center_id = ?", c);
            jdbc.update("DELETE FROM center_holiday WHERE center_id = ?", c);
            jdbc.update("DELETE FROM center_closure_day WHERE center_id = ?", c);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", c);
            jdbc.update("DELETE FROM facturation_settings WHERE center_id = ?", c);
            jdbc.update("DELETE FROM facture_sequence WHERE center_id = ?", c);
        }
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-ABS3-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-ABS3-%'");
    }

    private static void attendreSiProcheDeMinuitDuCentre() throws InterruptedException {
        Instant minuit = aujourdhui().plusDays(1).atStartOfDay(FUSEAU_DU_CENTRE).toInstant();
        Duration reste = Duration.between(Instant.now(), minuit);
        if (reste.compareTo(Duration.ofMinutes(2)) < 0) {
            Thread.sleep(reste.toMillis() + 2000);
        }
    }

    private void capteurDeDiffusions() {
        canal = context.getBean("brokerChannel", AbstractSubscribableChannel.class);
        capteur = new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
                if (destination != null) {
                    Object charge = message.getPayload();
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("destination", destination);
                    d.put("message", charge instanceof byte[] octets
                            ? mapper.readTree(new String(octets, StandardCharsets.UTF_8)) : String.valueOf(charge));
                    diffusions.add(d);
                }
                return message;
            }
        };
        canal.addInterceptor(capteur);
    }

    // ------------------------------------------------------------------ fixtures SQL

    private void centre(UUID id, String code, String name) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, name, SOC_1);
    }

    protected void forfait(UUID id, UUID centre, String code, String libelle, String prix) {
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", id, centre, code, libelle,
                new BigDecimal(prix));
    }

    private static Date date(String jour) {
        return jour == null ? null : Date.valueOf(LocalDate.parse(jour));
    }

    protected void pec(UUID patient, UUID centre, UUID forfaitEffectif) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, "
                        + "date_debut_effectif, date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) "
                        + "VALUES (?, ?, ?, NULL, NULL, ?, NULL, NULL, ?, 'VALIDEE', ?)",
                UUID.randomUUID(), centre, patient, date("2020-01-01"), forfaitEffectif,
                OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    protected void tva(UUID centre, String taux, boolean exonere, String typePrestation, String debut, String fin) {
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, "
                        + "date_fin_validite, actif, created_by) VALUES (?, ?, 'TVA', ?, ?, ?, ?, ?, TRUE, 'fixture')",
                UUID.randomUUID(), centre, new BigDecimal(taux), typePrestation, exonere, date(debut), date(fin));
    }

    protected void supprimerLaTva(UUID centre) {
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", centre);
    }

    /** Jour ferie (table {@code center_holiday}). */
    protected void ferie(UUID centre, LocalDate jour) {
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?, ?, ?, 'Ferie')",
                UUID.randomUUID(), centre, Date.valueOf(jour));
    }

    /** Jour de fermeture du centre (table {@code center_closure_day}). */
    protected void fermeture(UUID centre, LocalDate jour) {
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?, ?, ?, 'Fermeture')",
                UUID.randomUUID(), centre, Date.valueOf(jour));
    }

    protected UUID seance(UUID patient, UUID centre, LocalDate jour, String statut) {
        compteurSeances++;
        UUID id = nom(String.format("SEANCE_%03d", compteurSeances), 0x5e0000L + compteurSeances);
        Timestamp t = Timestamp.valueOf("2026-01-15 08:00:00");
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, validated_at, "
                        + "signed_infirmier_at, signed_medecin_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, patient, centre, Date.valueOf(jour), statut, t, t, t, t);
        return id;
    }

    protected void facture(UUID centre, UUID patient, String numero, LocalDate debut, LocalDate fin) {
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_code, patient_full_name, "
                        + "numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot, period_start, "
                        + "period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at) "
                        + "VALUES (?, ?, ?, ?, 'PAT', ?, 'ASS-SNAP', NULL, NULL, ?, ?, ?, 0.00, 100.00, 0.00, 100.00, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), centre, patient, numero, "Snapshot " + numero, Date.valueOf(debut), Date.valueOf(fin),
                Date.valueOf(fin));
    }

    // ------------------------------------------------------------------ patients

    /** Patient attendu tous les jours, admis depuis longtemps, avec une prise en charge au forfait du centre. */
    protected void patientAttendu(UUID id, UUID centre, String code, String nom) {
        pat(id, centre, code, nom).poser();
    }

    protected Pat pat(UUID id, UUID centre, String code, String nom) {
        return new Pat(id, centre, code, nom);
    }

    protected final class Pat {
        private final UUID id;
        private final UUID centre;
        private final String code;
        private final String nom;
        private LocalDate admission = ADMISSION_ANCIENNE;
        private String etat = "PERMANENT";
        private LocalDate evenement;
        private Boolean sommeil = Boolean.FALSE;
        private boolean[] jours = {true, true, true, true, true, true, true};
        private boolean sansPec;

        private Pat(UUID id, UUID centre, String code, String nom) {
            this.id = id;
            this.centre = centre;
            this.code = code;
            this.nom = nom;
        }

        Pat admission(LocalDate d) {
            this.admission = d;
            return this;
        }

        Pat etat(String e, LocalDate evt) {
            this.etat = e;
            this.evenement = evt;
            return this;
        }

        Pat etatNul() {
            this.etat = null;
            return this;
        }

        Pat sommeil(Boolean s) {
            this.sommeil = s;
            return this;
        }

        /** Attendu un seul jour de la semaine : celui de la date donnee. */
        Pat seulementLeJourDe(LocalDate d) {
            this.jours = new boolean[7];
            this.jours[d.getDayOfWeek().getValue() - 1] = true;
            return this;
        }

        /** Aucun jour coche. */
        Pat aucunJour() {
            this.jours = new boolean[7];
            return this;
        }

        Pat sansPec() {
            this.sansPec = true;
            return this;
        }

        UUID poser() {
            // lundi..dimanche = indices 0..6 (DayOfWeek.getValue() - 1)
            jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                            + "type_patient, etat_patient, date_evenement_etat, en_sommeil, jour_lundi, jour_mardi, jour_mercredi, "
                            + "jour_jeudi, jour_vendredi, jour_samedi, jour_dimanche, created_at) "
                            + "VALUES (?, ?, ?, ?, 'Prenom', 'F', ?, ?, 'NON_VACANCIER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, centre, code, nom, Date.valueOf(admission), "ASS-" + code, etat,
                    evenement == null ? null : Date.valueOf(evenement), sommeil,
                    jours[0], jours[1], jours[2], jours[3], jours[4], jours[5], jours[6], OffsetDateTime.now(ZoneOffset.UTC));
            if (!sansPec) pec(id, centre, centre.equals(CENTRE_A) ? F_HD : F_CENTRE_B);
            return id;
        }
    }

    protected Abs abs(int rang, UUID patient, LocalDate jour) {
        return new Abs(rang, patient, jour);
    }

    /** Ligne {@code absence_patient} posee par fixture. */
    protected final class Abs {
        private final int rang;
        private final UUID patient;
        private final LocalDate jour;
        private UUID centre = CENTRE_A;
        private String statut = "A_QUALIFIER";
        private String source = "AUTOMATIQUE";
        private String motif;
        private String commentaire;
        private String forfait = "Forfait HD";
        private String ttc = "11900.00";
        private String taux = "19.00";
        private String ht = "10000.00";
        private LocalDate rattrapage;

        private Abs(int rang, UUID patient, LocalDate jour) {
            this.rang = rang;
            this.patient = patient;
            this.jour = jour;
        }

        Abs centre(UUID c) {
            this.centre = c;
            return this;
        }

        Abs statut(String s) {
            this.statut = s;
            return this;
        }

        Abs declaree() {
            this.source = "DECLAREE";
            return this;
        }

        Abs motif(String m) {
            this.motif = m;
            return this;
        }

        Abs commentaire(String c) {
            this.commentaire = c;
            return this;
        }

        Abs valeur(String forfaitLibelle, String ttc, String taux, String ht) {
            this.forfait = forfaitLibelle;
            this.ttc = ttc;
            this.taux = taux;
            this.ht = ht;
            return this;
        }

        Abs rattrapee(LocalDate dateRattrapage) {
            this.rattrapage = dateRattrapage;
            return this;
        }

        UUID poser() {
            UUID id = idFixture(rang);
            boolean estQualifiee = !statut.equals("A_QUALIFIER");
            Timestamp declaree = Timestamp.valueOf("2026-01-15 08:00:00");
            Timestamp qualifieeLe = Timestamp.valueOf("2026-01-16 09:30:00");
            jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, motif, commentaire, "
                            + "forfait_id, forfait_libelle, prix_ttc, taux_tva, montant_ht, declaree_par, declaree_le, qualifiee_par, "
                            + "qualifiee_le, date_rattrapage, modifiee_par, modifiee_le) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
                    id, centre, patient, Date.valueOf(jour), source, statut, motif, commentaire, forfait, new BigDecimal(ttc),
                    new BigDecimal(taux), new BigDecimal(ht), source.equals("DECLAREE") ? U_SECRETAIRE : null, declaree,
                    estQualifiee ? U_SECRETAIRE : null, estQualifiee ? qualifieeLe : null,
                    rattrapage == null ? null : Date.valueOf(rattrapage));
            return id;
        }
    }

    // ------------------------------------------------------------------ requetes

    protected static String json(String corps) {
        return corps.replace('\'', '"');
    }

    protected Resp doPost(String path, String corps, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(post(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(corps)
                .with(who)).andReturn().getResponse());
    }

    protected Resp doPostType(String path, String corps, String type, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(post(java.net.URI.create(path)).contentType(type).content(corps).with(who))
                .andReturn().getResponse());
    }

    protected Resp doPostAnonyme(String path, String corps) throws Exception {
        return reponse(mvc.perform(post(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn().getResponse());
    }

    /** {@code POST /rattrapage-detection} ADMIN du centre A. */
    protected Resp rattraper(LocalDate from, LocalDate to) throws Exception {
        return rattraper(admin(), from, to);
    }

    protected Resp rattraper(RequestPostProcessor who, LocalDate from, LocalDate to) throws Exception {
        return doPost(ABSENCES + "/rattrapage-detection",
                json("{'from':'" + from + "','to':'" + to + "'}"), who);
    }

    private static Resp reponse(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8), r.getHeader("Cache-Control"));
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    // ------------------------------------------------------------------ etat de la base

    /** Lignes de {@code absence_patient} du centre (toutes colonnes), triees par date puis patient. */
    protected ArrayNode etat(UUID centre) {
        List<Map<String, Object>> lignes = jdbc.queryForList("SELECT id, patient_id, date_seance, source, statut, motif, commentaire, "
                + "forfait_id, forfait_libelle, prix_ttc, taux_tva, montant_ht, declaree_par, declaree_le, qualifiee_par, "
                + "qualifiee_le, date_rattrapage, modifiee_par, modifiee_le FROM absence_patient WHERE center_id = ? "
                + "ORDER BY date_seance, patient_id", centre);
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> l : lignes) {
            ObjectNode o = mapper.createObjectNode();
            l.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : String.valueOf(v)));
            a.add(o);
        }
        return a;
    }

    /**
     * Vue compacte et lisible des absences du centre : une ligne par absence (date, patient, statut / source, valeur figee,
     * motif, commentaire, auteur du depot / de la qualification / de la modification, rattrapage).
     */
    protected ArrayNode lignes(UUID centre) {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> l : jdbc.queryForList("SELECT date_seance, patient_id, source, statut, motif, commentaire, "
                + "forfait_libelle, prix_ttc, taux_tva, montant_ht, declaree_par, declaree_le, qualifiee_par, qualifiee_le, "
                + "date_rattrapage, modifiee_par, modifiee_le FROM absence_patient WHERE center_id = ? "
                + "ORDER BY date_seance, patient_id", centre)) {
            a.add(l.get("DATE_SEANCE") + " | " + l.get("PATIENT_ID") + " | " + l.get("STATUT") + "/" + l.get("SOURCE")
                    + " | valeur=" + l.get("FORFAIT_LIBELLE") + " ttc=" + l.get("PRIX_TTC") + " tva=" + l.get("TAUX_TVA")
                    + " ht=" + l.get("MONTANT_HT") + " | motif=" + l.get("MOTIF") + " | commentaire=" + l.get("COMMENTAIRE")
                    + " | declaree_par=" + l.get("DECLAREE_PAR") + " declaree_le=" + (l.get("DECLAREE_LE") != null)
                    + " | qualifiee_par=" + l.get("QUALIFIEE_PAR") + " qualifiee_le=" + (l.get("QUALIFIEE_LE") != null)
                    + " | rattrapage=" + l.get("DATE_RATTRAPAGE") + " | modifiee_par=" + l.get("MODIFIEE_PAR")
                    + " modifiee_le=" + (l.get("MODIFIEE_LE") != null));
        }
        return a;
    }

    /** Dates des absences du centre pour un patient, sous forme {@code date:STATUT}. */
    protected ArrayNode datesDe(UUID centre, UUID patient) {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> l : jdbc.queryForList("SELECT date_seance, statut FROM absence_patient WHERE center_id = ? "
                + "AND patient_id = ? ORDER BY date_seance", centre, patient)) {
            a.add(l.get("DATE_SEANCE") + ":" + l.get("STATUT"));
        }
        return a;
    }

    protected int compter(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ?", Integer.class, centre);
    }

    /** Entrees du journal des notifications du centre (type, charge utile, roles cibles). */
    protected ArrayNode journal(UUID centre) {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> l : jdbc.queryForList("SELECT type, payload, target_roles, cree_le FROM notification_evenement "
                + "WHERE center_id = ? ORDER BY cree_le, id", centre)) {
            ObjectNode o = mapper.createObjectNode();
            o.put("type", String.valueOf(l.get("TYPE")));
            Object payload = l.get("PAYLOAD");
            String texte = payload == null ? null : String.valueOf(payload);
            try {
                o.set("payload", texte == null ? null : mapper.readTree(texte));
            } catch (RuntimeException e) {
                o.put("payloadTexte", texte);
            }
            o.put("targetRoles", l.get("TARGET_ROLES") == null ? null : String.valueOf(l.get("TARGET_ROLES")));
            o.put("creeLe", l.get("CREE_LE") != null);
            a.add(o);
        }
        return a;
    }

    /** Diffusions temps reel destinees au centre (topic des evenements du centre), dans l'ordre. */
    protected ArrayNode diffusionsDuCentre(UUID centre) {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> d : diffusions) {
            if (String.valueOf(d.get("destination")).contains(centre.toString())) a.add(mapper.valueToTree(d));
        }
        return a;
    }

    // ------------------------------------------------------------------ golden

    protected ObjectNode resume(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status);
        if (r.cache != null) o.put("cacheControl", r.cache);
        if (r.raw == null || r.raw.isBlank()) {
            o.putNull("corps");
        } else {
            try {
                o.set("corps", mapper.readTree(r.raw));
            } catch (RuntimeException e) {
                o.put("corpsTexte", r.raw);
            }
        }
        return o;
    }

    protected ObjectNode refus(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status);
        if (r.raw != null && !r.raw.isBlank()) {
            try {
                JsonNode c = mapper.readTree(r.raw);
                if (c.has("code")) o.put("code", c.path("code").asString());
                else if (c.has("title")) o.put("title", c.path("title").asString());
            } catch (RuntimeException e) {
                o.put("corpsTexte", r.raw);
            }
        }
        return o;
    }

    protected void golden(String name, Resp r) {
        goldenNode(name, resume(r));
    }

    protected void goldenNode(String name, JsonNode node) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Scrubber().scrub(node)));
    }

    protected ObjectNode objet() {
        return mapper.createObjectNode();
    }

    protected record Resp(int status, String contentType, String raw, String cache) {
    }

    protected final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private final Map<String, String> dates = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            NOMS.forEach((id, alias) -> aliases.put(id.toString(), alias));
            generes.forEach((id, alias) -> aliases.put(id.toString(), alias));
            for (int n = -5; n <= 430; n++) {
                dates.put(jour(n).toString(), n == 0 ? "<J>" : n > 0 ? "<J-" + n + ">" : "<J+" + (-n) + ">");
            }
        }

        String scrubString(String raw) {
            String sansHeure = HORODATAGE_RE.matcher(raw).replaceAll("<HORODATAGE>");
            Matcher m = UUID_RE.matcher(sansHeure);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String a = aliases.computeIfAbsent(m.group(), k -> "<UUID_GENERE_" + (++inconnus) + ">");
                m.appendReplacement(sb, Matcher.quoteReplacement(a));
            }
            m.appendTail(sb);
            String resultat = sb.toString();
            Matcher d = Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(resultat);
            StringBuilder sd = new StringBuilder();
            while (d.find()) {
                d.appendReplacement(sd, Matcher.quoteReplacement(dates.getOrDefault(d.group(), d.group())));
            }
            d.appendTail(sd);
            return sd.toString();
        }

        JsonNode scrub(JsonNode n) {
            if (n.isString()) return mapper.getNodeFactory().stringNode(scrubString(n.asString()));
            if (n.isObject()) {
                ObjectNode o = mapper.createObjectNode();
                String code = n.path("code").asString("");
                String titre = n.path("title").asString("");
                boolean technique = "INTERNAL_ERROR".equals(code) || "DATA_ACCESS_ERROR".equals(code)
                        || "Requete invalide".equals(titre);
                List<Map.Entry<String, JsonNode>> entrees = new ArrayList<>();
                n.properties().forEach(entrees::add);
                entrees.sort(Map.Entry.comparingByKey(Comparator.naturalOrder()));
                for (Map.Entry<String, JsonNode> e : entrees) {
                    o.set(e.getKey(), technique && e.getKey().equals("detail")
                            ? mapper.getNodeFactory().stringNode("<message technique JDK/Spring/SGBD non verrouille>")
                            : scrub(e.getValue()));
                }
                return o;
            }
            if (n.isArray()) {
                ArrayNode a = mapper.createArrayNode();
                n.forEach(x -> a.add(scrub(x)));
                return a;
            }
            return n;
        }
    }

    protected static DayOfWeek jourDeSemaine(LocalDate d) {
        return d.getDayOfWeek();
    }
}
