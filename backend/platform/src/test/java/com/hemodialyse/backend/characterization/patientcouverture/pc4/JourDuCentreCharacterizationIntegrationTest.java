package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC4 (KB-PAT-47, revue du 2026-11-04) : le tableau de bord et la séance sans date utilisent le jour DU CENTRE, jamais le jour de la
 * JVM. L'horloge ne se commande pas dans le contexte Spring : on donne au centre A un fuseau dont le jour DIFFÈRE du jour de la
 * JVM à l'instant du test (parmi UTC+14 et UTC-12, toujours distincts de 26 heures, l'un des deux diffère de la JVM), puis on
 * vérifie que les compteurs et la date de la séance suivent le fuseau du centre. Les goldens ne portent que des booléens (aucune
 * date, déterministes) ; le fuseau du centre est restauré après chaque test.
 */
class JourDuCentreCharacterizationIntegrationTest extends AttestationCharacterizationSupport {

    private String fuseauInitial;
    private boolean fuseauModifie;

    @Override
    protected String dossier() {
        return "jour_du_centre";
    }

    @AfterEach
    void restaurerLeFuseau() {
        if (!fuseauModifie) return;
        jdbc.update("UPDATE centers SET fuseau_horaire = ? WHERE id = ?", fuseauInitial, CENTRE_A);
        fuseauModifie = false;
    }

    /** L'effectif du centre A un jour donné, par la règle de référence (l'ancien prédicat SQL d'effectif). */
    private long effectifAu(LocalDate jour) {
        var effectif = EffectifDeReference.presentSur("p", jour, jour);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(CENTRE_A);
        args.addAll(effectif.args());
        return jdbc.queryForObject("SELECT COUNT(1) FROM patients p WHERE p.center_id = ? AND " + effectif.sql(), Long.class, args.toArray());
    }

    /** Donne au centre A un fuseau dont le jour diffère de celui de la JVM et rend ce fuseau. */
    private ZoneId fuseauDifferentDeLaJvm() {
        LocalDate jvm = LocalDate.now();
        ZoneId choisi = List.of("Pacific/Kiritimati", "Etc/GMT+12").stream().map(ZoneId::of)
                .filter(z -> !LocalDate.now(z).equals(jvm)).findFirst().orElseThrow();
        List<String> anciens = jdbc.queryForList("SELECT fuseau_horaire FROM centers WHERE id = ?", String.class, CENTRE_A);
        Assumptions.assumeTrue(!anciens.isEmpty(), "le harnais ne porte pas de ligne centers pour le centre A");
        fuseauInitial = anciens.get(0);
        jdbc.update("UPDATE centers SET fuseau_horaire = ? WHERE id = ?", choisi.getId(), CENTRE_A);
        fuseauModifie = true;
        return choisi;
    }

    @Test
    void le_compteur_a_echeance_du_tableau_de_bord_suit_le_jour_du_centre_et_non_celui_de_la_jvm() throws Exception {
        ZoneId fuseau = fuseauDifferentDeLaJvm();
        LocalDate centre = LocalDate.now(fuseau);
        LocalDate jvm = LocalDate.now();
        UUID p = patientCouvert(CENTRE_A, "JOUR_TABLEAU", false, "ENFANT");
        attestationEnBase(CENTRE_A, "JC1", p, centre.minusDays(5), centre.plusDays(30));
        attestationEnBase(CENTRE_A, "JC2", p, jvm.minusDays(5), jvm.plusDays(30));
        // seuil du centre = centre + 30 : la ligne (centre + 30) y est, la ligne (jvm + 30) y est seulement si le centre est en avance
        long attendu = centre.isAfter(jvm) ? 2 : 1;
        // seuil de la JVM (ancien comportement) : le résultat inverse
        long ancien = centre.isAfter(jvm) ? 1 : 2;

        Resp get = doGet("/api/v1/dashboard/stats?centerId=" + CENTRE_A + "&expirationDays=30", adminA());
        Resp post = doPost("/api/v1/dashboard/stats/search", json("{'centerId':'" + CENTRE_A + "','expirationDays':30}"), adminA());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("le jour du centre diffère du jour de la JVM", !centre.equals(jvm));
        m.put("les deux comptes possibles diffèrent (le test discrimine)", attendu != ancien);
        m.put("GET stats : compteur à échéance = comptage au jour du centre", corps(get).path("attestationExpiring").asLong() == attendu);
        m.put("GET stats : le compteur n'est pas celui du jour de la JVM", corps(get).path("attestationExpiring").asLong() != ancien);
        m.put("POST stats/search : compteur à échéance = comptage au jour du centre", corps(post).path("attestationExpiring").asLong() == attendu);
        m.put("attestations comptées au total", corps(get).path("attestationTotal").asLong() == 2);
        arbre("echeance_au_jour_du_centre", m);
    }

