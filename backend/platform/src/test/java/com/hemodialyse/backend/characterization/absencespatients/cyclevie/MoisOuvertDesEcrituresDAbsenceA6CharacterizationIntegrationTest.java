package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice absences-patients-a6-cloture-mensuelle, filet « mois ouvert » : ce que les ecritures d'absence font AUJOURD'HUI pour
 * un mois qui sera un jour « clos » (aucune cloture d'absences n'existe encore). Verrouille (1) l'absence de tout lien entre la
 * cloture mensuelle de la Facturation (F7, {@code periodes_facturation}) et les ecritures directes d'absence (declaration,
 * qualification, rattrapage, annulation), (2) le mois que regarde le rattrapage (celui de l'absence, pas celui de la date de
 * rattrapage), (3) la cloture de facturation qui ignore les absences a qualifier. Dates : alias relatifs {@code <J-n>} ou jours
 * de calendrier NOMMES ({@code <DERNIER_JOUR_DU_MOIS-2>}) ; aucune chaine de mois (AAAA-MM) n'entre dans un golden.
 */
class MoisOuvertDesEcrituresDAbsenceA6CharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    private static final String PERIODES = "/api/v1/facturation/periodes/";

    @Override
    protected String theme() {
        return "mois-ouvert-a6";
    }

    // d'autres classes (exclusion) laissent des lignes de mois OUVERTES : on repart d'un etat propre avant comme apres
    @org.junit.jupiter.api.BeforeEach
    @AfterEach
    void nettoyerLaClotureDeFacturation() {
        jdbc.update("DELETE FROM periodes_facturation_journal WHERE center_id = ?", CENTRE_A);
        jdbc.update("DELETE FROM periodes_facturation WHERE center_id = ?", CENTRE_A);
    }

    /**
     * Cloture, comme le decor de F7, les mois de facturation du centre A qui contiennent ces jours.
     */
    private void cloreLaFacturationDesMoisDe(LocalDate... jours) {
        for (YearMonth m : Stream.of(jours).map(YearMonth::from).distinct().toList()) {
            jdbc.update("INSERT INTO periodes_facturation (center_id, annee, mois, statut, cloturee_par, cloturee_le) "
                            + "VALUES (?, ?, ?, 'CLOTURE', ?, ?)", CENTRE_A, m.getYear(), m.getMonthValue(), U_ADMIN,
                    OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    private int moisDeFacturationClos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM periodes_facturation WHERE center_id = ? AND statut = 'CLOTURE'",
                Integer.class, CENTRE_A);
    }

    /**
     * Jour de calendrier stable d'un jour d'execution a l'autre : nomme pour que le golden ne depende pas de la date.
     */
    private LocalDate nomme(LocalDate jour, String alias) {
        jourNomme(jour, alias);
        return jour;
    }

    @Test
    void la_cloture_de_facturation_du_mois_ne_ferme_ni_la_declaration_ni_la_qualification_ni_le_rattrapage_ni_l_annulation()
            throws Exception {
        LocalDate declaree = jour(3);
        UUID aQualifier = abs(1, P_BELKACEM, jour(8)).poser();
        UUID aRattraper = abs(2, P_CHIKHI, jour(9)).poser();
        UUID aAnnuler = abs(3, P_DAHMANI, jour(10)).poser();
        seance(P_CHIKHI, CENTRE_A, jour(5), "VALIDEE");
        cloreLaFacturationDesMoisDe(declaree, jour(5), jour(8), jour(9), jour(10));

        ObjectNode o = mapper.createObjectNode();
        o.set("declaration", resume(declarer(P_AMRANI, declaree, "MALADIE")));
        nommerLesAbsences(CENTRE_A);
        o.set("qualification", resume(qualifier(admin(), aQualifier, "VOYAGE", null)));
        o.set("rattrapage", resume(rattraper(admin(), aRattraper, jour(5))));
        o.set("annulation", resume(annuler(admin(), aAnnuler, "saisie en double")));
        o.set("base", etat(CENTRE_A));
        goldenNode("facturation_close_ecritures_directes_permises", o);
        assertTrue(moisDeFacturationClos() >= 1);
    }

    @Test
    void le_rattrapage_ne_regarde_que_le_mois_de_l_absence_pas_celui_de_la_date_de_rattrapage() throws Exception {
        YearMonth moisCourant = YearMonth.from(aujourdhui());
        LocalDate absence = nomme(moisCourant.minusMonths(2).atEndOfMonth(), "<DERNIER_JOUR_DU_MOIS-2>");
        LocalDate rattrapage = nomme(moisCourant.minusMonths(1).atDay(1), "<PREMIER_JOUR_DU_MOIS-1>");
        UUID id = abs(1, P_AMRANI, absence).poser();
        seance(P_AMRANI, CENTRE_A, rattrapage, "VALIDEE");
        // le mois de la date de rattrapage est clos cote Facturation, celui de l'absence reste ouvert : le rattrapage passe
        cloreLaFacturationDesMoisDe(rattrapage);

        ObjectNode o = mapper.createObjectNode();
        o.set("rattrapage", resume(rattraper(admin(), id, rattrapage)));
        o.set("base", etat(CENTRE_A));
        goldenNode("rattrapage_dans_un_autre_mois_que_l_absence", o);
    }

    @Test
    void l_annulation_et_la_qualification_d_une_absence_d_un_ancien_mois_sont_permises_quel_que_soit_l_age() throws Exception {
        YearMonth moisCourant = YearMonth.from(aujourdhui());
        LocalDate ancienne = nomme(moisCourant.minusMonths(3).atDay(15), "<QUINZE_DU_MOIS-3>");
        LocalDate autre = nomme(moisCourant.minusMonths(4).atDay(15), "<QUINZE_DU_MOIS-4>");
        UUID aQualifier = abs(1, P_AMRANI, ancienne).poser();
        UUID aAnnuler = abs(2, P_BELKACEM, autre).poser();

        ObjectNode o = mapper.createObjectNode();
        o.set("qualification", resume(qualifier(admin(), aQualifier, "MALADIE", null)));
        o.set("annulation", resume(annuler(admin(), aAnnuler, "doublon")));
        o.set("base", etat(CENTRE_A));
        goldenNode("anciens_mois_ecritures_permises", o);
    }

    @Test
    void la_cloture_de_facturation_ne_depend_pas_des_absences_a_qualifier_du_mois_et_les_laisse_intactes() throws Exception {
        // ADR 0044 : seul un mois terminé se clôture ; l'absence est donc dans le mois précédent (jour nommé, stable d'un jour à l'autre)
        LocalDate finDuMoisPrecedent = YearMonth.from(aujourdhui()).minusMonths(1).atEndOfMonth();
        jourNomme(finDuMoisPrecedent, "<FIN_DU_MOIS_PRECEDENT>");
        UUID id = abs(1, P_AMRANI, finDuMoisPrecedent).poser();
        String mois = YearMonth.from(finDuMoisPrecedent).toString();

        var cloture = doPost(PERIODES + mois + "/cloturer", json("{}"), admin());

        ObjectNode o = mapper.createObjectNode();
        o.put("status", cloture.status());
        var corps = json(cloture);
        o.put("statut", corps.path("statut").asString());
        o.put("seancesEligiblesRestantes", corps.path("seancesEligiblesRestantes").asInt(-1));
        ArrayNode champs = mapper.createArrayNode();
        corps.propertyNames().stream().sorted().forEach(champs::add);
        o.set("champs", champs);
        o.set("base", etat(CENTRE_A));
        o.put("absencesDuMoisNonCloses", corps.path("absencesDuMoisNonCloses").asBoolean(false));
        // écart décidé (A6, ADR 0042) : champ additif d'avertissement ; l'ancien golden est gardé dans _ecarts-legacy
        goldenNode("cloture_de_facturation_avec_absence_a_qualifier_nouveau_code", o);
        assertEquals(1, moisDeFacturationClos());
        assertEquals(List.of("A_QUALIFIER"),
                jdbc.queryForList("SELECT statut FROM absence_patient WHERE id = ?", String.class, id));
    }
}
