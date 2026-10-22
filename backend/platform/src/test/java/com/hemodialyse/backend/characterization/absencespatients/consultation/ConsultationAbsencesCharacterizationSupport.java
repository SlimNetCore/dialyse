package com.hemodialyse.backend.characterization.absencespatients.consultation;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.RoleScopeFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Harnais du filet de caracterisation de la slice absences-patients-a1-consultation : lectures de
 * {@code /api/v1/absences-patients} (liste, semaine, synthese, seances de rattrapage) et lectures de {@code absence_patient}
 * par la Direction ({@code /api/v1/direction/absences}) et la Seance ({@code /api/v1/seances/dashboard}, {@code /details}),
 * observees en HTTP (MockMvc, Spring complet, securite reelle + RoleScopeFilter, H2). Aucun mock du code caracterise, aucun seam.
 * <p>
 * Non-determinisme maitrise sans seam :
 * <ul>
 *   <li>dates RELATIVES au jour d'execution UTC (horloge du code legacy : {@code Clock.systemUTC}) : alias {@code <J>},
 *   {@code <J-4>}, {@code <J+2>}... pour tout ce qui depend d'« aujourd'hui » (retard de qualification, plage des seances de
 *   rattrapage, semaine par defaut) ; aucune valeur de golden ne depend de la date reelle ;</li>
 *   <li>dates ANCREES (alias {@code <ANCRE+n>}) pour les routes qui ont besoin d'un jour de semaine connu (debut de semaine
 *   dimanche, {@code weekday} des details) ou d'un mois entier : l'ancre est un dimanche fixe du passe, donc independante du jour
 *   d'execution ; aucune dependance au jour de la semaine d'execution ;</li>
 *   <li>identifiants de fixture deterministes et NOMMES ({@code <ABS_nn>}, {@code <P_AMRANI>}...) ; horodatages
 *   remplaces par {@code <HORODATAGE>}.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
abstract class ConsultationAbsencesCharacterizationSupport {

    protected static final String ABSENCES = "/api/v1/absences-patients";
    protected static final String DIRECTION = "/api/v1/direction";
    protected static final String SEANCES = "/api/v1/seances";
    /** Dimanche fixe du passe : debut de semaine connu, mois de mars 2026 entier dans le passe. */
    protected static final LocalDate ANCRE = LocalDate.of(2026, 3, 1);

    private static final Path GOLDENS =
            Path.of("src", "test", "resources", "characterization", "absences-patients", "consultation");
    private static final Map<UUID, String> NOMS = new java.util.concurrent.ConcurrentHashMap<>();

    protected static final UUID SOC_1 = nom("SOCIETE_1", 0x5001);
    protected static final UUID CENTRE_A = nom("CENTRE_A", 0xa);
    protected static final UUID CENTRE_B = nom("CENTRE_B", 0xb);
    protected static final UUID CENTRE_VIDE = nom("CENTRE_VIDE", 0xc);
    protected static final UUID DIRECTEUR = nom("DIRECTEUR", 0xd001);
    protected static final UUID UTILISATEUR = nom("UTILISATEUR", 0xf001);
    protected static final UUID P_AMRANI = nom("P_AMRANI", 0xa001);
    protected static final UUID P_BELKACEM = nom("P_BELKACEM", 0xa002);
    protected static final UUID P_CHIKHI = nom("P_CHIKHI", 0xa003);
    protected static final UUID P_DAHMANI = nom("P_DAHMANI", 0xa004);
    protected static final UUID P_CENTRE_B = nom("P_CENTRE_B", 0xb001);
    protected static final UUID P_FANTOME = nom("P_FANTOME", 0xe001);

    private static final Pattern UUID_RE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern HORODATAGE_RE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");

    protected final ObjectMapper mapper =
            JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final Map<String, String> datesNommees = new LinkedHashMap<>();
    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RoleScopeFilter roleScopeFilter;
    protected MockMvc mvc;

    protected static UUID nom(String label, long suffixe) {
        UUID u = UUID.fromString(String.format("99998100-0000-0000-0000-%012x", suffixe));
        NOMS.put(u, "<" + label + ">");
        return u;
    }

