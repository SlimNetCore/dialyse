package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.AjouterUneDecisionDeRcp;
import com.hemodialyse.backend.contexts.dossiermedical.application.AjouterUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.ChangerLeStatutDuBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesDonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesEtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.application.GenererLesEtapesStandard;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourLeBilanImmunologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourLesNotesDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.BilansPreGreffeEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.DonneursVivantsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.EtapesDeBilanEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
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
import java.time.ZoneOffset;
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
 * Les refus du bilan pré-greffe, des étapes et des donneurs au format commun, par l'advice du module : verrou non obtenu 409 avec le code
 * de l'agrégat ; patient inconnu 404 (KB-MED-85/86) ; étape et donneur introuvables 404 (KB-MED-88) ; entrée illisible ou inconnue 400 sans
 * nom de classe interne (KB-MED-87), 403 AVANT 400 pour un profil sans droit (KB-MED-89) ; transition refusée 422 ; suppression sans effet
 * 204 marquée « sans trace ».
 */
class ErreursDuBilanPreGreffeTest {
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID LIGNE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String BILAN = "/api/v1/patients/" + PATIENT + "/greffe/bilan";
    private static final String ETAPES = "/api/v1/patients/" + PATIENT + "/greffe/etapes";
    private static final String DONNEURS = "/api/v1/patients/" + PATIENT + "/greffe/donneurs";
    private static final UniteDeTravail UNITE = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T independante(Supplier<T> action) {
            return action.get();
        }
    };

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc sur(com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe bilans, EtapesDeBilan etapes,
                               DonneursVivants donneurs) {
        SessionCourante session = demande -> new CentreId(CENTRE);
        var horloge = HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC));
        var patients = (com.hemodialyse.backend.contexts.dossiermedical.application.PatientsDuCentre) (centre, patient) -> !patient.valeur().equals(INCONNU);
        var identifiants = (com.hemodialyse.backend.contexts.dossiermedical.application.GenerateurDIdentifiants) UUID::randomUUID;
        var bilan = new BilanPreGreffeControleur(new ConsulterLeBilanPreGreffe(session, patients, bilans, UNITE, identifiants, horloge),
                new ChangerLeStatutDuBilanPreGreffe(session, patients, bilans, UNITE, identifiants, horloge),
                new MettreAJourLeBilanImmunologique(session, patients, bilans, UNITE, identifiants, horloge),
                new MettreAJourLesNotesDuBilan(session, patients, bilans, UNITE, identifiants, horloge),
                new AjouterUneDecisionDeRcp(session, patients, bilans, UNITE, identifiants, horloge));
        var etape = new EtapesDeBilanControleur(new ConsulterLesEtapesDeBilan(session, etapes),
                new AjouterUneEtapeDeBilan(session, patients, etapes, UNITE, identifiants, horloge),
                new GenererLesEtapesStandard(session, patients, etapes, UNITE, identifiants, horloge),
                new MettreAJourUneEtapeDeBilan(session, etapes, UNITE, horloge), new SupprimerUneEtapeDeBilan(session, etapes, UNITE));
        var donneur = new DonneursVivantsControleur(new ConsulterLesDonneursVivants(session, donneurs),
                new EnregistrerUnDonneurVivant(session, patients, donneurs, UNITE, identifiants, horloge),
                new MettreAJourUnDonneurVivant(session, donneurs, UNITE, horloge), new SupprimerUnDonneurVivant(session, donneurs, UNITE));
        return MockMvcBuilders.standaloneSetup(bilan, etape, donneur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    private static MockMvc normal() {
        return sur(new BilansPreGreffeEnMemoire(), new EtapesDeBilanEnMemoire(), new DonneursVivantsEnMemoire());
    }

    @Test
    void une_ecriture_qui_n_obtient_pas_le_verrou_repond_409_avec_le_code_de_l_agregat() throws Exception {
        profil("ROLE_MEDECIN");
        var bilans = new BilansPreGreffeEnMemoire() {
            @Override
            public Optional<BilanPreGreffe> trouverEnVerrouillant(CentreId centre, PatientId patient) {
                throw new EcritureConcurrenteSurUnBilanPreGreffe();
            }
        };
        var etapes = new EtapesDeBilanEnMemoire() {
            @Override
            public Optional<EtapeDeBilan> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                throw new EcritureConcurrenteSurUneEtapeDeBilan();
            }
        };
        var donneurs = new DonneursVivantsEnMemoire() {
            @Override
            public Optional<DonneurVivant> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                throw new EcritureConcurrenteSurUnDonneurVivant();
            }
        };
        var mvc = sur(bilans, etapes, donneurs);
        mvc.perform(put(BILAN + "/statut").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"BILAN_EN_COURS\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILAN_PRE_GREFFE_ECRITURE_CONCURRENTE"));
        mvc.perform(put(ETAPES + "/" + LIGNE).contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"A_FAIRE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_BILAN_ECRITURE_CONCURRENTE"));
        mvc.perform(put(DONNEURS + "/" + LIGNE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"x\",\"lienParente\":\"PARENT\",\"statutBilan\":\"CANDIDAT\",\"crossmatchResultat\":\"NON_FAIT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DONNEUR_VIVANT_ECRITURE_CONCURRENTE"));
    }

    @Test
    void un_patient_inconnu_repond_404_sans_ouvrir_de_bilan() throws Exception {
        profil("ROLE_MEDECIN");
        var bilans = new BilansPreGreffeEnMemoire();
        var mvc = sur(bilans, new EtapesDeBilanEnMemoire(), new DonneursVivantsEnMemoire());
        mvc.perform(get("/api/v1/patients/" + INCONNU + "/greffe/bilan")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
        mvc.perform(post("/api/v1/patients/" + INCONNU + "/greffe/donneurs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"x\",\"lienParente\":\"PARENT\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/patients/" + INCONNU + "/greffe/etapes/generer-standard")).andExpect(status().isNotFound());
        assertThat(bilans.nombreDuPatient(new CentreId(CENTRE), new PatientId(INCONNU))).isZero();
    }

    @Test
    void une_etape_ou_un_donneur_introuvable_repond_404_avec_son_code() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = normal();
        mvc.perform(put(ETAPES + "/" + LIGNE).contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"A_FAIRE\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ETAPE_BILAN_INTROUVABLE"));
        mvc.perform(put(DONNEURS + "/" + LIGNE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"x\",\"lienParente\":\"PARENT\",\"statutBilan\":\"CANDIDAT\",\"crossmatchResultat\":\"NON_FAIT\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DONNEUR_VIVANT_INTROUVABLE"));
    }

    @Test
    void une_valeur_inconnue_ou_un_corps_illisible_repond_400_sans_nom_de_classe_interne() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = normal();
        for (String corps : new String[]{"{}", "{\"statut\":\"BIDON\"}", "{\"statut\":\"bilan_en_cours\"}", "{\"statut\":3}", "pas du json", ""}) {
            mvc.perform(put(BILAN + "/statut").contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.hemodialyse"))))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("No enum constant"))));
        }
        mvc.perform(get("/api/v1/patients/pas-un-uuid/greffe/bilan")).andExpect(status().isBadRequest());
    }

    @Test
    void les_bornes_de_colonne_repondent_400_et_les_regles_de_gestion_422_avec_leur_code() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = normal();
        mvc.perform(put(BILAN + "/immunologique").contentType(MediaType.APPLICATION_JSON).content("{\"groupeSanguinConfirme\":\"ABCDEF\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put(BILAN + "/statut").contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"INSCRIT_LISTE_ATTENTE\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BILAN_GREFFE_TRANSITION_INVALIDE"));
        mvc.perform(post(BILAN + "/decisions-rcp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateReunion\":\"2026-11-08\",\"avis\":\"FAVORABLE\"}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DECISION_RCP_DATE_FUTURE"));
        mvc.perform(post(ETAPES).contentType(MediaType.APPLICATION_JSON).content("{\"categorie\":\"ORL\",\"libelle\":\" \"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ETAPE_GREFFE_CHAMPS_REQUIS"));
        mvc.perform(post(DONNEURS).contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\" \",\"lienParente\":\"PARENT\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("DONNEUR_VIVANT_CHAMPS_REQUIS"));
    }

    @Test
    void un_profil_sans_droit_recoit_403_avant_toute_lecture_du_corps() throws Exception {
        var mvc = normal();
        profil("ROLE_INFIRMIER");
        mvc.perform(put(BILAN + "/statut").contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(DONNEURS).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        profil("ROLE_ADMIN");
        mvc.perform(put(ETAPES + "/" + LIGNE).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        profil("ROLE_MEDECIN");
        mvc.perform(put(BILAN + "/statut").contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isBadRequest());
    }

    @Test
    void une_suppression_sans_effet_repond_204_et_demande_de_ne_rien_inscrire_au_journal_d_audit() throws Exception {
        profil("ROLE_MEDECIN");
        var sansEffet = normal().perform(delete(ETAPES + "/" + LIGNE)).andExpect(status().isNoContent()).andReturn();
        assertThat(sansEffet.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isEqualTo(Boolean.TRUE);
        var sansDonneur = normal().perform(delete(DONNEURS + "/" + LIGNE)).andExpect(status().isNoContent()).andReturn();
        assertThat(sansDonneur.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isEqualTo(Boolean.TRUE);
        var etapes = new EtapesDeBilanEnMemoire();
        etapes.ajouter(EtapeDeBilan.ajouter(LIGNE, new PatientId(PATIENT), new CentreId(CENTRE),
                com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape.ORL, "x", Instant.parse("2026-11-07T09:00:00Z")));
        var effectif = sur(new BilansPreGreffeEnMemoire(), etapes, new DonneursVivantsEnMemoire()).perform(delete(ETAPES + "/" + LIGNE))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(effectif.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isNull();
        assertThat(etapes.duPatient(new CentreId(CENTRE), new PatientId(PATIENT))).isEmpty();
    }
}
