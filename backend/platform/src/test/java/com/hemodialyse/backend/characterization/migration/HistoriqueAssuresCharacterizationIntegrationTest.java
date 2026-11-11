package com.hemodialyse.backend.characterization.migration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * Filet S6a (ADR 0133), reprise des assures ({@code AssureMigrator}) : verification, import, rejeu, refus tout-ou-rien,
 * rapprochement d'un assure existant. Observation : reponses de {@code POST /api/v1/admin/migration/batches/{lot}/import/assures}
 * et tables {@code assure} / {@code migration_id_map}.
 */
class HistoriqueAssuresCharacterizationIntegrationTest extends HistoriquePatientSupport {

    @Test
    void la_verification_n_ecrit_rien_l_import_cree_et_le_rejeu_met_a_jour_sans_effacer() throws Exception {
        UUID lot = ouvrirLot(adminA());
        String premier = fichier(ENTETE_ASSURES,
                "A1;ZA100;BENALI;Ahmed;Homme;15/03/1962;0550123456;0661123456;12 rue Didouche Mourad;O+",
                "A2;za200;KACI;Nadia;F;1970-05-20;;;;");
        ObjectNode r = mapper.createObjectNode();
        r.set("1_verification", rendu(importer(lot, "assures", premier, true)));
        r.set("1_etat", etat());
        r.set("2_import", rendu(importer(lot, "assures", premier, false)));
        r.set("2_etat", etat());
        r.set("3_rejeu_identique", rendu(importer(lot, "assures", premier, false)));
        r.set("3_etat", etat());
        // Le rejeu reporte les valeurs renseignees seulement : prenom et telephone vides ne sont pas effaces.
        r.set("4_rejeu_modifie", rendu(importer(lot, "assures", fichier(ENTETE_ASSURES,
                "A1;ZA100;BENALI-MODIFIE;;;;;0771000000;;"), false)));
        r.set("4_etat", etat());
        r.set("5_executions", executions(CENTRE_A));
        golden("assures_verification_import_et_rejeu", r);
    }

    @Test
    void un_fichier_avec_une_anomalie_n_ecrit_aucun_assure() throws Exception {
        UUID lot = ouvrirLot(adminA());
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom) VALUES ('ZA-AUTRE', ?, 'DU-CENTRE-B')", CENTRE_B);
        viderLesCaches();
        ObjectNode r = mapper.createObjectNode();
        r.set("1_doublons_et_autre_centre", rendu(importer(lot, "assures", fichier(ENTETE_ASSURES,
                "A1;ZA300;VALIDE;Karim;M;;;;;",
                "A1;ZA301;MEME-IDENTIFIANT;Karim;M;;;;;",
                "A3;ZA300;MEME-NUMERO;Karim;M;;;;;",
                "A4;za300;MEME-NUMERO-CASSE;Karim;M;;;;;",
                "A5;ZA-AUTRE;AUTRE-CENTRE;Karim;M;;;;;"), false)));
        r.set("1_etat", etat());
        r.set("2_formats_invalides", rendu(importer(lot, "assures", fichier(ENTETE_ASSURES,
                "A6;ZA400;SEXE;Karim;X;;;;;",
                "A7;ZA401;DATE;Karim;M;31/02/2020;;;;",
                "A8;ZA402;TELEPHONE;Karim;M;;abc;;;",
                ";ZA403;SANS-IDENTIFIANT;Karim;M;;;;;",
                "A10;ZA404;;Karim;M;;;;;"), false)));
        r.set("2_etat", etat());
        // Identifiant d'origine deja repris avec un autre numero.
        importerOk(lot, "assures", fichier(ENTETE_ASSURES, "A20;ZA500;PREMIER;Karim;M;;;;;"));
        r.set("3_conflit_identifiant_d_origine", rendu(importer(lot, "assures", fichier(ENTETE_ASSURES,
                "A20;ZA501;MEME-IDENTIFIANT-AUTRE-NUMERO;Karim;M;;;;;"), false)));
        r.set("3_etat", etat());
        golden("assures_refus_tout_ou_rien", r);
    }

    @Test
    void un_assure_deja_present_dans_le_centre_est_rapproche_et_mis_a_jour_sans_effacer() throws Exception {
        UUID lot = ouvrirLot(adminA());
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, prenom, adresse, groupe_sanguin, created_at) "
                + "VALUES ('ZA600', ?, 'ANCIEN-NOM', 'Prenom-conserve', 'Adresse conservee', 'A+', CURRENT_TIMESTAMP)", CENTRE_A);
        viderLesCaches();
        ObjectNode r = mapper.createObjectNode();
        String fichier = fichier(ENTETE_ASSURES, "A1;ZA600;NOUVEAU-NOM;;M;1962-03-15;;;;");
        r.set("1_verification", rendu(importer(lot, "assures", fichier, true)));
        r.set("2_import", rendu(importer(lot, "assures", fichier, false)));
        r.set("2_etat", etat());
        golden("assures_rapprochement_d_un_assure_existant", r);
    }
}
