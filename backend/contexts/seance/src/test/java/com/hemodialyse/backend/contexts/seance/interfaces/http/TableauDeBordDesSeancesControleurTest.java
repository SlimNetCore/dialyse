package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeDetailDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.ExporterLeTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.EffectifDuMois;
import com.hemodialyse.backend.contexts.seance.domain.FormatDExport;
import com.hemodialyse.backend.contexts.seance.domain.LigneDuDetail;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.SeancesAttendues;
import com.hemodialyse.backend.contexts.seance.domain.TableauDeBordDesSeances;
import com.hemodialyse.backend.contexts.seance.domain.TypeDeDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** Adapter entrant du tableau de bord mensuel (SE7b, ADR 0095) : formes JSON du legacy, en-têtes d'export, refus (400, 403 avant 400). */
class TableauDeBordDesSeancesControleurTest {

    private static final UUID CENTRE = new UUID(0, 0xA);
    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 1, 31);

    private ConsulterLeTableauDeBord tableau;
    private ConsulterLeDetailDuTableauDeBord detail;
    private ExporterLeTableauDeBord export;
    private MockMvc mvc;

    @BeforeEach
    void monter() {
        tableau = Mockito.mock(ConsulterLeTableauDeBord.class);
        detail = Mockito.mock(ConsulterLeDetailDuTableauDeBord.class);
        export = Mockito.mock(ExporterLeTableauDeBord.class);
        mvc = MockMvcBuilders.standaloneSetup(new TableauDeBordDesSeancesControleur(tableau, detail, export))
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
    void le_tableau_de_bord_repond_avec_les_champs_du_legacy() throws Exception {
        var attendues = SeancesAttendues.calculer(List.of(), DEBUT, FIN, Set.of());
        var effectif = EffectifDuMois.composer(List.of(), List.of(), DEBUT, FIN);
        Mockito.when(tableau.executer(CENTRE, 2026, 1)).thenReturn(TableauDeBordDesSeances.composer(new MoisDuTableauDeBord(2026, 1),
                LocalDate.of(2026, 6, 15), 3, 2, attendues, effectif, 1));

        mvc.perform(get("/api/v1/seances/dashboard").param("centerId", CENTRE.toString()).param("year", "2026").param("month", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.year").value(2026)).andExpect(jsonPath("$.presenceCount").value(3))
                .andExpect(jsonPath("$.absenceCount").value(2)).andExpect(jsonPath("$.totalSeances").value(3))
                .andExpect(jsonPath("$.effectifPatients").value(0)).andExpect(jsonPath("$.absenceDetails.blockedDays").value(1))
                .andExpect(jsonPath("$.absenceDetails.periodEnd").value("2026-01-31"))
                .andExpect(jsonPath("$.absenceDetails.expectedByWeekday.LUNDI").value(0))
                .andExpect(jsonPath("$.sexeDistribution.AUTRE").value(0)).andExpect(jsonPath("$.ageDistribution['60+']").value(0));
    }

    @Test
    void le_detail_repond_avec_les_champs_du_legacy() throws Exception {
        var ligne = new LigneDuDetail(new PatientId(new UUID(1, 1)), "Nom", "Prenom", LocalDate.of(2026, 1, 5), "SIGNEE");
        Mockito.when(detail.executer(CENTRE, 2026, 1, "presence")).thenReturn(new ConsulterLeDetailDuTableauDeBord.Detail(
                new MoisDuTableauDeBord(2026, 1), TypeDeDetail.PRESENCE, List.of(ligne)));

        mvc.perform(get("/api/v1/seances/dashboard/details").param("centerId", CENTRE.toString()).param("year", "2026")
                        .param("month", "1").param("kind", "presence"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("presence")).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].patientNom").value("Nom")).andExpect(jsonPath("$.items[0].weekday").value("MONDAY"))
                .andExpect(jsonPath("$.items[0].scheduled").value(true)).andExpect(jsonPath("$.items[0].present").value(true))
                .andExpect(jsonPath("$.items[0].status").value("SIGNEE"));
    }

    @Test
    void l_export_porte_le_nom_du_fichier_et_le_type_sans_jeu_de_caracteres_pour_le_csv() throws Exception {
        Mockito.when(export.executer(CENTRE, 2026, 1, "csv")).thenReturn(new ExporterLeTableauDeBord.Export("a,b".getBytes(), FormatDExport.CSV,
                "seances-dashboard-2026-01.csv"));

        mvc.perform(get("/api/v1/seances/dashboard/export").param("centerId", CENTRE.toString()).param("year", "2026").param("month", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=seances-dashboard-2026-01.csv"))
                .andExpect(header().string("Content-Type", "text/plain")).andExpect(content().string("a,b"));
    }

    @Test
    void une_annee_un_mois_ou_un_centre_absent_ou_illisible_est_une_requete_invalide_et_non_une_erreur_serveur() throws Exception {
        mvc.perform(get("/api/v1/seances/dashboard").param("centerId", CENTRE.toString()).param("month", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/seances/dashboard").param("centerId", CENTRE.toString()).param("year", "abc").param("month", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/seances/dashboard").param("centerId", "pas-un-uuid").param("year", "2026").param("month", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/seances/dashboard/export").param("year", "2026").param("month", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void un_mois_hors_bornes_ou_un_type_inconnu_est_une_requete_invalide() throws Exception {
        Mockito.when(tableau.executer(CENTRE, 2026, 13)).thenThrow(new SaisieInvalide("Mois du tableau de bord hors bornes"));
        Mockito.when(detail.executer(CENTRE, 2026, 1, "x")).thenThrow(new SaisieInvalide("kind must be 'presence' or 'absence'"));

        mvc.perform(get("/api/v1/seances/dashboard").param("centerId", CENTRE.toString()).param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/seances/dashboard/details").param("centerId", CENTRE.toString()).param("year", "2026").param("month", "1")
                        .param("kind", "x"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void un_centre_etranger_recoit_403_et_un_profil_sans_droit_recoit_403_avant_toute_erreur_de_lecture() throws Exception {
        Mockito.when(tableau.executer(CENTRE, 2026, 13)).thenThrow(new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session"));
        mvc.perform(get("/api/v1/seances/dashboard").param("centerId", CENTRE.toString()).param("year", "2026").param("month", "13"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        avecLeRole("DIRECTION");
        mvc.perform(get("/api/v1/seances/dashboard").param("centerId", "pas-un-uuid").param("year", "2026").param("month", "1"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/seances/dashboard/export").param("year", "2026").param("month", "1"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
