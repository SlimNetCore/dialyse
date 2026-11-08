package com.hemodialyse.backend.characterization.seance.se7f;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE7f : dernier chemin d'écriture du module legacy {@code bc-seance} encore en production, {@code PUT /api/v1/seances/{id}/volet-paramedical}
 * ({@code VoletParamedicalRestController} → {@code VoletParamedicalDomainService} → {@code SeanceRepositoryAdapter.findById},
 * {@code VoletParamedicalRepositoryAdapter.save}, puis {@code SeanceDomainService.getDetails} pour notifier). Le filet DM1
 * ({@code dossiermedical/dm1/ecriture}) fige le nominal, les bornes, les profils et le cloisonnement du corps ; ici les cas que DM1 ne
 * touche pas : la fiche du patient hors du centre de la séance (écriture puis échec), un volet déjà posé sous un autre centre (unicité par
 * séance) et la relecture des volets au centre par le détail de la séance ({@code VoletsFournisLegacy}).
 */
class LaSaisieResiduelleDuVoletParamedicalCharacterizationIntegrationTest extends Se7fCharacterizationSupport {

    @Override
    protected String dossier() {
        return "volet";
    }

    @Test
    void la_saisie_sur_une_seance_dont_la_fiche_est_dans_un_autre_centre_ecrit_le_volet_puis_echoue_sans_notifier() throws Exception {
        UUID ficheAuCentreB = patientSe1(CENTRE_B, "FICHE_B");
        UUID seance = seanceEnBase(CENTRE_A, ficheAuCentreB, "SEANCE_A_FICHE_B", fixe(10), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reponse (infirmier du centre A, corps centre A)", reponse(saisirLeVolet(seance, CENTRE_A, COMPLET, infirmierA())));
        m.put("02 volets en base apres la reponse", voletsParamedicaux(seance));
        m.put("03 notifications", notificationsLues());
        m.put("04 seconde saisie (le volet existe : meme chemin, meme issue)", reponse(saisirLeVolet(seance, CENTRE_A, "\"poidsAvantKg\":71", infirmierA())));
        m.put("05 volets en base (poids remplace)", voletsParamedicaux(seance));
        arbre("fiche_hors_centre", m);
    }

    @Test
    void un_volet_deja_pose_sous_un_autre_centre_fait_echouer_la_saisie_par_l_unicite_de_la_seance() throws Exception {
        UUID patient = patientSe1(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE_A", fixe(10), "VALIDEE");
        voletParamedical(CENTRE_B, seance, "80.00", "77.50", "140/90", null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 volets avant", voletsParamedicaux(seance));
        m.put("02 reponse (infirmier du centre A, corps centre A)", reponse(saisirLeVolet(seance, CENTRE_A, COMPLET, infirmierA())));
        m.put("03 volets apres (le volet du centre B est intact, aucun volet au centre A)", voletsParamedicaux(seance));
        m.put("04 notifications", notificationsLues());
        arbre("volet_d_un_autre_centre", m);
    }

    @Test
    void le_detail_de_la_seance_relit_les_volets_au_centre_de_la_seance_seulement() throws Exception {
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
        arbre("detail_volets_au_centre", m);
    }
}
