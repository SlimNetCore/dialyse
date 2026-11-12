package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.application.CahierImprime;
import com.hemodialyse.backend.contexts.seance.application.ImprimerLeCahierDeDialyse;
import com.hemodialyse.backend.contexts.seance.domain.CahierRefuse;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adapter entrant de l'impression du cahier (PL5, ADR 0144) : en-têtes par format, table des refus (statut + code), entrées illisibles. */
class CahierDeDialyseControleurTest {

    private static final UUID PATIENT = UUID.fromString("354ca79a-0000-0000-0000-000000000001");
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private ImprimerLeCahierDeDialyse imprimer;
    private MockMvc mvc;

    @BeforeEach
    void monter() {
        imprimer = Mockito.mock(ImprimerLeCahierDeDialyse.class);
        mvc = MockMvcBuilders.standaloneSetup(new CahierDeDialyseControleur(imprimer)).setControllerAdvice(new ErreursDeSeanceAdvice()).build();
    }

    @AfterEach
    void vider() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private static String route() {
        return "/api/v1/cahier-dialyse/" + PATIENT + "/imprimer";
    }

    @Test
    void le_pdf_est_servi_en_ligne_sous_le_nom_du_patient() throws Exception {
        Mockito.when(imprimer.executer(eq(CENTRE), eq(PATIENT), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 2, 1)), eq("UTC")))
                .thenReturn(new CahierImprime(new byte[]{'%', 'P', 'D', 'F'}, CahierImprime.Format.PDF));

        mvc.perform(get(route()).param("centerId", CENTRE.toString()).param("from", "2026-01-01").param("to", "2026-02-01").param("tz", "UTC"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"cahier-dialyse-354ca79a.pdf\"; filename*=UTF-8''cahier-dialyse-354ca79a.pdf"));
    }

    @Test
    void le_tableur_est_servi_en_piece_jointe_et_le_html_sans_nom() throws Exception {
        Mockito.when(imprimer.executer(any(), any(), any(), any(), any()))
                .thenReturn(new CahierImprime(new byte[]{1}, CahierImprime.Format.EXCEL))
                .thenReturn(new CahierImprime(new byte[]{1}, CahierImprime.Format.HTML));

        mvc.perform(get(route())).andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        mvc.perform(get(route())).andExpect(status().isOk()).andExpect(content().contentType("text/html"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void les_refus_ont_leur_statut_et_leur_code() throws Exception {
        Mockito.when(imprimer.executer(any(), any(), any(), any(), any()))
                .thenThrow(CahierRefuse.vide()).thenThrow(CahierRefuse.periodeInvalide()).thenThrow(new PatientIntrouvable())
                .thenThrow(new AccesRefuse());

        mvc.perform(get(route())).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CAHIER_VIDE"))
                .andExpect(jsonPath("$.detail").value("Aucune séance validée à imprimer pour ce patient"));
        mvc.perform(get(route())).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CAHIER_PERIODE_INVALIDE"));
        mvc.perform(get(route())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"))
                .andExpect(jsonPath("$.detail").value("Patient introuvable"));
        mvc.perform(get(route())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void une_entree_illisible_repond_400_et_non_500() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("m", "n/a",
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));

        mvc.perform(get("/api/v1/cahier-dialyse/pas-un-uuid/imprimer")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get(route()).param("from", "31/01/2026")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get(route()).param("centerId", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void un_autre_verbe_n_est_pas_servi_par_le_controleur() throws Exception {
        mvc.perform(post(route())).andExpect(status().isMethodNotAllowed());
    }
}
