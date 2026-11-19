package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.consultation.ConsulterLesFactures;
import com.hemodialyse.backend.contexts.facturation.application.consultation.SeancesDeLaFacture.SeanceRattachee;
import com.hemodialyse.backend.contexts.facturation.doubles.ListeDesFacturesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adapter entrant de la consultation des factures (RG-FAC-046 à 048) : contrat HTTP de {@code contract.md} (chemins, défauts page=0/size=20,
 * noms des champs JSON), refus 403 (rôle autre qu'ADMIN, centre autre que celui de la session, 403 avant 400), 400 avec un {@code code}
 * stable, 404 sans corps. Le {@code @PreAuthorize} est appliqué par le même intercepteur que la sécurité de méthode de l'application.
 */
class FacturesControleurTest {

    private static final UUID CENTRE = UUID.fromString("99995900-0000-0000-0000-0000000000a1");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99995900-0000-0000-0000-0000000000b1");
    private static final UUID CNAS = UUID.fromString("99995900-0000-0000-0000-000000000c01");
    private static final UUID F1 = UUID.fromString("99995900-0000-0000-0000-0000000f0001");
    private static final UUID F2 = UUID.fromString("99995900-0000-0000-0000-0000000f0002");
    private static final UUID F3 = UUID.fromString("99995900-0000-0000-0000-0000000f0003");
    private static final UUID FB = UUID.fromString("99995900-0000-0000-0000-0000000f00b1");
    private static final UUID PATIENT = UUID.fromString("99995900-0000-0000-0000-00000000aa01");
    private static final UUID HD = UUID.fromString("99995900-0000-0000-0000-00000000f0d1");
    private static final UUID S1 = UUID.fromString("99995900-0000-0000-0000-000000005e01");
    private static final UUID S2 = UUID.fromString("99995900-0000-0000-0000-000000005e02");
    /** Mois courant au fuseau d'Alger : novembre 2026. */
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC);

    private MockMvc mvc;

    /** Session d'un utilisateur du centre {@link #CENTRE} : un autre centre demandé est refusé comme par l'ACL réelle. */
    private static final class SessionDuCentre implements SessionCourante {
        @Override
        public CentreId centre(UUID centreDemande) {
            if (centreDemande != null && !centreDemande.equals(CENTRE)) {
                throw new AccessDeniedException("Accès refusé : le centre demandé ne correspond pas à la session");
            }
            return new CentreId(CENTRE);
        }

        @Override
        public UUID utilisateur() {
            return null;
        }

        @Override
        public Collection<String> roles() {
            return List.of("ROLE_ADMIN");
        }
    }

    /** Le refus d'accès au format du gestionnaire commun de la plateforme ({@code ApiExceptionHandler}). */
    @RestControllerAdvice
    static class RefusDAccesCommun {
        @ExceptionHandler(AccessDeniedException.class)
        ProblemDetail refus(AccessDeniedException erreur) {
            ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
            probleme.setProperty("code", "ACCESS_DENIED");
            return probleme;
        }
    }

    @BeforeEach
    void monter() {
        ListeDesFacturesEnMemoire memoire = new ListeDesFacturesEnMemoire();
        CentreId centre = new CentreId(CENTRE);
        memoire.poserLaFacture(centre, F1, "FAC-2026-0001", LocalDate.of(2026, 11, 5), PATIENT, "111", "BENALI", "Karim", CNAS, "CNAS",
                "9", "16000", "1440", "17440");
        memoire.poserLaFacture(centre, F2, "FAC-2026-0002", LocalDate.of(2026, 11, 6), UUID.randomUUID(), "222", "amrani", "Lina", null,
                "Non renseignee", "0", "8000", "0", "8000");
        memoire.poserLaFacture(centre, F3, "FAC-2026-0003", LocalDate.of(2026, 10, 20), UUID.randomUUID(), "333", "Chikhi", "Nadia", null,
                "Non renseignee", "0", "4000", "0", "4000");
        memoire.poserLaFacture(new CentreId(AUTRE_CENTRE), FB, "FAC-B-0001", LocalDate.of(2026, 11, 6), "999");
        memoire.poserLaLigne(centre, F1, UUID.randomUUID(), new LigneFacture(HD, "Hemodialyse standard", new BigDecimal("8000"), 2));
        memoire.poserLeReglement(centre, F1, "10000");
        memoire.rattacher(centre, F1, new SeanceRattachee(S1, LocalDate.of(2026, 10, 3), "SIGNEE", null));
        memoire.rattacher(centre, F1, new SeanceRattachee(S2, LocalDate.of(2026, 10, 7), "SIGNEE", null));
        ConsulterLesFactures consulter = new ConsulterLesFactures(memoire, memoire, c -> ZoneId.of("Africa/Algiers"), HORLOGE);

        ProxyFactory securite = new ProxyFactory(new FacturesControleur(new SessionDuCentre(), consulter));
        securite.setProxyTargetClass(true);
        securite.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        mvc = MockMvcBuilders.standaloneSetup(securite.getProxy())
                .setControllerAdvice(new FacturesAdvice(), new RefusDAccesCommun()).build();
        avecLeRole("ADMIN");
    }

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    private static void avecLeRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("marie", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private ResultActions liste(String... parametres) throws Exception {
        var requete = get("/api/v1/facturation/factures").param("centerId", CENTRE.toString());
        for (int i = 0; i < parametres.length; i += 2) {
            requete = requete.param(parametres[i], parametres[i + 1]);
        }
        return mvc.perform(requete);
    }

    private void refuseeAvecLeCode(String code, String... parametres) throws Exception {
        liste(parametres).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void la_liste_repond_avec_les_champs_du_contrat_la_periode_effective_les_totaux_et_les_defauts_de_pagination() throws Exception {
        liste("du", "2026-11-01", "au", "2026-11-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.du").value("2026-11-01")).andExpect(jsonPath("$.au").value("2026-11-30"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].factureId").value(F2.toString()))
                .andExpect(jsonPath("$.items[1].factureId").value(F1.toString()))
                .andExpect(jsonPath("$.items[1].numeroFacture").value("FAC-2026-0001"))
                .andExpect(jsonPath("$.items[1].dateFacturation").value("2026-11-05"))
                .andExpect(jsonPath("$.items[1].periodeDebut").value("2026-11-01"))
                .andExpect(jsonPath("$.items[1].periodeFin").value("2026-11-05"))
                .andExpect(jsonPath("$.items[1].patientId").value(PATIENT.toString()))
                .andExpect(jsonPath("$.items[1].numeroAssurance").value("111"))
                .andExpect(jsonPath("$.items[1].patientNom").value("BENALI"))
                .andExpect(jsonPath("$.items[1].patientPrenom").value("Karim"))
                .andExpect(jsonPath("$.items[1].caisseId").value(CNAS.toString()))
                .andExpect(jsonPath("$.items[1].caisse").value("CNAS"))
                .andExpect(jsonPath("$.items[1].nombreDeSeances").value(2))
                .andExpect(jsonPath("$.items[1].tauxTva").value(9.0))
                .andExpect(jsonPath("$.items[1].montantHt").value(16000.0))
                .andExpect(jsonPath("$.items[1].montantTva").value(1440.0))
                .andExpect(jsonPath("$.items[1].montantTtc").value(17440.0))
                .andExpect(jsonPath("$.items[1].montantRegle").value(10000.0))
                .andExpect(jsonPath("$.items[1].reste").value(7440.0))
                .andExpect(jsonPath("$.items[1].tropPercu").value(0.0))
                .andExpect(jsonPath("$.items[1].etat").value("PARTIELLEMENT_REGLEE"))
                .andExpect(jsonPath("$.totaux.nombreDeFactures").value(2))
                .andExpect(jsonPath("$.totaux.montantHt").value(24000.0))
                .andExpect(jsonPath("$.totaux.montantTva").value(1440.0))
                .andExpect(jsonPath("$.totaux.montantTtc").value(25440.0))
                .andExpect(jsonPath("$.totaux.montantRegle").value(10000.0))
                .andExpect(jsonPath("$.totaux.reste").value(15440.0));
    }

    @Test
    void sans_periode_la_liste_porte_sur_le_mois_courant() throws Exception {
        liste().andExpect(status().isOk()).andExpect(jsonPath("$.du").value("2026-11-01")).andExpect(jsonPath("$.au").value("2026-11-30"))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void la_liste_lit_le_tri_champ_virgule_direction_les_filtres_et_la_page() throws Exception {
        liste("du", "2026-10-01", "au", "2026-11-30", "sort", "montantTtc,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].factureId").value(F1.toString()))
                .andExpect(jsonPath("$.items[2].factureId").value(F3.toString()));
        liste("du", "2026-10-01", "au", "2026-11-30", "sort", "patient").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].patientNom").value("amrani"))
                .andExpect(jsonPath("$.items[2].patientNom").value("Chikhi"));
        liste("du", "2026-10-01", "au", "2026-11-30", "etat", "NON_REGLEE", "montantMin", "5000").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].factureId").value(F2.toString()));
        liste("du", "2026-10-01", "au", "2026-11-30", "page", "1", "size", "2").andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totaux.nombreDeFactures").value(3));
    }

    @Test
    void le_detail_repond_avec_les_lignes_figees_les_seances_et_leur_forfait_et_les_totaux_figes() throws Exception {
        mvc.perform(get("/api/v1/facturation/factures/{id}/lignes", F1).param("centerId", CENTRE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factureId").value(F1.toString()))
                .andExpect(jsonPath("$.numeroFacture").value("FAC-2026-0001"))
                .andExpect(jsonPath("$.tauxTva").value(9.0))
                .andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.lignes[0].forfaitId").value(HD.toString()))
                .andExpect(jsonPath("$.lignes[0].forfaitLibelle").value("Hemodialyse standard"))
                .andExpect(jsonPath("$.lignes[0].prixUnitaireHt").value(8000.0))
                .andExpect(jsonPath("$.lignes[0].nombreDeSeances").value(2))
                .andExpect(jsonPath("$.lignes[0].montantHt").value(16000.0))
                .andExpect(jsonPath("$.lignes[0].montantTva").value(1440.0))
                .andExpect(jsonPath("$.lignes[0].montantTtc").value(17440.0))
                .andExpect(jsonPath("$.seances.length()").value(2))
                .andExpect(jsonPath("$.seances[0].seanceId").value(S1.toString()))
                .andExpect(jsonPath("$.seances[0].dateSeance").value("2026-10-03"))
                .andExpect(jsonPath("$.seances[0].statut").value("SIGNEE"))
                .andExpect(jsonPath("$.seances[0].forfaitId").value(HD.toString()))
                .andExpect(jsonPath("$.seances[0].forfaitLibelle").value("Hemodialyse standard"))
                .andExpect(jsonPath("$.seances[0].prixUnitaireHt").value(8000.0))
                .andExpect(jsonPath("$.totaux.montantHt").value(16000.0))
                .andExpect(jsonPath("$.totaux.montantTva").value(1440.0))
                .andExpect(jsonPath("$.totaux.montantTtc").value(17440.0))
                .andExpect(jsonPath("$.totaux.montantRegle").value(10000.0))
                .andExpect(jsonPath("$.totaux.reste").value(7440.0));
    }

    @Test
    void une_facture_inconnue_ou_d_un_autre_centre_repond_404_sans_corps() throws Exception {
        mvc.perform(get("/api/v1/facturation/factures/{id}/lignes", FB).param("centerId", CENTRE.toString()))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        mvc.perform(get("/api/v1/facturation/factures/{id}/lignes", UUID.randomUUID()).param("centerId", CENTRE.toString()))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
    }

    @Test
    void une_periode_incomplete_inversee_ou_trop_longue_est_refusee_avec_periode_invalide() throws Exception {
        refuseeAvecLeCode("PERIODE_INVALIDE", "du", "2026-11-01");
        refuseeAvecLeCode("PERIODE_INVALIDE", "au", "2026-11-30");
        refuseeAvecLeCode("PERIODE_INVALIDE", "du", "2026-11-30", "au", "2026-11-01");
        refuseeAvecLeCode("PERIODE_INVALIDE", "du", "2025-01-01", "au", "2026-01-02");
    }

    @Test
    void un_tri_illisible_est_refuse_avec_tri_invalide() throws Exception {
        refuseeAvecLeCode("TRI_INVALIDE", "sort", "foo");
        refuseeAvecLeCode("TRI_INVALIDE", "sort", "montantTtc,up");
        refuseeAvecLeCode("TRI_INVALIDE", "sort", "a,b,c");
        refuseeAvecLeCode("TRI_INVALIDE", "sort", "montantTtc,desc,x");
    }

    @Test
    void un_etat_inconnu_est_refuse_avec_etat_invalide_et_des_montants_inverses_avec_montant_invalide() throws Exception {
        refuseeAvecLeCode("ETAT_INVALIDE", "etat", "X");
        refuseeAvecLeCode("MONTANT_INVALIDE", "montantMin", "500", "montantMax", "100");
    }

    @Test
    void un_parametre_illisible_ou_un_centre_absent_est_refuse_avec_parametre_invalide_et_non_une_erreur_serveur() throws Exception {
        refuseeAvecLeCode("PARAMETRE_INVALIDE", "page", "abc");
        refuseeAvecLeCode("PARAMETRE_INVALIDE", "size", "vingt");
        refuseeAvecLeCode("PARAMETRE_INVALIDE", "du", "2026-13-01", "au", "2026-11-30");
        refuseeAvecLeCode("PARAMETRE_INVALIDE", "caisseId", "pas-un-uuid");
        refuseeAvecLeCode("PARAMETRE_INVALIDE", "montantMin", "beaucoup");
        mvc.perform(get("/api/v1/facturation/factures")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mvc.perform(get("/api/v1/facturation/factures").param("centerId", "pas-un-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mvc.perform(get("/api/v1/facturation/factures/{id}/lignes", "pas-un-uuid").param("centerId", CENTRE.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }

    @Test
    void un_autre_centre_que_celui_de_la_session_est_refuse_403() throws Exception {
        mvc.perform(get("/api/v1/facturation/factures").param("centerId", AUTRE_CENTRE.toString()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/facturation/factures/{id}/lignes", FB).param("centerId", AUTRE_CENTRE.toString()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void un_profil_autre_qu_administrateur_est_refuse_403_avant_toute_erreur_de_lecture() throws Exception {
        for (String role : List.of("SECRETAIRE", "MEDECIN", "DIRECTION")) {
            avecLeRole(role);
            liste().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(get("/api/v1/facturation/factures/{id}/lignes", F1).param("centerId", CENTRE.toString()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            liste("page", "abc").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(get("/api/v1/facturation/factures")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }
}
