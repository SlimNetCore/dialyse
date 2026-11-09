package com.hemodialyse.backend.characterization.patientcouverture.pc3;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import com.hemodialyse.backend.contexts.absencespatients.application.ForfaitsDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PC3 : ce que les contextes voisins lisent de {@code prise_en_charge} : forfait de la PEC applicable à une date (valorisation des
 * absences, RG-ABS-030, ACL {@code ForfaitsDesPatients}), PEC validée d'un patient pour la création d'une séance
 * ({@code PecValidationPort}), cause de non-facturabilité d'une séance ({@code SeanceBillingEligibilityPort}). Point d'observation :
 * les trois ports sont appelés tels que câblés dans le contexte Spring (aucun mock), sur des lignes posées en base, puis sur une PEC
 * pilotée par l'API. Les filets Absences, Séance et Facturation verrouillent leurs propres effets de bout en bout.
 */
class LecteursDeLaPriseEnChargeCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Autowired
    private ForfaitsDesPatients forfaits;
    /**
     * SE7f (ADR 0100) : le port legacy {@code SeanceBillingEligibilityPort} et son ACL (code mort, KB-SEA-97) sont retirés ; la cause est lue
     * par le langage publié de Patient et couverture, et traduite ici dans les libellés du legacy que figent les goldens (même table de
     * correspondance que l'ACL retirée et que {@code contexts/seance/.../EligibiliteParLaCouverture}).
     */
    @Autowired
    private CouvertureDesPatients couverture;

    @Override
    protected String racine() {
        return "../patientcouverture/pc3/";
    }

    @Override
    protected String dossier() {
        return "lecteurs";
    }

    private String forfaitDe(UUID patient, LocalDate date) {
        Optional<com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable> f = forfaits.applicable(new CentreId(CENTRE_A),
                new com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId(patient), date);
        return f.map(x -> x.libelle() + " (" + x.prixHt().toPlainString() + ")").orElse("aucun forfait");
    }

    private String causeDe(UUID patient, LocalDate date) {
        return couverture.causeDeNonFacturation(CENTRE_A, patient, date).map(cause -> switch (cause) {
            case AUCUNE_PEC_COUVRANTE -> "PRISE_EN_CHARGE";
            case PEC_NON_VALIDEE -> "PRISE_EN_CHARGE_NON_VALIDEE";
            case ATTESTATION_ABSENTE -> "ATTESTATION_DROITS";
        }).orElse("facturable");
    }

    @Test
    void le_forfait_applicable_a_une_date_est_le_forfait_effectif_de_la_pec_couvrante_puis_a_defaut_le_plus_recent_sans_condition_de_date() throws Exception {
        UUID f3 = forfait(CENTRE_A, "HD3", "9500.00");
        UUID f4 = forfait(CENTRE_A, "HD4", "11000.00");
        UUID f5 = forfait(CENTRE_A, "HD5", "12000.50");
        UUID fb = forfait(CENTRE_B, "HDB", "7000.00");
        LocalDate j = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        UUID p = patientCouvert(CENTRE_A, "AUCUNE", true, null);
        m.put("aucune PEC", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "CREE", true, null);
        pecEnBase(CENTRE_A, "C1", p, "CREE", jour(-5), jour(5), f3, null, null, null, 1);
        m.put("PEC créée, forfait demandé, période demandée couvrante", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "VALIDEE", true, null);
        pecEnBase(CENTRE_A, "C2", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f4, 1);
        m.put("PEC validée : le forfait effectif prime sur le demandé", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "CLOTUREE", true, null);
        pecEnBase(CENTRE_A, "C3", p, "CLOTUREE", jour(-5), jour(5), f3, jour(-5), jour(5), f4, 1);
        m.put("PEC clôturée : le statut n'est pas regardé", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "INCONNU", true, null);
        pecEnBase(CENTRE_A, "C4", p, "REFUSEE", jour(-5), jour(5), f3, null, null, null, 1);
        m.put("PEC à statut inconnu : le statut n'est pas regardé", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "EFFECTIF_HORS", true, null);
        pecEnBase(CENTRE_A, "C5", p, "VALIDEE", jour(-5), jour(5), f3, jour(-100), jour(-50), f4, 1);
        m.put("période effective échue mais demandée couvrante : forfait effectif retenu", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "EFFECTIF_OUVERT", true, null);
        pecEnBase(CENTRE_A, "C6", p, "VALIDEE", jour(-100), jour(-50), f3, jour(-5), null, f4, 1);
        m.put("période effective ouverte (sans fin)", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "DEMANDE_OUVERTE", true, null);
        pecEnBase(CENTRE_A, "C7", p, "CREE", jour(-5), null, f3, null, null, null, 1);
        m.put("période demandée ouverte (sans fin)", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "BORNE_DEBUT", true, null);
        pecEnBase(CENTRE_A, "C8", p, "CREE", jour(0), jour(5), f3, null, null, null, 1);
        m.put("début le jour même", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "BORNE_FIN", true, null);
        pecEnBase(CENTRE_A, "C9", p, "CREE", jour(-5), jour(0), f3, null, null, null, 1);
        m.put("fin le jour même", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "HORS_PERIODE", true, null);
        pecEnBase(CENTRE_A, "C10", p, "CREE", jour(-100), jour(-50), f3, null, null, null, 1);
        pecEnBase(CENTRE_A, "C11", p, "CREE", jour(50), jour(100), f4, null, null, null, 2);
        m.put("aucune période ne couvre : la plus récemment créée (futur)", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "HORS_PERIODE_EFFECTIF", true, null);
        pecEnBase(CENTRE_A, "C12", p, "VALIDEE", jour(-100), jour(-50), f3, jour(-100), jour(-50), f4, 1);
        pecEnBase(CENTRE_A, "C13", p, "CREE", jour(50), jour(100), f5, null, null, null, 2);
        m.put("aucune période ne couvre : celle qui porte un forfait effectif d'abord", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "SANS_DATES", true, null);
        pecEnBase(CENTRE_A, "C14", p, "CREE", null, null, f3, null, null, null, 1);
        m.put("PEC sans aucune date", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "SANS_FORFAIT", true, null);
        pecEnBase(CENTRE_A, "C15", p, "VALIDEE", jour(-5), jour(5), null, jour(-5), jour(5), null, 1);
        m.put("PEC couvrante sans forfait", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "FORFAIT_B", true, null);
        pecEnBase(CENTRE_A, "C16", p, "VALIDEE", jour(-5), jour(5), fb, jour(-5), jour(5), fb, 1);
        m.put("forfait d'un autre centre", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "FORFAIT_INCONNU", true, null);
        pecEnBase(CENTRE_A, "C17", p, "VALIDEE", jour(-5), jour(5), UUID.nameUUIDFromBytes("inconnu".getBytes()), null, null, null, 1);
        m.put("forfait inexistant", forfaitDe(p, j));
        arbre("forfait_applicable", m);
    }

    @Test
    void deux_pec_se_chevauchant_departagent_par_le_forfait_effectif_puis_par_la_creation_la_plus_recente() throws Exception {
        UUID f3 = forfait(CENTRE_A, "HD3", "9500.00");
        UUID f4 = forfait(CENTRE_A, "HD4", "11000.00");
        UUID f5 = forfait(CENTRE_A, "HD5", "12000.50");
        LocalDate j = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        UUID p = patientCouvert(CENTRE_A, "DEUX_DEMANDES", true, null);
        pecEnBase(CENTRE_A, "D1", p, "CREE", jour(-5), jour(5), f3, null, null, null, 1);
        pecEnBase(CENTRE_A, "D2", p, "CREE", jour(-2), jour(9), f4, null, null, null, 2);
        m.put("deux demandes couvrantes : la plus récemment créée", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "EFFECTIF_ANCIEN", true, null);
        pecEnBase(CENTRE_A, "E1", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f4, 1);
        pecEnBase(CENTRE_A, "E2", p, "CREE", jour(-2), jour(9), f5, null, null, null, 2);
        m.put("effectif ancien contre demande récente", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "DEUX_EFFECTIFS", true, null);
        pecEnBase(CENTRE_A, "F1", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f4, 1);
        pecEnBase(CENTRE_A, "F2", p, "VALIDEE", jour(-2), jour(9), f3, jour(-2), jour(9), f5, 2);
        m.put("deux effectifs : le plus récemment créé", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "CLOTUREE_ET_VALIDEE", true, null);
        pecEnBase(CENTRE_A, "G1", p, "CLOTUREE", jour(-5), jour(5), f3, jour(-5), jour(5), f4, 1);
        pecEnBase(CENTRE_A, "G2", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f5, 2);
        m.put("une clôturée et une validée : la plus récente, sans regard au statut", forfaitDe(p, j));
        p = patientCouvert(CENTRE_A, "ANCIENNE_VALIDEE", true, null);
        pecEnBase(CENTRE_A, "H1", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f4, 2);
        pecEnBase(CENTRE_A, "H2", p, "CLOTUREE", jour(-5), jour(5), f3, jour(-5), jour(5), f5, 1);
        m.put("la plus récente est la validée", forfaitDe(p, j));
        arbre("chevauchement", m);
    }

    @Test
    void la_cause_de_non_facturabilite_distingue_aucune_pec_pec_non_validee_et_attestation_absente() throws Exception {
        UUID f3 = forfait(CENTRE_A, "HD3", "9500.00");
        LocalDate j = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        UUID p = patientCouvert(CENTRE_A, "AUCUNE", true, null);
        m.put("aucune PEC", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "SANS_FORFAIT", true, null);
        pecEnBase(CENTRE_A, "A1", p, "VALIDEE", jour(-5), jour(5), null, jour(-5), jour(5), null, 1);
        attestation(CENTRE_A, p, jour(-5), jour(5));
        m.put("PEC validée couvrante sans forfait", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "CREE", true, null);
        pecEnBase(CENTRE_A, "A2", p, "CREE", jour(-5), jour(5), f3, null, null, null, 1);
        attestation(CENTRE_A, p, jour(-5), jour(5));
        m.put("PEC créée couvrante", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "VALIDEE_SANS_ATTESTATION", true, null);
        pecEnBase(CENTRE_A, "A3", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f3, 1);
        m.put("PEC validée, aucune attestation (même vacancier)", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "VALIDEE_ATTESTEE", false, null);
        pecEnBase(CENTRE_A, "A4", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f3, 1);
        attestation(CENTRE_A, p, jour(-5), jour(5));
        m.put("PEC validée et attestation valide", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "ATTESTATION_ECHUE", false, null);
        pecEnBase(CENTRE_A, "A5", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f3, 1);
        attestation(CENTRE_A, p, jour(-50), jour(-1));
        m.put("attestation échue hier", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "ATTESTATION_BORNE", false, null);
        pecEnBase(CENTRE_A, "A6", p, "VALIDEE", jour(-5), jour(5), f3, jour(-5), jour(5), f3, 1);
        attestation(CENTRE_A, p, jour(0), jour(0));
        m.put("attestation d'un seul jour (le jour même)", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "PEC_FUTURE", false, null);
        pecEnBase(CENTRE_A, "A7", p, "VALIDEE", jour(1), jour(9), f3, jour(1), jour(9), f3, 1);
        attestation(CENTRE_A, p, jour(-5), jour(5));
        m.put("PEC dont la période commence demain", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "BORNES_MELANGEES", false, null);
        pecEnBase(CENTRE_A, "A8", p, "VALIDEE", jour(-5), jour(-2), f3, jour(-1), null, f3, 1);
        attestation(CENTRE_A, p, jour(-5), jour(5));
        m.put("début effectif hier, fin effective ouverte, demande échue", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "EFFECTIVE_OUVERTE", false, null);
        pecEnBase(CENTRE_A, "A9", p, "VALIDEE", jour(-50), jour(-40), f3, jour(-5), null, f3, 1);
        attestation(CENTRE_A, p, jour(-5), jour(5));
        m.put("période effective ouverte, demande échue", causeDe(p, j));
        p = patientCouvert(CENTRE_A, "DEUX_PEC", false, null);
        pecEnBase(CENTRE_A, "B1", p, "CLOTUREE", jour(-5), jour(5), f3, jour(-5), jour(5), f3, 1);
        pecEnBase(CENTRE_A, "B2", p, "CREE", jour(-5), jour(5), f3, null, null, null, 2);
        attestation(CENTRE_A, p, jour(-5), jour(5));
        m.put("une clôturée et une créée, aucune validée", causeDe(p, j));
        arbre("cause_de_non_facturabilite", m);
    }

    @Test
    void le_cycle_de_la_pec_pilote_par_l_api_change_ce_que_lisent_les_autres_contextes() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "VACANCIER", true, null);
        UUID demande = forfait(CENTRE_A, "HD3", "9500.00");
        UUID accorde = forfait(CENTRE_A, "HD4", "11000.00");
        attestation(CENTRE_A, patient, jour(-50), jour(50));
        LocalDate j = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        Resp c = creerLaPec("PEC_1", patient, CENTRE_A, jour(-5), jour(25), demande, adminA());
        UUID pec = UUID.fromString(corps(c).path("id").asString());
        PecIdEtPatient lecteur = new PecIdEtPatient(pec, patient);
        m.put("1. demande créée", lecteur.lire(j));
        valider(pec, CENTRE_A, jour(-2), jour(20), accorde, adminA());
        m.put("2. validée avec un autre forfait", lecteur.lire(j));
        cloturer(pec, CENTRE_A, adminA());
        m.put("3. clôturée", lecteur.lire(j));
        supprimer(pec, CENTRE_A, patient, adminA());
        m.put("4. supprimée", lecteur.lire(j));
        arbre("de_la_demande_a_la_suppression_nouveau_code", m);
    }

    private final class PecIdEtPatient {
        private final UUID pec;
        private final UUID patient;

        PecIdEtPatient(UUID pec, UUID patient) {
            this.pec = pec;
            this.patient = patient;
        }

        Map<String, Object> lire(LocalDate j) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("forfait applicable (Absences)", forfaitDe(patient, j));
            try {
                Resp s = sessionAllowed(pec, CENTRE_A, adminA());
                r.put("route session-allowed (PEC)", s.status() + " " + corps(s).path("allowed").toString());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            r.put("cause de non-facturabilité (Séance)", causeDe(patient, j));
            return r;
        }
    }
}
