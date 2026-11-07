package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesSerologiesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.IsolementDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.SerologiesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les refus des sérologies au format commun, par l'advice du module : verrou non obtenu (le port lève
 * {@link EcritureConcurrenteSurUneSerologie}, ce que fait l'adapter JDBC sur le SQLSTATE 55P03) 409 {@code SEROLOGIE_ECRITURE_CONCURRENTE} ;
 * sérologie introuvable 400 (reproduit) ; entrée illisible ou inconnue 400 sans nom de classe interne, et 403 AVANT 400 pour un profil sans
 * droit (KB-MED-47 et 48) ; règle de gestion 422 avec son code ; suppression sans effet 204 et marquée « sans trace » pour l'audit.
 */
class ErreursDesSerologiesTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SEROLOGIE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String URL = "/api/v1/patients/" + PATIENT + "/serologies";

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc controleurSur(Serologies serologies) {
        UniteDeTravail unite = new UniteDeTravail() {
            @Override
            public <T> T executer(Supplier<T> action) {
                return action.get();
            }

            @Override
            public <T> T independante(Supplier<T> action) {
                return action.get();
            }
        };
        var session = (com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante) demande -> new CentreId(CENTRE);
        var horloge = HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC));
        IsolementDesPatients isolement = new IsolementDesPatients() {
            @Override
            public void verrouillerLesPlaces(CentreId centre, PatientId patient) {
            }

            @Override
            public void reevaluer(CentreId centre, PatientId patient) {
            }
        };
        var controleur = new SerologiesControleur(new ConsulterLesSerologiesDuPatient(session, serologies),
                new EnregistrerUneSerologie(session, serologies, isolement, unite, UUID::randomUUID, horloge),
                new CorrigerUneSerologie(session, serologies, isolement, unite, horloge), new SupprimerUneSerologie(session, serologies, unite));
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    private static Serologies verrouillee() {
        return new SerologiesEnMemoire() {
            @Override
            public Optional<Serologie> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                throw new EcritureConcurrenteSurUneSerologie();
            }

            @Override
            public boolean existeEnVerrouillant(CentreId centre, PatientId patient, MarqueurSerologique marqueur, LocalDate date) {
                throw new EcritureConcurrenteSurUneSerologie();
            }
        };
    }

    @Test
    void une_correction_qui_n_obtient_pas_le_verrou_repond_409_avec_le_code_de_la_serologie() throws Exception {
        profil("ROLE_MEDECIN");
        controleurSur(verrouillee()).perform(put(URL + "/" + SEROLOGIE).contentType(MediaType.APPLICATION_JSON).content("{\"resultat\":\"NEGATIF\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEROLOGIE_ECRITURE_CONCURRENTE"));
    }

    @Test
    void un_enregistrement_qui_n_obtient_pas_le_verrou_du_triplet_repond_409() throws Exception {
        profil("ROLE_MEDECIN");
        controleurSur(verrouillee()).perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\",\"datePrelevement\":\"2026-01-05\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEROLOGIE_ECRITURE_CONCURRENTE"));
    }

    @Test
    void une_serologie_introuvable_a_la_correction_repond_400_bad_request() throws Exception {
        profil("ROLE_MEDECIN");
        controleurSur(new SerologiesEnMemoire()).perform(put(URL + "/" + SEROLOGIE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultat\":\"NEGATIF\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Sérologie introuvable"));
    }

    @Test
    void un_marqueur_ou_un_resultat_inconnu_repond_400_sans_nom_de_classe_interne() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new SerologiesEnMemoire());
        for (String corps : List.of("{\"marqueur\":\"HIV\",\"resultat\":\"NEGATIF\"}", "{\"marqueur\":\"TPHA\",\"resultat\":\"negatif\"}",
                "{\"resultat\":\"NEGATIF\"}", "{\"marqueur\":\"TPHA\"}", "{}")) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.hemodialyse"))));
        }
    }

    @Test
    void un_corps_ou_un_identifiant_illisible_repond_400_pour_un_medecin_et_403_pour_un_profil_sans_droit() throws Exception {
        var mvc = controleurSur(new SerologiesEnMemoire());
        profil("ROLE_MEDECIN");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\",\"titre\":\"abc\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"datePrelevement\":\"2026-02-30\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/patients/pas-un-uuid/serologies")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("page", "x")).andExpect(status().isBadRequest());
        profil("ROLE_INFIRMIER");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/patients/pas-un-uuid/serologies")).andExpect(status().isForbidden());
        profil("ROLE_ADMIN");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        mvc.perform(get(URL).param("page", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void les_regles_de_gestion_repondent_422_avec_leur_code() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new SerologiesEnMemoire());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"marqueur\":\"VIH_AC\",\"resultat\":\"POSITIF\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("SEROLOGIE_CONDUITE_REQUISE"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\",\"datePrelevement\":\"2026-01-11\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("SEROLOGIE_DATE_FUTURE"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\"}")).andExpect(status().isOk());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void une_unite_ou_un_laboratoire_trop_long_repond_400_au_lieu_de_500() throws Exception {
        profil("ROLE_MEDECIN");
        controleurSur(new SerologiesEnMemoire()).perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\",\"unite\":\"" + "u".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void une_suppression_sans_effet_repond_204_et_demande_de_ne_rien_inscrire_au_journal_d_audit() throws Exception {
        profil("ROLE_MEDECIN");
        var sansEffet = controleurSur(new SerologiesEnMemoire()).perform(delete(URL + "/" + SEROLOGIE)).andExpect(status().isNoContent()).andReturn();
        assertThat(sansEffet.getRequest().getAttribute(SerologiesControleur.ATTRIBUT_SANS_TRACE)).isEqualTo(Boolean.TRUE);

        var serologies = new SerologiesEnMemoire();
        var existante = Serologie.enregistrer(SEROLOGIE, new PatientId(PATIENT), new CentreId(CENTRE), MarqueurSerologique.TPHA,
                com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique.NEGATIF, null, null, LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 10), null, null, null, Instant.parse("2026-01-05T10:00:00Z"));
        serologies.ajouter(existante);
        var effectif = controleurSur(serologies).perform(delete(URL + "/" + SEROLOGIE)).andExpect(status().isNoContent()).andReturn();
        assertThat(effectif.getRequest().getAttribute(SerologiesControleur.ATTRIBUT_SANS_TRACE)).isNull();
        assertThat(serologies.toutesDuPatient(new CentreId(CENTRE), new PatientId(PATIENT))).isEmpty();
    }
}
