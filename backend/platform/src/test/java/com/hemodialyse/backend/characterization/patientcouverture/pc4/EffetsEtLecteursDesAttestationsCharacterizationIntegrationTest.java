package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import com.hemodialyse.backend.domain.seance.port.SeanceBillingEligibilityPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC4 : ce que les attestations changent ailleurs et ce que les autres écrivent dans {@code attestation_droit} : éligibilité à la
 * facturation d'une séance ({@code SeanceBillingEligibilityPort}, bornes incluses), création d'une PEC de non-vacancier et route
 * {@code session-allowed} pilotées par l'API d'attestation, création d'une séance (HTTP), attestation saisie sur la fiche (PC1) lue par
 * les routes d'attestation, compteurs du tableau de bord (jour de la JVM), audit et diffusions, concurrence.
 */
class EffetsEtLecteursDesAttestationsCharacterizationIntegrationTest extends AttestationCharacterizationSupport {

    @Autowired
    private SeanceBillingEligibilityPort eligibilite;

    @Override
    protected String dossier() {
        return "effets";
    }

    private String causeDe(UUID patient, LocalDate date) {
        return eligibilite.causeNonFacturable(CenterId.of(CENTRE_A), patient, date).map(Enum::name).orElse("facturable");
    }

    /** Patient avec une PEC validée couvrant très largement (forfait renseigné) : seule l'attestation fait varier la cause. */
    private UUID patientAvecPecValidee(String alias, boolean vacancier) {
        UUID f = idStable("forfait", "HD3-" + CENTRE_A);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM forfait WHERE id = ?", Integer.class, f) == 0) forfait(CENTRE_A, "HD3", "9500.00");
        else aliasNommes.put(f.toString(), "<FORFAIT_HD3>");
        UUID p = patientCouvert(CENTRE_A, alias, vacancier, null);
        pecEnBase(CENTRE_A, "PEC_" + alias, p, "VALIDEE", jour(-200), jour(200), f, jour(-200), jour(200), f, 1);
        return p;
    }

    @Test
    void l_eligibilite_de_la_seance_exige_une_attestation_dont_la_periode_couvre_le_jour_bornes_incluses_meme_pour_un_vacancier() throws Exception {
        LocalDate j = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        UUID p = patientAvecPecValidee("SANS", false);
        m.put("aucune attestation (non-vacancier)", causeDe(p, j));
        p = patientAvecPecValidee("VACANCIER_SANS", true);
        m.put("aucune attestation (vacancier)", causeDe(p, j));
        p = patientAvecPecValidee("VACANCIER_AVEC", true);
        attestationEnBase(CENTRE_A, "AT1", p, jour(-5), jour(5));
        m.put("attestation couvrante (vacancier)", causeDe(p, j));
        p = patientAvecPecValidee("COUVRANTE", false);
        attestationEnBase(CENTRE_A, "AT2", p, jour(-5), jour(5));
        m.put("attestation couvrante", causeDe(p, j));
        p = patientAvecPecValidee("DEBUT_LE_JOUR", false);
        attestationEnBase(CENTRE_A, "AT3", p, jour(0), jour(5));
        m.put("début le jour même", causeDe(p, j));
        p = patientAvecPecValidee("FIN_LE_JOUR", false);
        attestationEnBase(CENTRE_A, "AT4", p, jour(-5), jour(0));
        m.put("fin le jour même", causeDe(p, j));
        p = patientAvecPecValidee("UN_JOUR", false);
        attestationEnBase(CENTRE_A, "AT5", p, jour(0), jour(0));
        m.put("un seul jour, le jour même", causeDe(p, j));
        p = patientAvecPecValidee("ECHUE_HIER", false);
        attestationEnBase(CENTRE_A, "AT6", p, jour(-50), jour(-1));
        m.put("échue hier", causeDe(p, j));
        p = patientAvecPecValidee("DEMAIN", false);
        attestationEnBase(CENTRE_A, "AT7", p, jour(1), jour(50));
        m.put("commence demain", causeDe(p, j));
        p = patientAvecPecValidee("TROU", false);
        attestationEnBase(CENTRE_A, "AT8", p, jour(-50), jour(-3));
        attestationEnBase(CENTRE_A, "AT9", p, jour(3), jour(50));
        m.put("deux attestations avec un trou sur le jour", causeDe(p, j));
        p = patientAvecPecValidee("CONTIGUES", false);
        attestationEnBase(CENTRE_A, "AT10", p, jour(-50), jour(-1));
        attestationEnBase(CENTRE_A, "AT11", p, jour(0), jour(50));
        m.put("deux attestations contiguës", causeDe(p, j));
        p = patientAvecPecValidee("ECHUE_ET_VALIDE", false);
        attestationEnBase(CENTRE_A, "AT12", p, jour(-50), jour(-10));
        attestationEnBase(CENTRE_A, "AT13", p, jour(-5), jour(5));
        m.put("une échue et une valide", causeDe(p, j));
        p = patientAvecPecValidee("AUTRE_CENTRE", false);
        attestationEnBase(CENTRE_B, "AT14", p, jour(-5), jour(5));
        m.put("attestation enregistrée dans l'autre centre", causeDe(p, j));
        UUID autre = patientAvecPecValidee("AUTRE_PATIENT", false);
        UUID voisin = patientCouvert(CENTRE_A, "VOISIN", false, null);
        attestationEnBase(CENTRE_A, "AT15", voisin, jour(-5), jour(5));
        m.put("attestation d'un autre patient", causeDe(autre, j));
        p = patientAvecPecValidee("DATE_PASSEE", false);
        attestationEnBase(CENTRE_A, "AT16", p, jour(-5), jour(5));
        m.put("séance d'il y a 4 jours, attestation couvrante", causeDe(p, jour(-4)));
        m.put("séance d'il y a 6 jours, attestation commençant il y a 5 jours", causeDe(p, jour(-6)));
        m.put("séance dans 6 jours, attestation finissant dans 5 jours", causeDe(p, jour(6)));
        m.put("séance exactement à la fin (dans 5 jours)", causeDe(p, jour(5)));
        m.put("séance exactement au début (il y a 5 jours)", causeDe(p, jour(-5)));
        arbre("cause_de_non_facturabilite", m);
    }

