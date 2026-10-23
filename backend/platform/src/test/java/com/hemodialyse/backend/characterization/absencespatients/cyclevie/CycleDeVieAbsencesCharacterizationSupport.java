package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Harnais du filet de caracterisation de la slice absences-patients-a2-cycle-de-vie : ecritures de
 * {@code /api/v1/absences-patients} (declarer, qualifier, rattraper, annuler) et effets sur la Facturation (exclusion et
 * reinclusion d'une seance par l'ACL {@code AbsencesDesPatientsLegacy}), observes en HTTP (MockMvc, contexte Spring complet,
 * securite reelle + RoleScopeFilter, H2) et dans la table {@code absence_patient}. Aucun mock du code caracterise, aucun seam.
 * <p>
 * Reprend la methode du harnais A1 ({@code ConsultationAbsencesCharacterizationSupport}, package-private donc copie ici) :
 * <ul>
 *   <li>dates RELATIVES au jour d'execution du fuseau du centre Africa/Algiers (UTC avant A3, decision 7 ; horloge du legacy : {@code Clock.systemUTC}), alias {@code <J>},
 *   {@code <J-4>} ; une execution lancee a moins de deux minutes de minuit UTC attend minuit (aucune bascule de jour pendant un
 *   test) ; aucune dependance au jour de la semaine ;</li>
 *   <li>identifiants de fixture NOMMES ({@code <P_AMRANI>}, {@code <U_ADMIN>}) ; identifiants generes par le code nommes dans
 *   l'ordre de creation ({@code <ABS_1>}...) ; horodatages -> {@code <HORODATAGE>} (presence/absence verrouillee, valeur non).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class CycleDeVieAbsencesCharacterizationSupport {

    protected static final String ABSENCES = "/api/v1/absences-patients";
    private static final Path GOLDENS =
            Path.of("src", "test", "resources", "characterization", "absences-patients", "cycle-de-vie");
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
    protected static final UUID F_PREMIUM = nom("F_PREMIUM", 0xf0a2);
    protected static final UUID F_CENTRE_B = nom("F_CENTRE_B", 0xf0b1);

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    protected final ObjectMapper mapper =
            JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final Map<String, String> datesNommees = new LinkedHashMap<>();
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
    protected MockMvc mvc;

    protected static UUID nom(String label, long suffixe) {
        UUID u = UUID.fromString(String.format("99998200-0000-0000-0000-%012x", suffixe));
        NOMS.put(u, "<" + label + ">");
        return u;
    }

    /** Absence posee par fixture, de rang n (identifiant nomme {@code <ABS_FIXTURE_nn>}). */
    protected static UUID idFixture(int rang) {
        return nom(String.format("ABS_FIXTURE_%02d", rang), 0xe00000L + rang);
    }

    protected abstract String theme();

    // ------------------------------------------------------------------ dates

    /** Aujourd'hui selon l'horloge du code legacy (UTC). */
    /** Fuseau du centre (décision 7, A3) : « aujourd'hui » n'est plus le jour UTC. */
    protected static final java.time.ZoneId FUSEAU_DU_CENTRE = java.time.ZoneId.of("Africa/Algiers");

    protected static LocalDate aujourdhui() {
        return LocalDate.now(FUSEAU_DU_CENTRE);
    }

    /** Jour relatif au jour d'execution (n = jours dans le passe ; negatif = futur), nomme {@code <J-n>}. */
    protected LocalDate jour(int n) {
        LocalDate d = aujourdhui().minusDays(n);
        datesNommees.put(d.toString(), n == 0 ? "<J>" : n > 0 ? "<J-" + n + ">" : "<J+" + (-n) + ">");
        return d;
    }

    /**
     * Jour de calendrier nomme (stable d'un jour d'execution a l'autre) : alias libre {@code <nom>} dans les goldens.
     */
    protected void jourNomme(LocalDate d, String alias) {
        datesNommees.put(d.toString(), alias);
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

    /** Profil de direction de la societe (refuse par le filtre de perimetre sur ces routes). */
    protected static RequestPostProcessor direction() {
        return user(new UserPrincipal(U_ADMIN.toString(), null, "u-direction", "",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DIRECTION")), true,
                SOC_1.toString()));
    }

    // ------------------------------------------------------------------ cycle

    @BeforeEach
    void setUpCycleDeVie() throws Exception {
        attendreSiProcheDeMinuitDuCentre();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupCycleDeVie();
        datesNommees.clear();
        generes.clear();
        compteurGeneres = 0;
        compteurSeances = 0;
        diffusions.clear();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_1, "ZT-ABS2-S1", "Societe Absences Deux");
        centre(CENTRE_A, "ZT-ABS2-A", "Centre Alpha");
        centre(CENTRE_B, "ZT-ABS2-B", "Centre Beta");
        patient(P_AMRANI, CENTRE_A, "ABS2-001", "Amrani", "Lina");
        patient(P_BELKACEM, CENTRE_A, "ABS2-002", "Belkacem", "Omar");
        patient(P_CHIKHI, CENTRE_A, "ABS2-003", "Chikhi", "Nadia");
        patient(P_DAHMANI, CENTRE_A, "ABS2-004", "Dahmani", "Yanis");
        patient(P_CENTRE_B, CENTRE_B, "ABS2-B01", "Zerrouki", "Rania");
        forfait(F_HD, CENTRE_A, "F-HD", "Forfait HD", "10000.00");
        forfait(F_PREMIUM, CENTRE_A, "F-PREM", "Forfait Premium", "15000.00");
        forfait(F_CENTRE_B, CENTRE_B, "F-B", "Forfait du centre B", "1000.00");
        for (UUID p : List.of(P_AMRANI, P_BELKACEM, P_CHIKHI, P_DAHMANI)) {
            pec(p, CENTRE_A, "2020-01-01", null, null, null, null, F_HD, 1);
        }
        pec(P_CENTRE_B, CENTRE_B, "2020-01-01", null, null, null, null, F_CENTRE_B, 1);
        tva(CENTRE_A, "19.00", false, "HEMODIALYSE", "2000-01-01", null);
        tva(CENTRE_B, "9.00", false, "HEMODIALYSE", "2000-01-01", null);
        capteurDeDiffusions();
    }

    @AfterEach
    void cleanupCycleDeVie() {
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
            jdbc.update("DELETE FROM facturation_settings WHERE center_id = ?", c);
            jdbc.update("DELETE FROM facture_sequence WHERE center_id = ?", c);
        }
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-ABS2-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-ABS2-%'");
    }

    /** Aucune bascule de jour UTC pendant un test : on attend minuit si on en est a moins de deux minutes. */
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
                    // le modele de messagerie serialise le contenu en JSON (octets) avant le courtier
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

    protected void patient(UUID id, UUID centre, String code, String nom, String prenom) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'F', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, code, nom, prenom, Date.valueOf(LocalDate.of(2025, 1, 10)), "ASS-" + code,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    protected void forfait(UUID id, UUID centre, String code, String libelle, String prix) {
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", id, centre, code, libelle,
                new BigDecimal(prix));
    }

    private static Date date(String jour) {
        return jour == null ? null : Date.valueOf(LocalDate.parse(jour));
    }

    /** Prise en charge ; {@code anciennete} = rang de creation (jour de janvier 2026, le plus grand est le plus recent). */
    protected void pec(UUID patient, UUID centre, String debutEffectif, String finEffectif, String debutDemande,
                       String finDemande, UUID forfaitDemande, UUID forfaitEffectif, int anciennete) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, "
                        + "date_debut_effectif, date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALIDEE', ?)",
                UUID.randomUUID(), centre, patient, date(debutDemande), date(finDemande), date(debutEffectif),
                date(finEffectif), forfaitDemande, forfaitEffectif,
                OffsetDateTime.of(2026, 1, anciennete, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    protected void supprimerLesPec(UUID patient) {
        jdbc.update("DELETE FROM prise_en_charge WHERE patient_id = ?", patient);
    }

    protected void tva(UUID centre, String taux, boolean exonere, String typePrestation, String debut, String fin) {
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, "
                        + "date_fin_validite, actif, created_by) VALUES (?, ?, 'TVA', ?, ?, ?, ?, ?, TRUE, 'fixture')",
                UUID.randomUUID(), centre, new BigDecimal(taux), typePrestation, exonere, date(debut), date(fin));
    }

    protected void supprimerLaTva(UUID centre) {
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", centre);
    }

    /** Seance du patient (compteur local pour un identifiant deterministe). */
    protected UUID seance(UUID patient, UUID centre, LocalDate jour, String statut) {
        compteurSeances++;
        UUID id = nom(String.format("SEANCE_%02d", compteurSeances), 0x5e0000L + compteurSeances);
        Timestamp t = Timestamp.valueOf("2026-01-15 08:00:00");
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, validated_at, "
                        + "signed_infirmier_at, signed_medecin_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, patient, centre, Date.valueOf(jour), statut, t, t, t, t);
        return id;
    }

    /** Facture du patient couvrant [debut, fin] : la periode est alors « facturee » pour l'absence. */
    protected void facture(UUID centre, UUID patient, String numero, LocalDate debut, LocalDate fin) {
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_code, patient_full_name, "
                        + "numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot, period_start, "
                        + "period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at) "
                        + "VALUES (?, ?, ?, ?, 'PAT', ?, 'ASS-SNAP', NULL, NULL, ?, ?, ?, 0.00, 100.00, 0.00, 100.00, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), centre, patient, numero, "Snapshot " + numero, Date.valueOf(debut), Date.valueOf(fin),
                Date.valueOf(fin));
    }

    protected Abs abs(int rang, UUID patient, LocalDate jour) {
        return new Abs(rang, patient, jour);
    }

    /** Ligne {@code absence_patient} posee par fixture (statut, motif, valeur figee...), construite par chainage. */
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

    protected Resp doPostAnonyme(String path, String corps) throws Exception {
        return reponse(mvc.perform(post(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn().getResponse());
    }

    protected Resp doPut(String path, String corps, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(put(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(corps)
                .with(who)).andReturn().getResponse());
    }

    protected Resp doPutAnonyme(String path, String corps) throws Exception {
        return reponse(mvc.perform(put(java.net.URI.create(path)).contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn().getResponse());
    }

    protected Resp doGet(String path, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse());
    }

    private static Resp reponse(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    /** Declare une absence (corps complet) et nomme l'identifiant cree dans l'ordre de creation ({@code <ABS_n>}). */
    protected Resp declarer(RequestPostProcessor who, UUID patient, LocalDate jour, String motif, String commentaire)
            throws Exception {
        StringBuilder c = new StringBuilder("{'patientId':'" + patient + "','dateSeance':'" + jour + "'");
        if (motif != null) c.append(",'motif':'").append(motif).append("'");
        if (commentaire != null) c.append(",'commentaire':'").append(commentaire.replace("'", "\\u0027")).append("'");
        c.append("}");
        Resp r = doPost(ABSENCES, json(c.toString()), who);
        if (r.status == 201) nommerGenere(UUID.fromString(json(r).path("id").asString()));
        return r;
    }

    protected Resp declarer(UUID patient, LocalDate jour, String motif) throws Exception {
        return declarer(admin(), patient, jour, motif, null);
    }

    /** Nomme un identifiant genere par le code : {@code <ABS_n>}, n = ordre de creation dans le test. */
    protected UUID nommerGenere(UUID id) {
        generes.computeIfAbsent(id, k -> "<ABS_" + (++compteurGeneres) + ">");
        return id;
    }

    protected UUID idCree(Resp r) {
        return nommerGenere(UUID.fromString(json(r).path("id").asString()));
    }

    protected Resp qualifier(RequestPostProcessor who, UUID id, String motif, String commentaire) throws Exception {
        return doPut(ABSENCES + "/" + id + "/qualification", json("{'motif':" + chaine(motif) + ",'commentaire':"
                + chaine(commentaire) + "}"), who);
    }

    protected Resp rattraper(RequestPostProcessor who, UUID id, LocalDate date) throws Exception {
        return doPut(ABSENCES + "/" + id + "/rattrapage", json("{'dateRattrapage':" + (date == null ? "null" : "'" + date + "'")
                + "}"), who);
    }

    protected Resp annuler(RequestPostProcessor who, UUID id, String commentaire) throws Exception {
        return doPut(ABSENCES + "/" + id + "/annulation", json("{'commentaire':" + chaine(commentaire) + "}"), who);
    }

    private static String chaine(String s) {
        return s == null ? "null" : "'" + s.replace("'", "\\u0027") + "'";
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

    /** Nomme (dans l'ordre date puis patient) les absences du centre creees par le code et pas encore nommees. */
    protected void nommerLesAbsences(UUID centre) {
        for (UUID id : jdbc.query("SELECT id FROM absence_patient WHERE center_id = ? ORDER BY date_seance, patient_id",
                (rs, i) -> rs.getObject(1, UUID.class), centre)) {
            if (!NOMS.containsKey(id)) nommerGenere(id);
        }
    }

    /** Seances du centre : statut, memoire de l'exclusion et absence liee, triees par date. */
    protected ArrayNode etatSeances(UUID centre) {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> l : jdbc.queryForList("SELECT id, date_seance, statut, statut_avant_exclusion, "
                + "exclusion_absence_id, forfait_override_nom, forfait_override_prix FROM seances WHERE center_id = ? "
                + "ORDER BY date_seance, id", centre)) {
            ObjectNode o = mapper.createObjectNode();
            l.forEach((k, v) -> o.put(k.toLowerCase(), v == null ? null : String.valueOf(v)));
            a.add(o);
        }
        return a;
    }

    protected int compter(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ?", Integer.class, centre);
    }

    // ------------------------------------------------------------------ golden

    /** Resume d'une reponse : statut + corps (JSON si possible) ; les refus gardent leur forme complete. */
    protected ObjectNode resume(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status);
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

    /** Resume court d'un refus : statut et code metier (pour les tableaux de cas). */
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

    /** Diffusions temps reel capturees (destination, type, contenu) sous forme de tableau JSON. */
    protected ArrayNode diffusionsCapturees() {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> d : diffusions) {
            a.add(mapper.valueToTree(d));
        }
        return a;
    }

    protected record Resp(int status, String contentType, String raw) {
    }

    /**
     * Normalise : identifiants nommes -> alias, identifiants generes connus -> {@code <ABS_n>}, autres UUID ->
     * {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>}, dates relatives -> alias, cles triees, messages
     * techniques du JDK / de la base non verrouilles.
     */
    protected final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            NOMS.forEach((id, alias) -> aliases.put(id.toString(), alias));
            generes.forEach((id, alias) -> aliases.put(id.toString(), alias));
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
            for (Map.Entry<String, String> d : datesNommees.entrySet()) {
                resultat = resultat.replace(d.getKey(), d.getValue());
            }
            return resultat;
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
}
