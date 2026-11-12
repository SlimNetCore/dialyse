package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AccesRefuse;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesStatistiquesMedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesStatistiquesParamedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.ExporterLesStatistiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.ImpressionDesStatistiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.SeancesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Tension;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PL4 : les six routes des statistiques du patient et leurs refus au format commun du module ({@code ProblemDetail} + {@code code}) :
 * 400 {@code BAD_REQUEST} (parametre absent ou illisible, corps illisible), 400 {@code VALIDATION_ERROR} (centre ou patient absent du corps),
 * 400 {@code INVALID_DATE_RANGE} et {@code INVALID_EXPORT_FORMAT}, 403 {@code ACCESS_DENIED} AVANT la validation de la periode, 403 avant 400
 * pour un profil sans droit, 404 {@code PATIENT_INTROUVABLE}. (KB-PL4-1, 2, 5.)
 */
class StatistiquesDuPatientControleurTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID AUTRE_CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c002");
    private static final String BASE = "/api/v1/patients";

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc mvc() {
        SessionCourante session = demande -> {
            if (demande != null && !demande.equals(CENTRE)) throw new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session");
            return new CentreId(CENTRE);
        };
        PatientsDuCentre patients = (centre, patient) -> !patient.valeur().equals(INCONNU);
        SeancesDuPatient seances = (centre, patient, periode) -> new SeancesDuPatient.MesuresDesSeances(2, 70.5, null, 1500.0,
                List.of(Map.of("poids_avant_kg", 70.5)),
                List.of(new SeancesDuPatient.TensionsDeSeance(LocalDate.of(2026, 1, 10), Tension.lire("130/80"), Tension.lire("bizarre")),
                        new SeancesDuPatient.TensionsDeSeance(null, Tension.ILLISIBLE, Tension.ILLISIBLE)));
        StatistiquesMedicalesPubliees medicales = new StatistiquesMedicalesPubliees() {
            @Override
            public Moyennes moyennes(UUID centre, UUID patient, LocalDate du, LocalDate au) {
                return new Moyennes(11.5, 1.2, 300.0);
            }

            @Override
            public List<Map<String, Object>> tendanceDesBilans(UUID centre, UUID patient, LocalDate du, LocalDate au) {
                return List.of(new LinkedHashMap<>(Map.of("HB_G_DL", 11.5)));
            }

            @Override
            public List<Map<String, Object>> tendanceDesPrescriptionsDEpo(UUID centre, UUID patient, LocalDate du, LocalDate au) {
                return List.of();
            }

            @Override
            public List<Map<String, Object>> lignesDExport(UUID centre, UUID patient, LocalDate du, LocalDate au) {
                return List.of(new LinkedHashMap<>(Map.of("DATE_PRELEVEMENT", "2026-01-10", "HB_G_DL", 11.5)));
            }

            @Override
            public Optional<Double> poidsSecCibleEnVigueur(UUID centre, UUID patient, LocalDate au) {
                return Optional.of(68.0);
            }
        };
        HorlogeDuCentre horloge = HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC));
        ImpressionDesStatistiquesDuPatient impression = (centre, patient, periode, paramedicales, med) -> "%PDF-test".getBytes();
        var controleur = new StatistiquesDuPatientControleur(new ConsulterLesStatistiquesParamedicales(session, patients, seances, medicales, horloge),
                new ConsulterLesStatistiquesMedicales(session, patients, medicales),
                new ExporterLesStatistiquesDuPatient(session, patients, seances, medicales, horloge, impression));
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDesStatistiquesDuPatientAdvice()).build();
    }

    private static String corps(String centre, String patient, String suite) {
        List<String> champs = new java.util.ArrayList<>();
        if (centre != null) champs.add("\"centerId\":\"" + centre + "\"");
        if (patient != null) champs.add("\"patientId\":\"" + patient + "\"");
        if (suite != null) champs.add(suite);
        return "{" + String.join(",", champs) + "}";
    }

    // ------------------------------------------------------------------ lectures

    @Test
    void le_volet_paramedical_garde_les_cles_et_les_tensions_lues_ou_nulles() throws Exception {
        profil("ROLE_INFIRMIER");
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/paramedical").param("centerId", CENTRE.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.seanceCount").value(2)).andExpect(jsonPath("$.avgPoidsAvantKg").value(70.5))
                .andExpect(jsonPath("$.avgPoidsApresKg").value(0.0)).andExpect(jsonPath("$.avgUfReelleMl").value(1500.0))
                .andExpect(jsonPath("$.poidsSecCibleKg").value(68.0)).andExpect(jsonPath("$.poidsEvolution[0].poids_avant_kg").value(70.5))
                .andExpect(jsonPath("$.taEvolution[0].ta_systolique_avant").value(130)).andExpect(jsonPath("$.taEvolution[0].ta_diastolique_avant").value(80))
                .andExpect(jsonPath("$.taEvolution[0].ta_systolique_apres").doesNotExist())
                .andExpect(jsonPath("$.taEvolution[1].date_seance").doesNotExist());
        mvc().perform(post(BASE + "/stats/paramedical/search").contentType(MediaType.APPLICATION_JSON)
                        .content(corps(CENTRE.toString(), PATIENT.toString(), "\"type\":\"autre\",\"format\":\"pdf\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seanceCount").value(2));
    }

    @Test
    void le_volet_medical_garde_les_cles_en_majuscules_du_langage_publie() throws Exception {
        profil("ROLE_MEDECIN");
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/medical").param("centerId", CENTRE.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.avgHbGDl").value(11.5)).andExpect(jsonPath("$.avgKtV").value(1.2))
                .andExpect(jsonPath("$.avgFerritineNgMl").value(300.0)).andExpect(jsonPath("$.hbTrend[0].HB_G_DL").value(11.5))
                .andExpect(jsonPath("$.epoTrend").isEmpty());
        mvc().perform(post(BASE + "/stats/medical/search").contentType(MediaType.APPLICATION_JSON).content(corps(CENTRE.toString(), PATIENT.toString(), null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avgHbGDl").value(11.5));
    }

    // ------------------------------------------------------------------ exports

    @Test
    void l_export_telecharge_un_csv_ou_un_pdf_avec_le_nom_du_patient() throws Exception {
        profil("ROLE_ADMIN");
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/export").param("centerId", CENTRE.toString()).param("format", " CSV "))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=patient-stats-" + PATIENT + ".csv"))
                .andExpect(content().contentTypeCompatibleWith("text/csv")).andExpect(content().string(containsString("\"2026-01-10\",\"11.5\"")));
        mvc().perform(post(BASE + "/stats/export/search").contentType(MediaType.APPLICATION_JSON)
                        .content(corps(CENTRE.toString(), PATIENT.toString(), "\"format\":\"pdf\"")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=patient-stats-" + PATIENT + ".pdf"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void un_format_inconnu_ou_absent_repond_400_apres_le_centre_et_la_periode() throws Exception {
        profil("ROLE_ADMIN");
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/export").param("centerId", CENTRE.toString()).param("format", "xml"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPORT_FORMAT"));
        mvc().perform(post(BASE + "/stats/export/search").contentType(MediaType.APPLICATION_JSON).content(corps(CENTRE.toString(), PATIENT.toString(), null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPORT_FORMAT"));
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/export").param("centerId", CENTRE.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Paramètre obligatoire absent : format"));
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/export").param("centerId", AUTRE_CENTRE.toString()).param("format", "xml"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ------------------------------------------------------------------ KB-PL4-1 : entrees illisibles = 400

    @Test
    void un_parametre_absent_ou_illisible_repond_400_bad_request_sans_nom_de_classe_interne() throws Exception {
        profil("ROLE_INFIRMIER");
        String url = BASE + "/" + PATIENT + "/stats/paramedical";
        mvc().perform(get(url)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Paramètre obligatoire absent : centerId"));
        mvc().perform(get(url).param("centerId", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Paramètre illisible : centerId"));
        mvc().perform(get(url).param("centerId", CENTRE.toString()).param("from", "pas-une-date")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Paramètre illisible : from"));
        mvc().perform(get(url).param("centerId", CENTRE.toString()).param("to", "31/01/2026")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(not(containsString("com.hemodialyse"))));
        mvc().perform(get(BASE + "/pas-un-uuid/stats/medical").param("centerId", CENTRE.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Paramètre illisible : patientId"));
    }

    @Test
    void un_corps_illisible_ou_absent_repond_400_et_un_centre_ou_un_patient_absent_400_validation_error() throws Exception {
        profil("ROLE_INFIRMIER");
        for (String corps : new String[]{"{pas du json", "", corps(CENTRE.toString(), PATIENT.toString(), "\"dateFrom\":\"pas-une-date\"")}) {
            mvc().perform(post(BASE + "/stats/paramedical/search").contentType(MediaType.APPLICATION_JSON).content(corps))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.detail").value("Corps de la requête illisible"));
        }
        mvc().perform(post(BASE + "/stats/paramedical/search").contentType(MediaType.APPLICATION_JSON).content(corps(null, PATIENT.toString(), null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Invalid request content."));
        mvc().perform(post(BASE + "/stats/medical/search").contentType(MediaType.APPLICATION_JSON).content(corps(CENTRE.toString(), null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void un_profil_sans_droit_recoit_403_avant_le_400_d_une_entree_illisible() throws Exception {
        profil("ROLE_MEDECIN");
        mvc().perform(post(BASE + "/stats/export/search").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/export").param("centerId", "abc").param("format", "csv"))
                .andExpect(status().isForbidden());
        profil("ROLE_INFIRMIER");
        mvc().perform(post(BASE + "/stats/paramedical/search").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ KB-PL4-5 et KB-PL4-2

    @Test
    void la_garde_de_centre_passe_avant_la_validation_de_la_periode() throws Exception {
        profil("ROLE_INFIRMIER");
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/paramedical").param("centerId", AUTRE_CENTRE.toString()).param("from", "2026-02-01")
                        .param("to", "2026-01-01"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value("Accès refusé : le centre demandé ne correspond pas à la session"));
        mvc().perform(post(BASE + "/stats/medical/search").contentType(MediaType.APPLICATION_JSON)
                        .content(corps(AUTRE_CENTRE.toString(), PATIENT.toString(), "\"dateFrom\":\"2026-02-01\",\"dateTo\":\"2026-01-01\"")))
                .andExpect(status().isForbidden());
    }

    @Test
    void une_periode_inversee_du_centre_de_la_session_repond_400_invalid_date_range() throws Exception {
        profil("ROLE_INFIRMIER");
        mvc().perform(get(BASE + "/" + PATIENT + "/stats/medical").param("centerId", CENTRE.toString()).param("from", "2026-02-01")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.error").value("La date de début doit être <= à la date de fin"));
        mvc().perform(post(BASE + "/stats/paramedical/search").contentType(MediaType.APPLICATION_JSON)
                        .content(corps(CENTRE.toString(), PATIENT.toString(), "\"dateFrom\":\"2026-02-01\",\"dateTo\":\"2026-01-01\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_repond_404_patient_introuvable_sur_les_six_routes() throws Exception {
        profil("ROLE_ADMIN");
        for (String volet : new String[]{"paramedical", "medical"}) {
            mvc().perform(get(BASE + "/" + INCONNU + "/stats/" + volet).param("centerId", CENTRE.toString())).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE")).andExpect(jsonPath("$.detail").value("Patient introuvable dans ce centre"));
            mvc().perform(post(BASE + "/stats/" + volet + "/search").contentType(MediaType.APPLICATION_JSON)
                    .content(corps(CENTRE.toString(), INCONNU.toString(), null))).andExpect(status().isNotFound());
        }
        mvc().perform(get(BASE + "/" + INCONNU + "/stats/export").param("centerId", CENTRE.toString()).param("format", "csv"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
        mvc().perform(post(BASE + "/stats/export/search").contentType(MediaType.APPLICATION_JSON)
                .content(corps(CENTRE.toString(), INCONNU.toString(), "\"format\":\"pdf\""))).andExpect(status().isNotFound());
    }
}