    /** Absence de rang n : identifiant croissant avec le rang (tri secondaire par identifiant verrouille). */
    protected static UUID idAbsence(int rang) {
        return nom(String.format("ABS_%02d", rang), 0xe00000L + rang);
    }

    // ------------------------------------------------------------------ dates

    /** Aujourd'hui selon l'horloge du code legacy (UTC). */
    protected static LocalDate aujourdhui() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /** Jour relatif au jour d'execution (n = nombre de jours dans le passe ; negatif = futur), nomme {@code <J-n>}. */
    protected LocalDate jour(int n) {
        LocalDate d = aujourdhui().minusDays(n);
        datesNommees.put(d.toString(), n == 0 ? "<J>" : n > 0 ? "<J-" + n + ">" : "<J+" + (-n) + ">");
        return d;
    }

    /** Jour ancre + n, nomme {@code <ANCRE+n>} ({@code <ANCRE-n>} si negatif). */
    protected LocalDate ancre(int n) {
        LocalDate d = ANCRE.plusDays(n);
        datesNommees.put(d.toString(), n == 0 ? "<ANCRE>" : n > 0 ? "<ANCRE+" + n + ">" : "<ANCRE-" + (-n) + ">");
        return d;
    }

    // ------------------------------------------------------------------ identites

    protected static RequestPostProcessor as(UUID centre, String... roles) {
        return user(UserPrincipal.create(UTILISATEUR.toString(), centre == null ? null : centre.toString(), "u", "",
                List.of(roles), true));
    }

