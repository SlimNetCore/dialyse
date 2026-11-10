package com.hemodialyse.backend.characterization.seance.se7f;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SE7f : dernier chemin d'écriture du module legacy {@code bc-seance} encore en production, {@code PUT /api/v1/seances/{id}/volet-paramedical}
 * ({@code VoletParamedicalRestController} → {@code VoletParamedicalDomainService} → {@code SeanceRepositoryAdapter.findById},
 * {@code VoletParamedicalRepositoryAdapter.save}, puis {@code SeanceDomainService.getDetails} pour notifier). Le filet DM1
 * ({@code dossiermedical/dm1/ecriture}) fige le nominal, les bornes, les profils et le cloisonnement du corps ; ici les cas que DM1 ne
 * touche pas : la fiche du patient hors du centre de la séance, un volet déjà posé sous un autre centre et la relecture des volets au centre par
 * le détail de la séance ({@code VoletsFournisLegacy}). Lot L4 (2026-11-10, ADR 0116) : les deux premiers goldens sont {@code _nouveau_code}
 * (KB-SEA-93 : refus sans écriture ; KB-SEA-94 : volet remplacé), les anciens sont archivés sous {@code _ecarts-legacy}.
 */
class LaSaisieResiduelleDuVoletParamedicalCharacterizationIntegrationTest extends Se7fCharacterizationSupport {

    @Override
    protected String dossier() {
        return "volet";
    }

    @Test
    void la_saisie_sur_une_seance_dont_la_fiche_est_dans_un_autre_centre_est_refusee_sans_rien_ecrire_ni_notifier_KB_SEA_93() throws Exception {
        UUID ficheAuCentreB = patientSe1(CENTRE_B, "FICHE_B");
        UUID seance = seanceEnBase(CENTRE_A, ficheAuCentreB, "SEANCE_A_FICHE_B", fixe(10), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        Resp premiere = saisirLeVolet(seance, CENTRE_A, COMPLET, infirmierA());
        m.put("01 reponse (infirmier du centre A, corps centre A)", reponse(premiere));
        m.put("02 volets en base apres la reponse (aucun : la fiche est gardee avant l'ecriture)", voletsParamedicaux(seance));
        m.put("03 notifications", notificationsLues());
        m.put("04 seconde saisie (meme refus, toujours rien d'ecrit)", reponse(saisirLeVolet(seance, CENTRE_A, "\"poidsAvantKg\":71", infirmierA())));
        m.put("05 volets en base (toujours aucun)", voletsParamedicaux(seance));
        assertThat(premiere.status()).isEqualTo(400);
        assertThat(voletsParamedicaux(seance)).isEmpty();
        arbre("fiche_hors_centre_nouveau_code", m);
    }

    @Test
    void un_volet_deja_pose_sous_un_autre_centre_est_remplace_comme_le_volet_de_sa_seance_KB_SEA_94() throws Exception {
        UUID patient = patientSe1(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE_A", fixe(10), "VALIDEE");
        voletParamedical(CENTRE_B, seance, "80.00", "77.50", "140/90", null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 volets avant", voletsParamedicaux(seance));
        Resp r = saisirLeVolet(seance, CENTRE_A, COMPLET, infirmierA());
        m.put("02 reponse (infirmier du centre A, corps centre A)", reponse(r));
        m.put("03 volets apres (une seule ligne, remplacee ; son centre n'est PAS reecrit)", voletsParamedicaux(seance));
        m.put("04 notifications", notificationsLues());
        assertThat(r.status()).isEqualTo(200);
        assertThat(voletsParamedicaux(seance)).hasSize(1);
        arbre("volet_d_un_autre_centre_nouveau_code", m);
    }

    @Test
    void le_detail_de_la_seance_relit_les_volets_le_paramedical_par_la_seance_comme_la_saisie_et_le_medical_au_centre() throws Exception {
        UUID patient = patientSe1(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE_A", fixe(10), "VALIDEE");
        voletParamedical(CENTRE_B, seance, "80.00", "77.50", "140/90", "1500");
        voletMedical(CENTRE_B, seance, "Prescription posee au centre B");
        Map<String, Object> m = new LinkedHashMap<>();
        Resp avant = doGet(detailApi(seance, CENTRE_A), infirmierA());
        m.put("01 statut", avant.status());
        m.put("02 volets du detail quand les deux volets sont sous le centre B", Map.of("paramedical", corps(avant).path("paramedical"),
                "medical", corps(avant).path("medical")));
        jdbc.update("DELETE FROM volet_paramedical WHERE seance_id = ?", seance);
        jdbc.update("DELETE FROM volet_medical WHERE seance_id = ?", seance);
        voletParamedical(CENTRE_A, seance, "70.50", "68.00", "130/80", "2000");
        voletMedical(CENTRE_A, seance, "Prescription posee au centre A");
        Resp apres = doGet(detailApi(seance, CENTRE_A), infirmierA());
        m.put("03 volets du detail quand les deux volets sont sous le centre A", Map.of("paramedical", corps(apres).path("paramedical"),
                "medical", corps(apres).path("medical")));
        arbre("detail_volets_au_centre_nouveau_code", m);
    }
}
