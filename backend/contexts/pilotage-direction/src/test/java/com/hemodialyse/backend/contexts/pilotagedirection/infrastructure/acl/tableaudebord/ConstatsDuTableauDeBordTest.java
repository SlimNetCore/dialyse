package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.tableaudebord;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConstatsDuTableauDeBord.Lecture;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Changement;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.GraviteDAlerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Gravite;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Clinique;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Indicateurs;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.IndicateursDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Marqueur;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Stock;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.CaisseDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Repartition;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.RepartitionParAge;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.RepartitionParSexe;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.StatistiquesDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Tranche;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.TraitementDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.VueDEnsemble;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Traduction table-driven du tableau de bord en constat : chaque ligne de la table est (famille, nom) d'un changement diffusé au client. Les
 * noms sont des valeurs de contrat (repris de l'ancien {@code DirectionRealtimeService.snapshot}).
 */
class ConstatsDuTableauDeBordTest {

    private static final UUID SOCIETE = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static VueDEnsemble vue(long base) {
        StatistiquesDuCentre c = new StatistiquesDuCentre(CENTRE, "Centre", true, base, base, base, base, BigDecimal.valueOf(base),
                BigDecimal.valueOf(base), BigDecimal.valueOf(base), BigDecimal.valueOf(base), null);
        return new VueDEnsemble(SOCIETE, "Société", null, null, null, 5, List.of(c), null, List.of(), null);
    }

    private static Indicateurs indicateurs(long base, List<Alerte> alertes) {
        Marqueur m = new Marqueur(base, BigDecimal.valueOf(base), BigDecimal.valueOf(base), BigDecimal.valueOf(base));
        Clinique k = new Clinique(m, m, m, m, m, base, base, base, base, base, base, base);
        Stock s = new Stock(base, base, base, base, BigDecimal.valueOf(base));
        IndicateursDuCentre centre = new IndicateursDuCentre(CENTRE, "Centre", true, k, s);
        return new Indicateurs(SOCIETE, null, null, null, 5, List.of(centre), centre, alertes);
    }

    private static Repartition repartition(long base, String caisseCode) {
        return new Repartition(SOCIETE, null, null, null, 5,
                List.of(new RepartitionParSexe(CENTRE, "Centre", base, base, base)),
                List.of(new RepartitionParAge(CENTRE, "Centre", List.of(new Tranche("20-39", base)))),
                List.of(new CaisseDuCentre(CENTRE, "Centre", caisseCode, "Caisse", base, base, BigDecimal.valueOf(base))),
                List.of(),
                List.of(new TraitementDeLAnemie(CENTRE, "Centre", base, base, base, base, base, null, base, base)));
    }

    private static List<String> noms(ConstatDesIndicateurs avant, ConstatDesIndicateurs apres) {
        List<String> l = new ArrayList<>();
        for (Changement c : apres.changementsDepuis(avant)) l.add(c.famille() + "/" + c.nom());
        return l;
    }

    @Test
    void chaque_indicateur_affiche_porte_la_famille_et_le_nom_du_contrat_avec_le_client() {
        ConstatDesIndicateurs avant = ConstatsDuTableauDeBordParLeTableauDeBord.constater(vue(1), indicateurs(1, List.of()), repartition(1, "CNAS"));
        ConstatDesIndicateurs apres = ConstatsDuTableauDeBordParLeTableauDeBord.constater(vue(2), indicateurs(2, List.of()), repartition(2, "CNAS"));

        assertEquals(List.of(
                "PATIENTS/patients", "PATIENTS/patientsSousKt", "SEANCES/seances", "FINANCE/factures", "FINANCE/caTtc", "FINANCE/encaisse",
                "FINANCE/resteARecouvrer",
                "CLINIQUE/ktV:dansCible", "CLINIQUE/ktV:evalues", "CLINIQUE/hemoglobine:dansCible", "CLINIQUE/hemoglobine:evalues",
                "CLINIQUE/phosphore:dansCible", "CLINIQUE/phosphore:evalues", "CLINIQUE/pth:dansCible", "CLINIQUE/pth:evalues",
                "CLINIQUE/albumine:dansCible", "CLINIQUE/albumine:evalues", "CLINIQUE/vhbPositifs", "CLINIQUE/vhcPositifs",
                "CLINIQUE/vihPositifs", "CLINIQUE/observanceEnRetard", "CLINIQUE/greffeListeAttente", "CLINIQUE/greffesPeriode",
                "STOCK/articlesSousSeuil", "STOCK/lotsPerimes", "STOCK/lotsPeremptionProche", "STOCK/valeurStock",
                "PATIENTS/masculin", "PATIENTS/feminin", "PATIENTS/age:20-39",
                "CAISSES/seances:CNAS", "CAISSES/caHt:CNAS", "CAISSES/patients:CNAS",
                "ANEMIE/administreesEpo", "ANEMIE/administreesFer", "ANEMIE/nonAdministrees", "ANEMIE/patientsEpo", "ANEMIE/patientsFer"),
                noms(avant, apres));
    }

    @Test
    void une_caisse_sans_code_est_nommee_point_d_interrogation() {
        ConstatDesIndicateurs avant = ConstatsDuTableauDeBordParLeTableauDeBord.constater(vue(1), indicateurs(1, List.of()), repartition(1, ""));
        ConstatDesIndicateurs apres = ConstatsDuTableauDeBordParLeTableauDeBord.constater(vue(1), indicateurs(1, List.of()), repartition(2, ""));
        assertTrue(noms(avant, apres).contains("CAISSES/seances:?"));
    }

    @Test
    void une_alerte_sans_valeur_vaut_un_dans_le_constat_et_les_valeurs_masquees_sont_absentes() {
        Alerte observance = new Alerte(CENTRE, "Centre", "OBSERVANCE_EN_RETARD", Gravite.WARNING, null);
        ConstatDesIndicateurs sans = ConstatsDuTableauDeBordParLeTableauDeBord.constater(vue(1), indicateurs(1, List.of()), repartition(1, "X"));
        ConstatDesIndicateurs avec = ConstatsDuTableauDeBordParLeTableauDeBord.constater(vue(1), indicateurs(1, List.of(observance)), repartition(1, "X"));

        List<Changement> changements = avec.changementsDepuis(sans);
        assertEquals(1, changements.size());
        assertEquals("ALERTES", changements.get(0).famille());
        assertEquals("OBSERVANCE_EN_RETARD", changements.get(0).nom());
        assertEquals(0, changements.get(0).avant().signum());
        assertEquals(0, BigDecimal.ONE.compareTo(changements.get(0).apres()));
    }

    @Test
    void un_effectif_masque_n_est_pas_dans_le_constat_donc_jamais_diffuse() {
        StatistiquesDuCentre masque = new StatistiquesDuCentre(CENTRE, "Centre", true, null, null, 3L, 1L, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, null);
        VueDEnsemble vueMasquee = new VueDEnsemble(SOCIETE, "Société", null, null, null, 5, List.of(masque), null, List.of(), null);
        ConstatDesIndicateurs avant = ConstatsDuTableauDeBordParLeTableauDeBord.constater(vueMasquee, indicateurs(1, List.of()), repartition(1, "X"));
        ConstatDesIndicateurs apres = ConstatsDuTableauDeBordParLeTableauDeBord.constater(vue(9), indicateurs(1, List.of()), repartition(1, "X"));

        assertTrue(noms(avant, apres).stream().noneMatch(n -> n.equals("PATIENTS/patients") || n.equals("PATIENTS/patientsSousKt")));
    }

    @Test
    void la_gravite_publiee_est_traduite_en_gravite_du_domaine() {
        assertEquals(GraviteDAlerte.CRITICAL, ConstatsDuTableauDeBordParLeTableauDeBord
                .lever(new Alerte(CENTRE, "Centre", "STOCK_SOUS_SEUIL", Gravite.CRITICAL, BigDecimal.ONE)).gravite());
        assertEquals(GraviteDAlerte.WARNING, ConstatsDuTableauDeBordParLeTableauDeBord
                .lever(new Alerte(CENTRE, "Centre", "HB_HORS_CIBLE", Gravite.WARNING, BigDecimal.ONE)).gravite());
    }

    @Test
    void la_lecture_range_les_alertes_levees_dans_l_ordre_des_indicateurs_fournis() {
        IndicateursDeLaDirection fournis = (societeId, from, to) -> indicateurs(1, List.of(
                new Alerte(CENTRE, "Centre", "STOCK_SOUS_SEUIL", Gravite.CRITICAL, BigDecimal.ONE),
                new Alerte(CENTRE, "Centre", "LOTS_PERIMES", Gravite.CRITICAL, BigDecimal.TEN)));
        var tableau = new com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection() {
            @Override
            public SocieteDeLaDirection societe(UUID societeId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public VueDEnsemble vueDEnsemble(UUID societeId, java.time.LocalDate from, java.time.LocalDate to) {
                return vue(1);
            }

            @Override
            public Repartition repartition(UUID societeId, java.time.LocalDate from, java.time.LocalDate to) {
                return ConstatsDuTableauDeBordTest.repartition(1, "X");
            }
        };

        Lecture lecture = new ConstatsDuTableauDeBordParLeTableauDeBord(tableau, fournis).lire(SOCIETE);
        assertEquals(List.of("STOCK_SOUS_SEUIL", "LOTS_PERIMES"), lecture.alertes().stream().map(a -> a.code()).toList());
    }
}