    /**
     * Revue du 2026-11-05 : le tableau de bord n'a plus qu'UN « aujourd'hui » (celui du centre) pour l'échéance ET pour l'effectif
     * (avant, l'effectif prenait le jour d'UTC). Un patient admis le plus grand des deux jours (centre, UTC) est présent au jour du
     * centre seulement si celui-ci est le plus tardif.
     */
    @Test
    void l_effectif_du_tableau_de_bord_est_compte_au_jour_du_centre_et_non_a_celui_d_utc() throws Exception {
        ZoneId fuseau = fuseauDifferentDeLaJvm();
        LocalDate centre = LocalDate.now(fuseau);
        LocalDate utc = LocalDate.now(java.time.ZoneOffset.UTC);
        Assumptions.assumeTrue(!centre.equals(utc), "le jour du centre égale le jour d'UTC (fuseau choisi)");
        UUID p = patientCouvert(CENTRE_A, "JOUR_EFFECTIF", false, "ENFANT");
        LocalDate admission = centre.isAfter(utc) ? centre : utc;
        jdbc.update("UPDATE patients SET date_admission = ? WHERE id = ?", java.sql.Date.valueOf(admission), p);
        viderLesCaches();
        long auJourDuCentre = effectifAu(centre);
        long auJourDUtc = effectifAu(utc);

        Resp r = doGet("/api/v1/dashboard/stats?centerId=" + CENTRE_A, adminA());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("le jour du centre diffère du jour d'UTC", !centre.equals(utc));
        m.put("les deux effectifs possibles diffèrent (le test discrimine)", auJourDuCentre != auJourDUtc);
        m.put("patientCount = effectif au jour du centre", corps(r).path("patientCount").asLong() == auJourDuCentre);
        m.put("patientCount n'est pas l'effectif au jour d'UTC", corps(r).path("patientCount").asLong() != auJourDUtc);
        arbre("effectif_au_jour_du_centre", m);
    }

    @Test
    void une_seance_creee_ou_remise_a_vide_sans_date_prend_le_jour_du_centre_et_non_celui_de_la_jvm() throws Exception {
        ZoneId fuseau = fuseauDifferentDeLaJvm();
        LocalDate centre = LocalDate.now(fuseau);
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        UUID p = patientCouvert(CENTRE_A, "JOUR_SEANCE", false, null);
        pecEnBase(CENTRE_A, "PEC_JOUR", p, "VALIDEE", jour(-200), jour(200), forfait, jour(-200), jour(200), forfait, 1);
        attestationEnBase(CENTRE_A, "JC_ATT", p, centre.minusDays(100), centre.plusDays(100));

        Resp creation = doPost("/api/v1/seances", json("{'centerId':'" + CENTRE_A + "','patientId':'" + p + "'}"), adminA());
        UUID seance = UUID.fromString(corps(creation).path("id").asString());
        LocalDate creee = jdbc.queryForObject("SELECT date_seance FROM seances WHERE id = ?", java.sql.Date.class, seance).toLocalDate();
        Resp avecDate = doPut("/api/v1/seances/" + seance, json("{'centerId':'" + CENTRE_A + "','dateSeance':'" + centre.minusDays(10) + "'}"), adminA());
        LocalDate apresDate = jdbc.queryForObject("SELECT date_seance FROM seances WHERE id = ?", java.sql.Date.class, seance).toLocalDate();
        Resp remiseAVide = doPut("/api/v1/seances/" + seance, json("{'centerId':'" + CENTRE_A + "'}"), adminA());
        LocalDate apresVide = jdbc.queryForObject("SELECT date_seance FROM seances WHERE id = ?", java.sql.Date.class, seance).toLocalDate();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("le jour du centre diffère du jour de la JVM", !centre.equals(LocalDate.now()));
        m.put("création sans date acceptée", refus(creation));
        m.put("date de la séance créée sans date = jour du centre", creee.equals(centre));
        m.put("date de la séance créée sans date ≠ jour de la JVM", !creee.equals(LocalDate.now()));
        m.put("changement de date explicite", refus(avecDate));
        m.put("date explicite conservée", apresDate.equals(centre.minusDays(10)));
        m.put("remise à vide acceptée", refus(remiseAVide));
        m.put("date remise à vide = jour du centre", apresVide.equals(centre));
        m.put("date remise à vide ≠ jour de la JVM", !apresVide.equals(LocalDate.now()));
        arbre("seance_sans_date_au_jour_du_centre", m);
    }
}
