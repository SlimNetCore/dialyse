package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.application.AjouterUnConsommable;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLesRaccourcis;
import com.hemodialyse.backend.contexts.seance.application.ModifierLaQuantiteDUnConsommable;
import com.hemodialyse.backend.contexts.seance.application.RemplacerLesRaccourcis;
import com.hemodialyse.backend.contexts.seance.application.RetirerUnConsommable;
import com.hemodialyse.backend.contexts.seance.application.ConsulterUneSeance;
import com.hemodialyse.backend.contexts.seance.application.CreerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.application.ImposerUnForfait;
import com.hemodialyse.backend.contexts.seance.application.ListerLesSeances;
import com.hemodialyse.backend.contexts.seance.application.ScannerUnPatient;
import com.hemodialyse.backend.contexts.seance.application.SeanceEnregistree;
import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.MotifDeRefus;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceDuJourAmbigue;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adapter entrant : formes JSON identiques au legacy, table des refus (statut + code) et garde « 403 avant 400 ». Les cas d'usage sont
 * simulés : leurs règles sont prouvées dans les tests de l'application.
 */
class SeancesControleurTest {

    private static final UUID CENTRE = new UUID(0, 0xA);
    private static final UUID PATIENT = new UUID(1, 1);
    private static final UUID SEANCE = new UUID(2, 2);
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    private CreerUneSeance creer;
    private ScannerUnPatient scanner;
    private ConsulterUneSeance consulter;
    private ListerLesSeances lister;
    private MockMvc mvc;

    @BeforeEach
    void monter() {
        creer = Mockito.mock(CreerUneSeance.class);
        scanner = Mockito.mock(ScannerUnPatient.class);
        consulter = Mockito.mock(ConsulterUneSeance.class);
        lister = Mockito.mock(ListerLesSeances.class);
        valider = Mockito.mock(com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance.class);
        signer = Mockito.mock(com.hemodialyse.backend.contexts.seance.application.SignerUneSeance.class);
        deverrouiller = Mockito.mock(com.hemodialyse.backend.contexts.seance.application.DeverrouillerUneSeance.class);
        ajouter = Mockito.mock(AjouterUnConsommable.class);
        modifier = Mockito.mock(ModifierLaQuantiteDUnConsommable.class);
        retirer = Mockito.mock(RetirerUnConsommable.class);
        consulterRaccourcis = Mockito.mock(ConsulterLesRaccourcis.class);
        remplacerRaccourcis = Mockito.mock(RemplacerLesRaccourcis.class);
        imposer = Mockito.mock(ImposerUnForfait.class);
        changerLeJour = Mockito.mock(com.hemodialyse.backend.contexts.seance.application.ChangerLeJourDUneSeance.class);
        supprimerUneSeance = Mockito.mock(com.hemodialyse.backend.contexts.seance.application.SupprimerUneSeance.class);
        mvc = MockMvcBuilders.standaloneSetup(new SeancesControleur(creer, scanner, consulter, lister, valider, signer, deverrouiller,
                        ajouter, modifier, retirer, consulterRaccourcis, remplacerRaccourcis, imposer, changerLeJour, supprimerUneSeance))
                .setControllerAdvice(new ErreursDeSeanceAdvice()).build();
        avecLeRole("INFIRMIER");
    }

    private ImposerUnForfait imposer;
    private com.hemodialyse.backend.contexts.seance.application.ChangerLeJourDUneSeance changerLeJour;
    private com.hemodialyse.backend.contexts.seance.application.SupprimerUneSeance supprimerUneSeance;
    private static final UUID FORFAIT = new UUID(6, 7);

    // ---- SE6 : changement de jour et suppression

