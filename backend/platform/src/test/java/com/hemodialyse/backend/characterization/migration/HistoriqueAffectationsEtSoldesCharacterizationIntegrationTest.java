package com.hemodialyse.backend.characterization.migration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

/**
 * Filet S6a (ADR 0133), reprise des affectations assure-patient ({@code AffectationMigrator}) et des soldes d'ouverture
 * ({@code OpeningBalanceMigrator}, qui relit le patient par {@code PatientRepositoryPort}). Le nominal des soldes (factures,
 * lignes, reglements, payeur fige) est deja verrouille par {@code FacturesDeRepriseCharacterizationIntegrationTest} ; on
 * verrouille ici les controles de l'outil et le remplacement au rejeu.
 */
class HistoriqueAffectationsEtSoldesCharacterizationIntegrationTest extends HistoriquePatientSupport {

    private static final String ENTETE_AFFECTATIONS = "legacyId;patient;assureNumeroAssurance;dateDebut;dateFin;principale";
    private static final String ENTETE_SOLDES =
            "numeroFacture;patient;dateFacture;periodeDebut;periodeFin;montantTtc;montantRegle;dateDernierReglement;libelle";

    /** Instantane reduit : affectations, leurs correspondances et l'assure principal porte par le patient. */
    @Override
    protected ObjectNode etat() {
        return etatCompact(CENTRE_A, List.of("AFFECTATIONS"), "numero_assurance, assure_numero_assurance");
    }

    private void preparerAssuresEtPatient(UUID lot) throws Exception {
        importerOk(lot, "assures", fichier(ENTETE_ASSURES,
                "A1;ZA900;BENALI;Ahmed;M;;;;;", "A2;ZA901;KACI;Omar;M;;;;;", "A3;ZA902;DJABRI;Sofiane;M;;;;;"));
        importerOk(lot, "patients", fichier(ENTETE_PATIENTS, "P1;ZAP1;BENALI;Karim;M;1962-03-15;2019-01-02;;Fils;ZA900"));
    }

    @Test
    void les_affectations_sont_controlees_puis_importees_rejouees_et_rapprochees_de_l_historique_existant() throws Exception {
        UUID lot = ouvrirLot(adminA());
        preparerAssuresEtPatient(lot);
        ObjectNode r = mapper.createObjectNode();
        r.set("1_refus", rendu(importer(lot, "affectations", fichier(ENTETE_AFFECTATIONS,
                "AF1;P9;ZA900;2020-01-01;;non",
                "AF2;P1;ZX-INCONNU;2020-01-01;;non",
                "AF3;P1;ZA901;2020-06-01;2020-01-01;non",
                "AF4;P1;ZA901;2020-01-01;2020-12-31;oui",
                "AF5;P1;ZA901;2021-01-01;;oui",
                "AF6;P1;ZA902;2022-01-01;;oui",
                "af5;P1;ZA902;2023-01-01;;non",
                ";P1;ZA901;2024-01-01;;non",
                ";P1;za901;2024-01-01;;non"), false)));
        r.set("1_etat", etat());
        String valide = fichier(ENTETE_AFFECTATIONS,
                "AF10;P1;ZA901;2019-01-02;2020-12-31;non",
                ";P1;ZA902;2021-01-01;2021-12-31;non",
                "AF12;P1;ZA901;2022-01-01;;oui");
        r.set("2_verification", rendu(importer(lot, "affectations", valide, true)));
        r.set("2_etat", etat());
        r.set("3_import", rendu(importer(lot, "affectations", valide, false)));
        r.set("3_etat", etat());
        r.set("4_rejeu", rendu(importer(lot, "affectations", valide, false)));
        r.set("4_etat", etat());
        // Rapprochement avec une ligne d'historique existante (meme assure, meme date de debut), sans identifiant d'origine.
        r.set("5_rapprochement_historique", rendu(importer(lot, "affectations", fichier(ENTETE_AFFECTATIONS,
                ";P1;ZA900;2019-01-02;;non"), false)));
        r.set("5_etat", etat());
        golden("affectations_controles_import_et_rejeu", r);
    }

    @Test
    void les_soldes_d_ouverture_sont_controles_remplaces_au_rejeu_et_les_factures_soldees_ignorees() throws Exception {
        UUID lot = ouvrirLot(adminA());
        importerOk(lot, "patients", fichier(ENTETE_PATIENTS,
                "P1;ZS1;BENALI;Karim;M;1962-03-15;2019-01-02;;;", "P2;ZS2;KACI;Nadia;F;1970-05-20;2020-06-01;;;"));
        ObjectNode r = mapper.createObjectNode();
        r.set("1_refus", rendu(importer(lot, "soldes-ouverture", fichier(ENTETE_SOLDES,
                "F1;P9;2026-01-31;;;1000;0;;",
                "F2;P1;2026-01-31;;;0;0;;",
                "F3;P1;2026-01-31;;;1000;1500;;",
                "F4;P1;2026-01-31;2026-02-01;2026-01-01;1000;0;;",
                "F5;P1;2099-01-01;;;1000;0;;",
                "F6;P1;2026-01-31;;;-5;0;;",
                "F7;P1;2026-01-31;;;abc;0;;",
                "F8;P1;2026-01-31;;;1000;0;;",
                "f8;P1;2026-01-31;;;1000;0;;"), false)));
        r.set("1_etat", etatFactures(CENTRE_A));
        String f = fichier(ENTETE_SOLDES,
                "F10;P1;2026-01-31;;;67200;20000;;",
                "F11;P2;2026-02-28;2026-02-01;2026-02-28;5600;0;2026-03-15;Séances de février",
                "F12;P1;2026-03-15;;;5600;5600;;");
        r.set("2_verification", rendu(importer(lot, "soldes-ouverture", f, true)));
        r.set("2_etat", etatFactures(CENTRE_A));
        r.set("3_import", rendu(importer(lot, "soldes-ouverture", f, false)));
        r.set("3_etat", etatFactures(CENTRE_A));
        r.set("3_correspondances", etatCompact(CENTRE_A, List.of("SOLDES_OUVERTURE"), "numero_assurance").get("correspondances"));
        r.set("4_rejeu_montants_modifies", rendu(importer(lot, "soldes-ouverture", fichier(ENTETE_SOLDES,
                "F10;P1;2026-01-31;;;70000;30000;;"), false)));
        r.set("4_etat", etatFactures(CENTRE_A));
        r.set("5_rejeu_devenu_solde", rendu(importer(lot, "soldes-ouverture", fichier(ENTETE_SOLDES,
                "F10;P1;2026-01-31;;;70000;70000;;"), false)));
        r.set("5_etat", etatFactures(CENTRE_A));
        golden("soldes_ouverture_controles_et_remplacement", r);
    }
}
