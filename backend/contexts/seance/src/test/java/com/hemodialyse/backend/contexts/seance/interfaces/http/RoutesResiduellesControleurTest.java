package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AjouterUnJourAuCalendrier;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeCalendrier;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeJournalDuJour;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLesSeancesRecentes;
import com.hemodialyse.backend.contexts.seance.application.PatientsDuJour;
import com.hemodialyse.backend.contexts.seance.application.RenseignerLeVoletMedical;
import com.hemodialyse.backend.contexts.seance.application.RetirerUnJourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.JourDejaAuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adapter entrant des routes résiduelles (SE7a, ADR 0094) : formes JSON du legacy, table des refus (statut + code) et « 403 avant 400 ».
 * Les cas d'usage sont simulés : leurs règles sont prouvées dans les tests de l'application.
 */
class RoutesResiduellesControleurTest {

    private static final UUID CENTRE = new UUID(0, 0xA);
    private static final UUID SEANCE = new UUID(2, 2);
    private static final UUID PATIENT = new UUID(1, 1);
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    private ConsulterLeJournalDuJour journal;
    private AjouterUnJourAuCalendrier ajouter;
    private RetirerUnJourDuCalendrier retirer;
    private RenseignerLeVoletMedical volet;
    private MockMvc mvc;

    @BeforeEach
    void monter() {
        journal = Mockito.mock(ConsulterLeJournalDuJour.class);
        ajouter = Mockito.mock(AjouterUnJourAuCalendrier.class);
        retirer = Mockito.mock(RetirerUnJourDuCalendrier.class);
        volet = Mockito.mock(RenseignerLeVoletMedical.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoutesResiduellesControleur(journal, Mockito.mock(ConsulterLeCalendrier.class), ajouter,
                        retirer, volet, Mockito.mock(ConsulterLesSeancesRecentes.class)))
                .setControllerAdvice(new ErreursDeSeanceAdvice()).build();
        avecLeRole("ADMIN");
    }

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    private static void avecLeRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("marie", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void le_journal_repond_avec_les_champs_du_legacy() throws Exception {
        Mockito.when(journal.executer(CENTRE, JOUR)).thenReturn(new ConsulterLeJournalDuJour.Journal(JOUR,
                List.of(new PatientsDuJour.PatientDuJour(new SeanceId(SEANCE), new PatientId(PATIENT), JOUR, "VALIDEE", "P-1", "Nom", "Prenom")),
                List.of()));

        mvc.perform(get("/api/v1/seances/journal").param("centerId", CENTRE.toString()).param("dateSeance", "2026-06-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dateSeance").value("2026-06-15"))
                .andExpect(jsonPath("$.patients[0].seanceId").value(SEANCE.toString()))
                .andExpect(jsonPath("$.patients[0].status").value("VALIDEE"))
                .andExpect(jsonPath("$.patients[0].patientNom").value("Nom"))
                .andExpect(jsonPath("$.sortiesArticles").isEmpty());
    }

    @Test
    void un_parametre_absent_ou_illisible_est_une_requete_invalide_et_non_une_erreur_serveur() throws Exception {
        mvc.perform(get("/api/v1/seances/journal").param("centerId", CENTRE.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/seances/journal").param("centerId", CENTRE.toString()).param("dateSeance", "15/06/2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void un_profil_sans_droit_recoit_403_avant_toute_erreur_de_lecture() throws Exception {
        avecLeRole("SECRETAIRE");
        mvc.perform(put("/api/v1/seances/" + SEANCE + "/volet-medical").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/seances/patient/" + PATIENT + "/recentes").param("before", "demain"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void un_volet_medical_sans_centre_ou_illisible_est_une_requete_invalide() throws Exception {
        mvc.perform(put("/api/v1/seances/" + SEANCE + "/volet-medical").contentType(MediaType.APPLICATION_JSON).content("{\"prescription\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put("/api/v1/seances/" + SEANCE + "/volet-medical").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void deux_saisies_simultanees_du_volet_medical_la_seconde_recoit_409_ecriture_concurrente() throws Exception {
        avecLeRole("MEDECIN");
        Mockito.when(volet.executer(eq(CENTRE), eq(SEANCE), any())).thenThrow(new com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente());

        mvc.perform(put("/api/v1/seances/" + SEANCE + "/volet-medical").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"prescription\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ECRITURE_CONCURRENTE"));
    }

    @Test
    void un_jour_deja_au_calendrier_est_un_conflit_et_un_jour_absent_une_requete_invalide() throws Exception {
        Mockito.when(ajouter.executer(eq(CENTRE), eq(NatureDeJour.JOUR_FERIE), eq(JOUR), any())).thenThrow(new JourDejaAuCalendrier(NatureDeJour.JOUR_FERIE, JOUR));
        Mockito.when(ajouter.executer(eq(CENTRE), eq(NatureDeJour.FERMETURE_EXCEPTIONNELLE), eq(null), any())).thenThrow(new SaisieInvalide("Jour requis"));

        mvc.perform(post("/api/v1/seances/calendar/holiday").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"dayDate\":\"2026-06-15\",\"labelOrReason\":\"Fete\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOUR_DEJA_AU_CALENDRIER"));
        mvc.perform(post("/api/v1/seances/calendar/closure").contentType(MediaType.APPLICATION_JSON).content("{\"centerId\":\"" + CENTRE + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/v1/seances/calendar/closure").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void le_retrait_d_un_jour_absent_repond_toujours_200_deleted() throws Exception {
        Mockito.when(retirer.executer(eq(CENTRE), eq(NatureDeJour.JOUR_FERIE), any())).thenReturn(false);

        mvc.perform(delete("/api/v1/seances/calendar/holiday/" + UUID.randomUUID()).param("centerId", CENTRE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true));
        mvc.perform(delete("/api/v1/seances/calendar/holiday/xx").param("centerId", CENTRE.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/seances/calendar/holiday/" + UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }
}