    protected static RequestPostProcessor adminA() {
        return as(CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor adminB() {
        return as(CENTRE_B, "ADMIN");
    }

    protected static RequestPostProcessor direction() {
        return user(new UserPrincipal(DIRECTEUR.toString(), null, "u-direction", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOC_1.toString()));
    }

    protected abstract String theme();

    // ------------------------------------------------------------------ cycle

    @BeforeEach
    void setUpConsultation() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter).build();
        cleanupConsultation();
        datesNommees.clear();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC_1, "ZT-ABS-S1", "Societe Absences Un");
        centre(CENTRE_A, "ZT-ABS-A", "Centre Alpha");
        centre(CENTRE_B, "ZT-ABS-B", "Centre Beta");
        centre(CENTRE_VIDE, "ZT-ABS-V", "Centre Vide");
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                DIRECTEUR, "zt-abs-direction", "x", null, "Directeur Un");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", DIRECTEUR, SOC_1);
        patient(P_AMRANI, CENTRE_A, "ABS-001", "Amrani", "Lina");
        patient(P_BELKACEM, CENTRE_A, "ABS-002", "Belkacem", "");
        patient(P_CHIKHI, CENTRE_A, "ABS-003", "Chikhi", "   ");
        patient(P_DAHMANI, CENTRE_A, "ABS-004", "Dahmani", "Yasmine");
        patient(P_CENTRE_B, CENTRE_B, "ABS-B01", "Zerrouki", "Rania");
    }

    @AfterEach
    void cleanupConsultation() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B, CENTRE_VIDE)) {
            jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", c);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", c);
        }
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", DIRECTEUR);
        jdbc.update("DELETE FROM app_user WHERE username LIKE 'zt-abs-%'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-ABS-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-ABS-%'");
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

    /** Seance (compteur local pour un identifiant deterministe). */
    private int compteurSeances;

    protected UUID seance(UUID patient, UUID centre, LocalDate jour, String statut) {
        compteurSeances++;
        UUID id = nom(String.format("SEANCE_%02d", compteurSeances), 0x5e0000L + compteurSeances);
        Timestamp t = Timestamp.valueOf("2026-01-15 08:00:00");
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, validated_at, "
                        + "signed_infirmier_at, signed_medecin_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, patient, centre, Date.valueOf(jour), statut, t, t, t, t);
        return id;
    }

    protected Abs abs(int rang, UUID patient, LocalDate jour) {
        return new Abs(rang, patient, jour);
    }

    /** Ligne {@code absence_patient} (fixture SQL), construite par chainage puis {@link #poser()}. */
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
            UUID id = idAbsence(rang);
            boolean estQualifiee = !statut.equals("A_QUALIFIER");
            Timestamp declaree = Timestamp.valueOf("2026-01-15 08:00:00");
            Timestamp qualifieeLe = Timestamp.valueOf("2026-01-16 09:30:00");
            jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, motif, commentaire, "
                            + "forfait_id, forfait_libelle, prix_ttc, taux_tva, montant_ht, declaree_par, declaree_le, qualifiee_par, "
                            + "qualifiee_le, date_rattrapage, modifiee_par, modifiee_le) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
                    id, centre, patient, Date.valueOf(jour), source, statut, motif, commentaire, forfait, new BigDecimal(ttc),
                    new BigDecimal(taux), new BigDecimal(ht), source.equals("DECLAREE") ? UTILISATEUR : null, declaree,
                    estQualifiee ? UTILISATEUR : null, estQualifiee ? qualifieeLe : null,
                    rattrapage == null ? null : Date.valueOf(rattrapage));
            return id;
        }
    }

    // ------------------------------------------------------------------ requetes

    protected Resp doGet(String path, RequestPostProcessor who) throws Exception {
        return reponse(mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse());
    }

    protected Resp doGetAnonyme(String path) throws Exception {
        return reponse(mvc.perform(get(java.net.URI.create(path))).andReturn().getResponse());
    }

    private static Resp reponse(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8), r.getHeader("Cache-Control"),
                r.getHeader("Content-Disposition"));
    }

    protected JsonNode json(Resp r) {
        return mapper.readTree(r.raw);
    }

    // ------------------------------------------------------------------ golden

    protected void golden(String name, Resp r) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, new Scrubber().render(r));
    }

    protected void goldenNode(String name, JsonNode node) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), name,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new Scrubber().scrub(node)));
    }

    protected void goldenTexte(String name, Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status);
        o.put("contentType", r.contentType);
        o.put("contentDisposition", r.disposition);
        o.put("contenu", new Scrubber().scrubString(r.raw));
        GoldenMaster.verify(GOLDENS.resolve(theme()), name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o));
    }

    /** Statut, total, page, taille et identifiants dans l'ordre servi (resume d'une page de la liste). */
    protected ObjectNode resumeListe(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status);
        if (r.status != 200) {
            o.set("corps", r.raw == null || r.raw.isBlank() ? mapper.nullNode() : mapper.readTree(r.raw));
            return o;
        }
        JsonNode corps = json(r);
        o.set("total", corps.path("total"));
        o.set("page", corps.path("page"));
        o.set("size", corps.path("size"));
        ArrayNode ids = o.putArray("ids");
        corps.path("items").forEach(i -> ids.add(i.path("id").asString()));
        return o;
    }

    protected record Resp(int status, String contentType, String raw, String cacheControl, String disposition) {
    }

    /**
     * Normalise : identifiants nommes -> alias, autres UUID -> {@code <UUID_GENERE_n>}, horodatages -> {@code <HORODATAGE>},
     * dates relatives et ancrees -> alias, cles triees, messages techniques non verrouilles.
     */
    protected final class Scrubber {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private int inconnus = 0;

        Scrubber() {
            NOMS.forEach((id, nomAlias) -> aliases.put(id.toString(), nomAlias));
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
                boolean technique = "INTERNAL_ERROR".equals(code) || "DATA_ACCESS_ERROR".equals(code);
                List<Map.Entry<String, JsonNode>> entrees = new ArrayList<>();
                n.properties().forEach(entrees::add);
                entrees.sort(Map.Entry.comparingByKey(Comparator.naturalOrder()));
                for (Map.Entry<String, JsonNode> e : entrees) {
                    o.set(e.getKey(), technique && e.getKey().equals("detail")
                            ? mapper.getNodeFactory().stringNode("<message technique Spring/Jackson/SGBD non verrouille>")
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

        String render(Resp resp) {
            ObjectNode root = mapper.createObjectNode();
            root.put("status", resp.status);
            root.put("contentType", resp.contentType);
            if (resp.cacheControl != null) root.put("cacheControl", resp.cacheControl);
            if (resp.raw == null || resp.raw.isBlank()) {
                root.putNull("body");
            } else {
                try {
                    root.set("body", scrub(mapper.readTree(resp.raw)));
                } catch (RuntimeException e) {
                    root.put("bodyText", scrubString(resp.raw));
                }
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        }
    }
}