    @Test
    void l_api_d_attestation_pilote_la_creation_de_pec_du_non_vacancier_la_session_autorisee_et_la_facturabilite() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "CYCLE", false, "ENFANT");
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("1. PEC sans attestation", refus(creerLaPec("PEC_0", patient, CENTRE_A, jour(-5), jour(25), forfait, adminA())));
        m.put("2. attestation créée par l'API", refus(creerAttestation(patient, CENTRE_A, jour(-5), jour(5), adminA())));
        Resp pec = creerLaPec("PEC_1", patient, CENTRE_A, jour(-5), jour(25), forfait, adminA());
        m.put("3. PEC avec attestation", refus(pec));
        UUID id = UUID.fromString(corps(pec).path("id").asString());
        m.put("4. validation", refus(valider(id, CENTRE_A, jour(-5), jour(25), forfait, adminA())));
        m.put("5. facturabilité (séance du jour)", causeDe(patient, jour(0)));
        m.put("6. session-allowed", corps(sessionAllowed(id, CENTRE_A, adminA())).path("allowed").toString());
        Resp liste = attestationsDuPatient(patient, CENTRE_A, adminA());
        UUID attestation = UUID.fromString(corps(liste).get(0).path("id").asString());
        m.put("7. suppression de l'attestation", refus(supprimerAttestation(attestation, CENTRE_A, patient, adminA())));
        m.put("8. facturabilité après suppression", causeDe(patient, jour(0)));
        m.put("9. session-allowed après suppression", corps(sessionAllowed(id, CENTRE_A, adminA())).path("allowed").toString());
        m.put("10. nouvelle PEC après suppression", refus(creerLaPec("PEC_2", patient, CENTRE_A, jour(-5), jour(25), forfait, adminA())));
        m.put("11. attestation échue créée par l'API", refus(creerAttestation(patient, CENTRE_A, jour(-50), jour(-1), adminA())));
        m.put("12. facturabilité avec une attestation échue", causeDe(patient, jour(0)));
        m.put("13. PEC avec une attestation échue", refus(creerLaPec("PEC_3", patient, CENTRE_A, jour(-5), jour(25), forfait, adminA())));
        m.put("14. attestation du jour (début = fin = jour)", refus(creerAttestation(patient, CENTRE_A, jour(0), jour(0), adminA())));
        m.put("15. facturabilité avec l'attestation du jour", causeDe(patient, jour(0)));
        m.put("16. PEC avec l'attestation du jour", refus(creerLaPec("PEC_4", patient, CENTRE_A, jour(-5), jour(25), forfait, adminA())));
        arbre("de_l_attestation_a_la_pec", m);
        etatDesAttestations("attestations");
        etatDesPec("pec");
    }

    @Test
    void la_creation_d_une_seance_est_refusee_sans_attestation_couvrante_et_acceptee_avec() throws Exception {
        UUID patient = patientAvecPecValidee("SEANCE", false);
        String corps = "{'centerId':'" + CENTRE_A + "','patientId':'" + patient + "','dateSeance':'" + jour(0) + "'}";
        Map<String, Object> m = new LinkedHashMap<>();
        Resp sans = doPost("/api/v1/seances", json(corps), adminA());
        m.put("sans attestation", refus(sans));
        attestationEnBase(CENTRE_A, "AT_ECHUE", patient, jour(-50), jour(-1));
        m.put("attestation échue hier", refus(doPost("/api/v1/seances", json(corps), adminA())));
        attestationEnBase(CENTRE_A, "AT_DEMAIN", patient, jour(1), jour(50));
        m.put("attestation commençant demain", refus(doPost("/api/v1/seances", json(corps), adminA())));
        String corpsPasse = "{'centerId':'" + CENTRE_A + "','patientId':'" + patient + "','dateSeance':'" + jour(-3) + "'}";
        m.put("séance d'il y a trois jours, couverte par l'attestation échue hier", refus(doPost("/api/v1/seances", json(corpsPasse), adminA())));
        arbre("creation_de_seance", m);
        etatDesAttestations("attestations");
        Map<String, Object> nombre = new LinkedHashMap<>();
        nombre.put("séances du patient (seule la dernière a été créée)", jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE patient_id = ?", Integer.class, patient));
        arbre("seances_creees", nombre);
    }

    @Test
    void une_attestation_saisie_sur_la_fiche_est_lue_et_supprimee_par_les_routes_d_attestation() throws Exception {
        Map<String, Object> fiche = ficheMap("Fiche Attestee", "ASS-PC4-F1");
        Resp creation = creerLaFiche("FICHE_1", fiche, adminA());
        UUID patient = UUID.fromString(corps(creation).path("id").asString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("1. création de la fiche", refus(creation));
        Resp liste = attestationsDuPatient(patient, CENTRE_A, adminA());
        List<String> periodes = new ArrayList<>();
        for (tools.jackson.databind.JsonNode a : corps(liste)) periodes.add(a.path("dateDebut").asString() + " -> " + a.path("dateFin").asString());
        m.put("2. liste d'attestations de la fiche", periodes);
        m.put("3. recherche du centre (total)", corps(rechercher("{'centerId':'" + CENTRE_A + "','page':0,'size':10,'assurance':'ASS-PC4-F1'}", adminA())).path("total").asString());
        m.put("4. facturabilité sans PEC", causeDe(patient, jour(0)));
        UUID attestation = UUID.fromString(corps(liste).get(0).path("id").asString());
        m.put("5. suppression par la route d'attestation", refus(supprimerAttestation(attestation, CENTRE_A, patient, adminA())));
        Map<String, Object> sansAttestation = ficheMap("Fiche Attestee", "ASS-PC4-F1");
        sansAttestation.remove("attestationDebut");
        sansAttestation.remove("attestationFin");
        m.put("6. modification de la fiche sans attestation après suppression (non-vacancier)", refus(modifierLaFiche(patient, sansAttestation, adminA())));
        Map<String, Object> autrePeriode = ficheMap("Fiche Attestee", "ASS-PC4-F1");
        autrePeriode.put("attestationDebut", jour(-10).toString());
        autrePeriode.put("attestationFin", jour(10).toString());
        m.put("7. modification de la fiche avec une nouvelle période", refus(modifierLaFiche(patient, autrePeriode, adminA())));
        Resp apres = attestationsDuPatient(patient, CENTRE_A, adminA());
        List<String> apresPeriodes = new ArrayList<>();
        for (tools.jackson.databind.JsonNode a : corps(apres)) apresPeriodes.add(a.path("dateDebut").asString() + " -> " + a.path("dateFin").asString());
        m.put("8. liste après la nouvelle période", apresPeriodes);
        m.put("9. même période créée par la route d'attestation (doublon accepté)", refus(creerAttestation(patient, CENTRE_A, jour(-10), jour(10), adminA())));
        m.put("10. fiche identique enregistrée de nouveau", refus(modifierLaFiche(patient, autrePeriode, adminA())));
        Resp fin = attestationsDuPatient(patient, CENTRE_A, adminA());
        m.put("11. nombre d'attestations (la route a doublé la période de la fiche, la fiche ressaisie n'en ajoute pas)", corps(fin).size());
        arbre("fiche_et_routes", m);
    }

    @Test
    void les_compteurs_du_tableau_de_bord_lisent_la_table_au_jour_de_la_jvm_et_les_dates_de_creation() throws Exception {
        supposerLeJourDeLaJvmEgalAuJourDuCentre();
        UUID p = patientCouvert(CENTRE_A, "TABLEAU", false, "ENFANT");
        attestationEnBase(CENTRE_A, "T1", p, jour(-200), jour(-10));
        attestationEnBase(CENTRE_A, "T2", p, jour(-5), jour(10));
        attestationEnBase(CENTRE_A, "T3", p, jour(-5), jour(30));
        attestationEnBase(CENTRE_A, "T4", p, jour(-5), jour(31));
        attestationEnBase(CENTRE_A, "T5", p, jour(-5), jour(200));
        attestationEnBase(CENTRE_B, "T6", patientCouvert(CENTRE_B, "TABLEAU_B", false, "ENFANT"), jour(-5), jour(1));
        Map<String, Object> m = new LinkedHashMap<>();
        for (String requete : new String[]{"", "&expirationDays=0", "&expirationDays=30", "&expirationDays=31", "&expirationDays=-1", "&month=2026-01", "&month=2026-02"}) {
            Resp r = doGet("/api/v1/dashboard/stats?centerId=" + CENTRE_A + requete, adminA());
            m.put("GET stats" + requete, r.status() == 200 ? corps(r).path("attestationTotal") + " attestation(s), " + corps(r).path("attestationExpiring") + " à échéance" : refus(r));
        }
        Resp b = doGet("/api/v1/dashboard/stats?centerId=" + CENTRE_B, adminB());
        m.put("centre B", corps(b).path("attestationTotal") + " attestation(s), " + corps(b).path("attestationExpiring") + " à échéance");
        Resp s = doPost("/api/v1/dashboard/stats/search", json("{'centerId':'" + CENTRE_A + "','expirationDays':30}"), adminA());
        m.put("POST stats/search (30 jours)", s.status() == 200 ? corps(s).path("attestationTotal") + " attestation(s), " + corps(s).path("attestationExpiring") + " à échéance" : refus(s));
        arbre("compteurs", m);
    }

    @Test
    void les_routes_d_attestation_sont_auditees_et_les_lectures_ne_le_sont_pas() throws Exception {
        monterLAudit();
        UUID patient = patientCouvert(CENTRE_A, "AUDIT", false, "ENFANT");
        Resp c = creerAttestation(patient, CENTRE_A, jour(-5), jour(5), adminA());
        UUID id = UUID.fromString(corps(c).path("id").asString());
        aliasNommes.put(id.toString(), "<ATT_AUDIT>");
        creerAttestation(patient, CENTRE_A, jour(5), jour(-5), adminA());
        attestationsDuPatient(patient, CENTRE_A, adminA());
        rechercher("{'centerId':'" + CENTRE_A + "','page':0,'size':10}", adminA());
        supprimerAttestation(id, CENTRE_A, patient, adminA());
        supprimerAttestation(id, CENTRE_A, patient, as(CENTRE_A, "DIRECTION"));
        audit("audit");
        diffusionsCapturees("diffusions");
    }

    @Test
    void deux_creations_simultanees_de_la_meme_periode_creent_deux_lignes_et_une_suppression_simultanee_de_la_meme_ligne_reussit_deux_fois() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "CONCURRENCE", false, "ENFANT");
        List<Resp> creations = enParallele(() -> creerAttestation(patient, CENTRE_A, jour(-5), jour(5), adminA()),
                () -> creerAttestation(patient, CENTRE_A, jour(-5), jour(5), adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deux créations", List.of(refus(creations.get(0)), refus(creations.get(1))));
        m.put("identifiants distincts", !corps(creations.get(0)).path("id").asString().equals(corps(creations.get(1)).path("id").asString()));
        m.put("lignes", nombreDAttestations());
        UUID id = UUID.fromString(corps(creations.get(0)).path("id").asString());
        List<Resp> suppressions = enParallele(() -> supprimerAttestation(id, CENTRE_A, patient, adminA()),
                () -> supprimerAttestation(id, CENTRE_A, patient, adminA()));
        // l'issue de la seconde dépend de la course (200 si la ligne est déjà partie, 500 DATA_ACCESS_ERROR si elle disparaît entre la lecture
        // et l'effacement) : seul l'ensemble des issues permises est verrouillé
        List<String> issues = List.of(refus(suppressions.get(0)), refus(suppressions.get(1)));
        m.put("chaque suppression rend 200 ou 500 DATA_ACCESS_ERROR, une au moins réussit", issues.stream().allMatch(
                s -> s.startsWith("200") || s.startsWith("500 DATA_ACCESS_ERROR")) && issues.stream().anyMatch(s -> s.startsWith("200")));
        m.put("lignes après", nombreDAttestations());
        arbre("concurrence", m);
    }
}
