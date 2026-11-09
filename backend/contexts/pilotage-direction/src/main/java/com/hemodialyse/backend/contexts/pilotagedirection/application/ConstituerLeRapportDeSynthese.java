package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.EvolutionMensuelle;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.EvolutionMensuelle.PointDuMois;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.LibelleDAlerte;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MatriceDesCaisses;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MatriceDesCaisses.CelluleDeCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MatriceDesCaisses.CentreDeLaMatrice;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MatriceDesCaisses.TotalDeCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TrancheDAge;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.IndicateursDeLaPeriode;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.IndicateursDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Caisses;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Finances;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneClinique;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneDeCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneFinanciere;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneParAge;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneParSexe;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.LigneStock;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Mois;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.QualiteDesSoins;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Rapport;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Repartitions;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.SourceDuRapport;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Stock;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Synthese;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.PointMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Repartition;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.StatistiquesDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.VueDEnsemble;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Constituer le rapport de synthèse de la direction : le MODÈLE du document (sections, totaux, évolution mensuelle, matrice des caisses,
 * libellés d'alerte), reflet de l'anonymat de sa source — il n'imprime jamais un effectif que la source masque. Deux sources : les
 * données en direct d'une période, ou un contenu déjà figé (instantané mensuel, jamais recalculé). Le rendu PDF reste à la plateforme.
 * <p>
 * Les règles du rapport sont dans le domaine ({@link EvolutionMensuelle}, {@link MatriceDesCaisses}, {@link LibelleDAlerte}) ; ce cas
 * d'usage orchestre et recopie les valeurs sources dans les sections.
 */
public final class ConstituerLeRapportDeSynthese {

    public static final String TITRE_EN_DIRECT = "Rapport de direction";
    private static final String ABSENT = "—";

    private final TableauDeBordDeLaDirection tableau;
    private final IndicateursDeLaDirectionFournis indicateurs;
    private final HorlogeDuPilotage horloge;

    public ConstituerLeRapportDeSynthese(TableauDeBordDeLaDirection tableau, IndicateursDeLaDirectionFournis indicateurs,
                                         HorlogeDuPilotage horloge) {
        this.tableau = tableau;
        this.indicateurs = indicateurs;
        this.horloge = horloge;
    }

    /** Le rapport en direct d'une période ; les indicateurs et les répartitions portent sur la période de la vue d'ensemble. */
    public Rapport enDirect(UUID societeId, LocalDate from, LocalDate to) {
        VueDEnsemble vue = tableau.vueDEnsemble(societeId, from, to);
        IndicateursDeLaPeriode indicateursDeLaPeriode = indicateurs.pour(societeId, vue.from(), vue.to());
        Repartition repartition = tableau.repartition(societeId, vue.from(), vue.to());
        return constituer(new SourceDuRapport(TITRE_EN_DIRECT, horloge.maintenant(), vue, indicateursDeLaPeriode, repartition));
    }

    public Rapport constituer(SourceDuRapport source) {
        VueDEnsemble vue = source.vue();
        IndicateursDeLaPeriode ind = source.indicateurs();
        return new Rapport(source.titre(), vue.societeNom(), texte(vue.from()), texte(vue.to()), vue.seuilAnonymat(), source.genereLe(),
                synthese(vue.totaux()), alertes(ind), finances(vue), evolution(vue.mensuel()), qualite(ind), stock(ind),
                source.repartition() == null ? null : repartitions(source.repartition()));
    }

    private static String texte(Object valeur) {
        return valeur == null ? null : valeur.toString();
    }

    private static Synthese synthese(StatistiquesDuCentre t) {
        return t == null ? null : new Synthese(t.patients(), t.seances(), t.factures(), t.caHt(), t.caTtc(), t.encaisse(),
                t.resteARecouvrer(), t.tauxEncaissement());
    }

    private static List<Alerte> alertes(IndicateursDeLaPeriode ind) {
        List<Alerte> alertes = new ArrayList<>();
        if (ind == null || ind.alertes() == null) return alertes;
        ind.alertes().forEach(a -> alertes.add(new Alerte(a.centre(), LibelleDAlerte.de(a.code() == null ? ABSENT : a.code()),
                a.severity(), a.valeur())));
        return alertes;
    }

    private static LigneFinanciere ligne(StatistiquesDuCentre c) {
        return c == null ? new LigneFinanciere(null, null, null, null, null, null, null, null, null, null)
                : new LigneFinanciere(c.nom(), c.patients(), c.patientsSousKt(), c.seances(), c.factures(), c.caHt(), c.caTtc(),
                c.encaisse(), c.resteARecouvrer(), c.tauxEncaissement());
    }

    private static Finances finances(VueDEnsemble vue) {
        if (vue.centres() == null) return null;
        return new Finances(vue.centres().stream().map(ConstituerLeRapportDeSynthese::ligne).toList(), ligne(vue.totaux()));
    }

    private static List<Mois> evolution(List<PointMensuel> points) {
        if (points == null) return List.of();
        List<PointDuMois> source = points.stream().map(p -> new PointDuMois(p.mois() == null ? ABSENT : p.mois(),
                p.seances() == null ? 0 : p.seances(), p.caHt() == null ? 0 : p.caHt().doubleValue(),
                p.caTtc() == null ? 0 : p.caTtc().doubleValue())).toList();
        return EvolutionMensuelle.cumuler(source).stream().map(m -> new Mois(m.mois(), m.seances(), m.caHt(), m.caTtc())).toList();
    }

    private static LigneClinique clinique(IndicateursDuCentre c, String nom) {
        var k = c == null || c.clinique() == null ? null : c.clinique();
        if (k == null) return new LigneClinique(nom, null, null, null, null, null, null, null, null, null, null, null, null);
        return new LigneClinique(nom, k.ktV(), k.hemoglobine(), k.phosphore(), k.pth(), k.albumine(), k.vhbPositifs(),
                k.vhcPositifs(), k.vihPositifs(), k.patientsObservanceEnRetard(), k.greffeListeAttente(), k.greffeBilanEnCours(),
                k.greffesPeriode());
    }

    private static QualiteDesSoins qualite(IndicateursDeLaPeriode ind) {
        if (ind == null || ind.centres() == null) return null;
        return new QualiteDesSoins(ind.centres().stream().map(c -> clinique(c, c.nom())).toList(), clinique(ind.totaux(), null));
    }

    private static LigneStock ligneDeStock(IndicateursDuCentre c, String nom) {
        var s = c == null ? null : c.stock();
        return s == null ? new LigneStock(nom, null, null, null, null, null)
                : new LigneStock(nom, s.articlesActifs(), s.articlesSousSeuil(), s.lotsPerimes(), s.lotsPeremptionProche(), s.valeurStock());
    }

    private static Stock stock(IndicateursDeLaPeriode ind) {
        if (ind == null || ind.centres() == null) return null;
        return new Stock(ind.centres().stream().map(c -> ligneDeStock(c, c.nom())).toList(), ligneDeStock(ind.totaux(), null));
    }

    private static Repartitions repartitions(Repartition r) {
        List<LigneParSexe> sexe = new ArrayList<>();
        List<CentreDeLaMatrice> centres = new ArrayList<>();
        if (r.sexe() != null) {
            r.sexe().forEach(s -> {
                sexe.add(new LigneParSexe(s.nom(), s.masculin(), s.feminin(), s.autre()));
                centres.add(new CentreDeLaMatrice(s.centerId() == null ? ABSENT : s.centerId().toString(), s.nom() == null ? ABSENT : s.nom()));
            });
        }
        List<LigneParAge> ages = new ArrayList<>();
        if (r.ages() != null) {
            r.ages().forEach(a -> {
                Map<String, Long> tranches = new LinkedHashMap<>();
                for (TrancheDAge t : TrancheDAge.values()) {
                    if (a.tranches() == null) break;
                    a.tranches().stream().filter(x -> t.code().equals(x.code())).forEach(x -> tranches.put(t.code(), x.count()));
                }
                ages.add(new LigneParAge(a.nom(), tranches));
            });
        }
        List<LigneAnemie> anemie = new ArrayList<>();
        if (r.anemie() != null) {
            r.anemie().forEach(a -> anemie.add(new LigneAnemie(a.nom(), a.patientsSousEpo(), a.patientsSousFer(), a.patientsEpo(),
                    a.patientsFer(), a.administreesEpo(), a.administreesFer(), a.nonAdministrees(), a.tauxAdministration())));
        }
        return new Repartitions(sexe, ages, caisses(r, centres), anemie);
    }

    private static Caisses caisses(Repartition r, List<CentreDeLaMatrice> centres) {
        if (r.caisseTotaux() == null || r.caisseTotaux().isEmpty()) return null;
        List<CelluleDeCaisse> cellules = new ArrayList<>();
        if (r.caisses() != null) {
            r.caisses().forEach(c -> cellules.add(new CelluleDeCaisse(c.centerId() == null ? ABSENT : c.centerId().toString(),
                    (c.caisseCode() == null ? ABSENT : c.caisseCode()).replace(ABSENT, ""), c.patients(), c.seances(), c.caHt())));
        }
        List<TotalDeCaisse> totaux = r.caisseTotaux().stream().map(t -> new TotalDeCaisse(t.caisseCode() == null ? ABSENT : t.caisseCode(),
                t.caisse(), t.patients(), t.seances(), t.caHt())).toList();
        List<LigneDeCaisse> lignes = MatriceDesCaisses.constituer(centres, cellules, totaux).stream()
                .map(l -> new LigneDeCaisse(l.caisse(), l.patients(), l.totalPatients(), l.seances(), l.totalSeances(), l.caHt(),
                        l.totalCaHt())).toList();
        return new Caisses(centres.stream().map(CentreDeLaMatrice::nom).toList(), lignes);
    }
}