    private Seance uneSeance() {
        return Seance.restituer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR.plusDays(2),
                com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance.SIGNEE, Instant.parse("2026-06-15T08:00:00Z"), null, null);
    }

    @Test
    void le_changement_de_jour_repond_avec_l_identifiant_le_statut_et_le_nouveau_jour() throws Exception {
        avecLeRole("ADMIN");
        Mockito.when(changerLeJour.executer(Mockito.eq(CENTRE), Mockito.eq(SEANCE), Mockito.any())).thenReturn(uneSeance());

        mvc.perform(put("/api/v1/seances/" + SEANCE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"dateSeance\":\"2026-06-17\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(SEANCE.toString()))
                .andExpect(jsonPath("$.status").value("SIGNEE")).andExpect(jsonPath("$.dateSeance").value("2026-06-17"));
    }

    @Test
    void le_jour_est_lu_apres_le_centre_et_chaque_forme_illisible_est_une_saisie_invalide() {
        for (Object illisible : new Object[]{20261231, "31/12/2026", "demain", true, java.util.Map.of("a", 1)}) {
            assertThatThrownBy(() -> new com.hemodialyse.backend.contexts.seance.interfaces.http.dto.ChangementDeJourDemande(CENTRE, illisible).jour())
                    .isInstanceOf(com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide.class);
        }
    }

    @Test
    void les_formes_lisibles_du_jour_sont_celles_du_legacy() {
        var lire = (java.util.function.Function<Object, LocalDate>) o ->
                new com.hemodialyse.backend.contexts.seance.interfaces.http.dto.ChangementDeJourDemande(CENTRE, o).jour();
        org.assertj.core.api.Assertions.assertThat(lire.apply(null)).isNull();
        org.assertj.core.api.Assertions.assertThat(lire.apply("")).isNull();
        org.assertj.core.api.Assertions.assertThat(lire.apply("2026-06-17")).isEqualTo(LocalDate.of(2026, 6, 17));
        org.assertj.core.api.Assertions.assertThat(lire.apply("2026-06-17T10:00:00")).isEqualTo(LocalDate.of(2026, 6, 17));
        org.assertj.core.api.Assertions.assertThat(lire.apply("9999-12-31")).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    void le_changement_de_jour_sans_centre_est_une_erreur_de_validation_et_un_corps_illisible_un_400() throws Exception {
        avecLeRole("ADMIN");
        mvc.perform(put("/api/v1/seances/" + SEANCE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/api/v1/seances/" + SEANCE).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put("/api/v1/seances/pas-un-identifiant").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void un_profil_sans_droit_recoit_403_avant_tout_400_sur_les_deux_routes() throws Exception {
        avecLeRole("INFIRMIER");
        mvc.perform(put("/api/v1/seances/" + SEANCE).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/seances/" + SEANCE + "?centerId=" + CENTRE).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isForbidden());
        Mockito.verifyNoInteractions(changerLeJour, supprimerUneSeance);
    }

    @Test
    void la_suppression_repond_204_sans_corps_et_transmet_le_motif_tel_quel() throws Exception {
        avecLeRole("ADMIN");

        mvc.perform(delete("/api/v1/seances/" + SEANCE + "?centerId=" + CENTRE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"  Doublon  \"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        Mockito.verify(supprimerUneSeance).executer(CENTRE, SEANCE, "  Doublon  ");
    }

    @Test
    void la_suppression_refuse_un_centre_absent_un_corps_absent_ou_illisible_et_un_motif_en_tableau_par_un_400() throws Exception {
        avecLeRole("ADMIN");
        mvc.perform(delete("/api/v1/seances/" + SEANCE).contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Doublon\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(delete("/api/v1/seances/" + SEANCE + "?centerId=" + CENTRE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(delete("/api/v1/seances/" + SEANCE + "?centerId=" + CENTRE).contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/seances/" + SEANCE + "?centerId=" + CENTRE).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/seances/" + SEANCE + "?centerId=" + CENTRE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":[\"a\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/seances/" + SEANCE + "?centerId=pas-un-identifiant").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"Doublon\"}"))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(supprimerUneSeance);
    }

    @Test
    void un_motif_numerique_est_lu_comme_du_texte_et_un_champ_inconnu_est_ignore() throws Exception {
        avecLeRole("ADMIN");

        mvc.perform(delete("/api/v1/seances/" + SEANCE + "?centerId=" + CENTRE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":12345,\"autre\":true}"))
                .andExpect(status().isNoContent());

        Mockito.verify(supprimerUneSeance).executer(CENTRE, SEANCE, "12345");
    }

    private String corpsDImposition() {
        return "{\"centerId\":\"" + CENTRE + "\",\"forfaitId\":\"" + FORFAIT + "\",\"userId\":\"compta-01\"}";
    }

    @Test
    void l_imposition_d_un_forfait_repond_avec_les_champs_du_legacy_et_le_forfait_courant_a_sept_cles() throws Exception {
        avecLeRole("ADMIN");
        Seance seance = Seance.restituer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR,
                com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance.VALIDEE, Instant.parse("2026-06-15T08:00:00Z"), null, null);
        var courant = new ForfaitsDeSeance.ForfaitCourant(FORFAIT, "F1", "Forfait 1", new BigDecimal("250.50"), null,
                java.time.OffsetDateTime.parse("2026-06-15T10:00:00Z"), "compta-01", true);
        Mockito.when(imposer.executer(CENTRE, SEANCE, FORFAIT, "compta-01")).thenReturn(new ImposerUnForfait.Resultat(seance, Optional.of(courant)));

        mvc.perform(put("/api/v1/seances/" + SEANCE + "/forfait").contentType(MediaType.APPLICATION_JSON).content(corpsDImposition()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(SEANCE.toString()))
                .andExpect(jsonPath("$.status").value("VALIDEE")).andExpect(jsonPath("$.dateSeance").value("2026-06-15"))
                .andExpect(jsonPath("$.forfait.id").value(FORFAIT.toString())).andExpect(jsonPath("$.forfait.code").value("F1"))
                .andExpect(jsonPath("$.forfait.nom").value("Forfait 1")).andExpect(jsonPath("$.forfait.prix").value(250.50))
                .andExpect(jsonPath("$.forfait.nombreSeances").doesNotExist()).andExpect(jsonPath("$.forfait.updatedBy").value("compta-01"))
                .andExpect(jsonPath("$.forfait.updatedAt").exists());
    }

    @Test
    void les_erreurs_de_forme_de_l_imposition_sont_400_et_jamais_500_avec_403_avant_400() throws Exception {
        String route = "/api/v1/seances/" + SEANCE + "/forfait";
        avecLeRole("ADMIN");
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"forfaitId\":\"" + FORFAIT + "\",\"userId\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"abc\",\"forfaitId\":\"" + FORFAIT + "\",\"userId\":\"c\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isBadRequest());
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/seances/abc/forfait").contentType(MediaType.APPLICATION_JSON).content(corpsDImposition()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        avecLeRole("INFIRMIER");
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        Mockito.verifyNoInteractions(imposer);
    }

    @Test
    void les_refus_de_l_imposition_gardent_leur_statut_et_leur_code() throws Exception {
        avecLeRole("ADMIN");
        String route = "/api/v1/seances/" + SEANCE + "/forfait";
        Mockito.doThrow(new SeanceRefusee(MotifDeRefus.SEANCE_FACTUREE, "La seance facturee ne peut plus etre modifiee"))
                .when(imposer).executer(any(), any(), any(), any());
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content(corpsDImposition()))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("SEANCE_FACTUREE_NON_MODIFIABLE"));
        Mockito.doThrow(new SeanceIntrouvable()).when(imposer).executer(any(), any(), any(), any());
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content(corpsDImposition()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Seance introuvable"));
        Mockito.doThrow(new SaisieInvalide("Forfait introuvable pour le centre actif")).when(imposer).executer(any(), any(), any(), any());
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content(corpsDImposition()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Forfait introuvable pour le centre actif"));
        Mockito.doThrow(new EcritureConcurrente()).when(imposer).executer(any(), any(), any(), any());
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content(corpsDImposition()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ECRITURE_CONCURRENTE"));
        Mockito.doThrow(new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session"))
                .when(imposer).executer(any(), any(), any(), any());
        mvc.perform(put(route).contentType(MediaType.APPLICATION_JSON).content(corpsDImposition()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private AjouterUnConsommable ajouter;
    private ModifierLaQuantiteDUnConsommable modifier;
    private RetirerUnConsommable retirer;
    private ConsulterLesRaccourcis consulterRaccourcis;
    private RemplacerLesRaccourcis remplacerRaccourcis;
    private static final UUID ARTICLE = new UUID(6, 1);

    @Test
    void les_consommables_repondent_avec_les_champs_du_legacy_et_la_quantite_telle_que_saisie() throws Exception {
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/consommables").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"articleId\":\"" + ARTICLE + "\",\"quantite\":1.5,\"inconnue\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.added").value(true))
                .andExpect(jsonPath("$.articleId").value(ARTICLE.toString())).andExpect(jsonPath("$.quantite").value(1.5));
        Mockito.verify(ajouter).executer(CENTRE, SEANCE, ARTICLE, new BigDecimal("1.5"));

        mvc.perform(put("/api/v1/seances/" + SEANCE + "/consommables/" + ARTICLE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"userId\":\"inf-01\",\"quantite\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(true)).andExpect(jsonPath("$.quantite").value(3));
        Mockito.verify(modifier).executer(CENTRE, SEANCE, ARTICLE, new BigDecimal("3"), "inf-01");

        mvc.perform(delete("/api/v1/seances/" + SEANCE + "/consommables/" + ARTICLE).param("centerId", CENTRE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.removed").value(true))
                .andExpect(jsonPath("$.articleId").value(ARTICLE.toString()));
        Mockito.verify(retirer).executer(CENTRE, SEANCE, ARTICLE, "system");
    }

    @Test
    void les_erreurs_de_forme_des_consommables_sont_400_et_jamais_500_avec_403_avant_400() throws Exception {
        String route = "/api/v1/seances/" + SEANCE + "/consommables";
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"quantite\":\"abc\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"articleId\":\"pas-un-uuid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(put(route + "/pas-un-uuid").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(delete(route + "/" + ARTICLE)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parametre requis absent : centerId"));
        mvc.perform(delete(route + "/" + ARTICLE).param("centerId", "illisible")).andExpect(status().isBadRequest());

        avecLeRole("MEDECIN");
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"quantite\":\"abc\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(delete(route + "/" + ARTICLE)).andExpect(status().isForbidden());
    }

    @Test
    void les_refus_du_domaine_et_du_stock_gardent_leur_statut_et_leur_code() throws Exception {
        Mockito.doThrow(new SeanceRefusee(MotifDeRefus.CONSOMMABLES_AVANT_VALIDATION, "La seance doit etre validee avant d'ajouter un consommable"))
                .when(ajouter).executer(any(), any(), any(), any());
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/consommables").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"articleId\":\"" + ARTICLE + "\",\"quantite\":1}"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        Mockito.doThrow(new com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee("PIECE_DEJA_RAPPROCHEE", "rapprochée"))
                .when(modifier).executer(any(), any(), any(), any(), any());
        mvc.perform(put("/api/v1/seances/" + SEANCE + "/consommables/" + ARTICLE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"quantite\":1}"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("PIECE_DEJA_RAPPROCHEE"));
        Mockito.doThrow(new PatientIntrouvable()).when(retirer).executer(any(), any(), any(), any());
        mvc.perform(delete("/api/v1/seances/" + SEANCE + "/consommables/" + ARTICLE).param("centerId", CENTRE.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
    }

    @Test
    void les_raccourcis_se_lisent_dans_l_ordre_et_se_remplacent_par_l_administrateur_seul() throws Exception {
        var liste = com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables.restituer(new CentreId(CENTRE),
                List.of(new com.hemodialyse.backend.contexts.seance.domain.ArticleId(ARTICLE)));
        Mockito.when(consulterRaccourcis.executer(any())).thenReturn(liste);
        mvc.perform(get("/api/v1/seances/raccourcis-consommables").param("centerId", CENTRE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(ARTICLE.toString()));

        avecLeRole("SECRETAIRE");
        mvc.perform(get("/api/v1/seances/raccourcis-consommables")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/seances/raccourcis-consommables").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isForbidden());

        avecLeRole("ADMIN");
        Mockito.when(remplacerRaccourcis.executer(null, List.of(ARTICLE))).thenReturn(liste);
        mvc.perform(put("/api/v1/seances/raccourcis-consommables").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[\"" + ARTICLE + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(ARTICLE.toString()));
        mvc.perform(put("/api/v1/seances/raccourcis-consommables").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/seances/raccourcis-consommables").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    private com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance valider;
    private com.hemodialyse.backend.contexts.seance.application.SignerUneSeance signer;
    private com.hemodialyse.backend.contexts.seance.application.DeverrouillerUneSeance deverrouiller;

    @Test
    void la_validation_repond_avec_les_quatre_champs_du_legacy() throws Exception {
        Seance validee = seance();
        validee.validerParLInfirmier(Instant.parse("2026-06-15T09:00:00Z"), new com.hemodialyse.backend.contexts.seance.domain.Signataire("inf-01"));
        Mockito.when(valider.executer(eq(CENTRE), eq(SEANCE), eq("inf-01"), any()))
                .thenReturn(new com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance.Validation(validee, fiche()));
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/valider").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"userId\":\"inf-01\",\"consommations\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEANCE.toString()))
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.validatedAt").exists())
                .andExpect(jsonPath("$.signedByInfirmierAt").exists());
    }

    @Test
    void la_signature_et_le_deverrouillage_repondent_avec_les_champs_du_legacy() throws Exception {
        Seance signee = Seance.restituer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR,
                com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance.VALIDEE, null, null, null);
        signee.signerParLeMedecin(Instant.parse("2026-06-15T09:00:00Z"), new com.hemodialyse.backend.contexts.seance.domain.Signataire("med"));
        Mockito.when(signer.executer(eq(CENTRE), eq(SEANCE), eq("med"))).thenReturn(signee);
        avecLeRole("MEDECIN");
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/signer-medecin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"userId\":\"med\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNEE"))
                .andExpect(jsonPath("$.signedByMedecinAt").exists());

        Seance deverrouillee = seance();
        deverrouillee.deverrouillerPourRegularisation(Instant.parse("2026-06-20T09:00:00Z"),
                new com.hemodialyse.backend.contexts.seance.domain.Signataire("u"), JOUR.plusDays(5));
        Mockito.when(deverrouiller.executer(CENTRE, SEANCE)).thenReturn(deverrouillee);
        avecLeRole("ADMIN");
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/deverrouiller-regularisation").param("centerId", CENTRE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREE"))
                .andExpect(jsonPath("$.regularisationDeverrouilleeAt").exists());
    }

    @Test
    void les_routes_de_la_vie_de_la_seance_refusent_en_403_avant_400_et_en_400_sans_centre() throws Exception {
        avecLeRole("SECRETAIRE");
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/valider").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/deverrouiller-regularisation"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        avecLeRole("ADMIN");
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/deverrouiller-regularisation"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/valider").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Centre requis"));
        mvc.perform(post("/api/v1/seances/pas-un-uuid/valider").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_refus_du_stock_repond_422_avec_le_code_du_stock() throws Exception {
        Mockito.when(valider.executer(eq(CENTRE), eq(SEANCE), any(), any())).thenThrow(
                new com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee("PIECE_DEJA_RAPPROCHEE", "pièce rapprochée"));
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/valider").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\"}"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("PIECE_DEJA_RAPPROCHEE"));
    }

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    private static void avecLeRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("marie", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static Seance seance() {
        return Seance.creer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR, Instant.parse("2026-06-15T08:00:00Z"));
    }

    private static FicheDuPatient fiche() {
        return new FicheDuPatient(new PatientId(PATIENT), "P-1", "Nom", "Prenom", "F", JOUR.minusYears(40), "123", "A+", "0550", null, null,
                null, new UUID(3, 3), "G1", "Fresenius", "ACTIF");
    }

    @Test
    void la_creation_repond_avec_les_champs_du_legacy() throws Exception {
        Mockito.when(creer.executer(eq(CENTRE), eq(PATIENT), any())).thenReturn(new SeanceEnregistree(seance(), fiche()));
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"patientId\":\"" + PATIENT + "\",\"dateSeance\":\"2026-06-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEANCE.toString()))
                .andExpect(jsonPath("$.status").value("CREE"))
                .andExpect(jsonPath("$.horsPlanning").value(false))
                .andExpect(jsonPath("$.motifHorsPlanning").doesNotExist())
                .andExpect(jsonPath("$.generateurNom").value("G1"))
                .andExpect(jsonPath("$.generateurMarque").value("Fresenius"))
                .andExpect(jsonPath("$.generateurEtat").value("ACTIF"));
    }

    @Test
    void le_scan_ajoute_l_identite_et_les_drapeaux() throws Exception {
        Mockito.when(scanner.executer(eq(CENTRE), eq("P-1"), any(), any()))
                .thenReturn(new ScannerUnPatient.ResultatDuScan(seance(), fiche(), true, true, false));
        mvc.perform(post("/api/v1/seances/scan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"qrCode\":\"P-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientNom").value("Nom"))
                .andExpect(jsonPath("$.patientPrenom").value("Prenom"))
                .andExpect(jsonPath("$.patientCode").value("P-1"))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.validatedNow").value(true))
                .andExpect(jsonPath("$.alreadyValidated").value(false));
    }

    @Test
    void le_detail_porte_les_sept_blocs_du_legacy() throws Exception {
        var ligne = new com.hemodialyse.backend.contexts.seance.application.ConsommationsDeLaSeance.Ligne(new UUID(7, 7), "ART", "Aiguille",
                "u", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN);
        Mockito.when(consulter.executer(CENTRE, SEANCE)).thenReturn(new ConsulterUneSeance.DetailDeSeance(seance(), fiche(), Optional.empty(),
                Optional.empty(), List.of(ligne), BigDecimal.TEN, Optional.empty()));
        avecLeRole("MEDECIN");
        mvc.perform(get("/api/v1/seances/" + SEANCE).param("centerId", CENTRE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seance.status").value("CREE"))
                .andExpect(jsonPath("$.seance.precisionHorsPlanning").doesNotExist())
                .andExpect(jsonPath("$.patient.codePatient").value("P-1"))
                .andExpect(jsonPath("$.patient.numeroAssurance").value("123"))
                .andExpect(jsonPath("$.patient.telMobile").value("0550"))
                .andExpect(jsonPath("$.consommables[0].articleCode").value("ART"))
                .andExpect(jsonPath("$.consommablesTotalValorise").value(10))
                .andExpect(jsonPath("$.paramedical").doesNotExist())
                .andExpect(jsonPath("$.medical").doesNotExist())
                .andExpect(jsonPath("$.forfait").doesNotExist());
    }

    @Test
    void la_liste_porte_les_lignes_le_forfait_et_la_pagination() throws Exception {
        var ligne = new HistoriqueDesSeances.LigneDeSeance(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), "P-1", "Nom",
                "Prenom", JOUR, com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance.VALIDEE, null, null, null, null, null, false, null);
        var forfait = new ForfaitsDeSeance.ForfaitCourant(new UUID(8, 8), "F1", "Forfait", BigDecimal.valueOf(100), 0, null, null, false);
        Mockito.when(lister.executer(any())).thenReturn(PagedResult.of(
                List.of(new ListerLesSeances.LigneDeLHistorique(ligne, Optional.of(forfait))), 1L, 0, 20));
        avecLeRole("SECRETAIRE");
        mvc.perform(get("/api/v1/seances").param("centerId", CENTRE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].status").value("VALIDEE"))
                .andExpect(jsonPath("$.items[0].patientCode").value("P-1"))
                .andExpect(jsonPath("$.items[0].forfait.code").value("F1"))
                .andExpect(jsonPath("$.items[0].forfait.nombreSeances").value(0))
                .andExpect(jsonPath("$.items[0].forfait.updatedAt").doesNotExist());
    }

    @Test
    void les_refus_ont_le_statut_et_le_code_decides() throws Exception {
        refus(new SeanceRefusee(MotifDeRefus.CENTRE_FERME, "Centre fermé"), 422, "SEANCE_CENTRE_FERME");
        refus(new SeanceRefusee(MotifDeRefus.PRISE_EN_CHARGE_ABSENTE, "Pas de prise en charge"), 422, "BUSINESS_RULE_VIOLATION");
        refus(new SaisieInvalide("Patient requis"), 400, "BAD_REQUEST");
        refus(new SeanceIntrouvable(), 400, "BAD_REQUEST");
        refus(new PatientIntrouvable(), 404, "PATIENT_INTROUVABLE");
        refus(new SeanceDuJourAmbigue(), 409, "SEANCE_DU_JOUR_AMBIGUE");
        refus(new EcritureConcurrente(), 409, "ECRITURE_CONCURRENTE");
        refus(new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session"), 403, "ACCESS_DENIED");
    }

    private void refus(RuntimeException erreur, int statut, String code) throws Exception {
        Mockito.doThrow(erreur).when(creer).executer(any(), any(), any());
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"patientId\":\"" + PATIENT + "\"}"))
                .andExpect(status().is(statut))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void un_corps_illisible_est_un_400_pour_un_profil_autorise() throws Exception {
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void une_creation_sans_centre_dans_le_corps_est_un_400_sans_appeler_le_cas_d_usage() throws Exception {
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":\"" + PATIENT + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        Mockito.verify(creer, Mockito.never()).executer(any(), any(), any());
    }

    @Test
    void un_corps_illisible_est_un_403_pour_un_profil_sans_droit_sur_la_route() throws Exception {
        avecLeRole("SECRETAIRE");
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void un_scan_sans_qr_est_un_400_de_validation_pour_un_profil_autorise_et_un_403_sinon() throws Exception {
        String corps = "{\"centerId\":\"" + CENTRE + "\",\"qrCode\":\" \"}";
        mvc.perform(post("/api/v1/seances/scan").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        avecLeRole("MEDECIN");
        mvc.perform(post("/api/v1/seances/scan").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        Mockito.verify(scanner, Mockito.never()).executer(any(), any(), any(), any());
        Mockito.verify(lister, Mockito.never()).executer(any());
        Mockito.verify(consulter, Mockito.never()).executer(any(), any());
    }
}
