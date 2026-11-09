package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.LecturesDesIndicateursCliniques.Cible;
import com.hemodialyse.backend.contexts.dossiermedical.application.LecturesDesIndicateursCliniques.Greffes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EffectifMasque;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicateurs cliniques des centres d'une société pour la Direction (RG-DIR, RG-MED-081) : répartition des dernières valeurs face aux cibles
 * KDIGO, porteurs positifs AgHBs / anti-VHC / VIH, retards d'observance, greffes ; par centre et consolidés. Repris mot pour mot de
 * {@code DirectionIndicatorsQueryService} (DM9, ADR 0101). REPRODUIT, à trancher par le DPO : les consolidés sont la somme des valeurs
 * BRUTES puis masqués ({@link EffectifMasque}) : la différence révèle un effectif masqué (KB-MED-104).
 */
public class CalculerLesIndicateursCliniques implements IndicateursCliniquesPublies {

    private final LecturesDesIndicateursCliniques lectures;

    public CalculerLesIndicateursCliniques(LecturesDesIndicateursCliniques lectures) {
        this.lectures = lectures;
    }

    @Override
    public IndicateursCliniquesDesCentres desCentres(List<UUID> centres, LocalDate du, LocalDate au) {
        Map<UUID, Brut> bruts = new LinkedHashMap<>();
        for (UUID centre : centres) bruts.put(centre, new Brut());
        if (!centres.isEmpty()) {
            for (Cible cible : Cible.values()) {
                lectures.dernieresValeurs(cible, centres, du, au).forEach((centre, valeurs) -> {
                    Brut b = bruts.get(centre);
                    if (b != null) b.valeurs.get(cible).addAll(valeurs);
                });
            }
            lectures.porteursPositifs(MarqueurSerologique.AG_HBS, centres, au).forEach((c, n) -> brut(bruts, c, b -> b.vhb = n));
            lectures.porteursPositifs(MarqueurSerologique.AC_VHC, centres, au).forEach((c, n) -> brut(bruts, c, b -> b.vhc = n));
            lectures.porteursPositifs(MarqueurSerologique.VIH_AC, centres, au).forEach((c, n) -> brut(bruts, c, b -> b.vih = n));
            lectures.patientsEnRetardDObservance(centres).forEach((c, n) -> brut(bruts, c, b -> b.observance = n));
            lectures.greffes(centres, du, au).forEach((c, g) -> brut(bruts, c, b -> {
                b.greffeAttente = g.listeAttente();
                b.greffeBilan = g.bilanEnCours();
                b.greffes = g.greffesPeriode();
            }));
        }
        Brut total = new Brut();
        Map<UUID, IndicateursCliniques> parCentre = new LinkedHashMap<>();
        bruts.forEach((centre, b) -> {
            parCentre.put(centre, b.publier());
            total.fusionner(b);
        });
        return new IndicateursCliniquesDesCentres(parCentre, total.publier());
    }

    private static void brut(Map<UUID, Brut> bruts, UUID centre, java.util.function.Consumer<Brut> maj) {
        Brut b = bruts.get(centre);
        if (b != null) maj.accept(b);
    }

    private static final class Brut {
        final Map<Cible, List<BigDecimal>> valeurs = new EnumMap<>(Cible.class);
        long vhb, vhc, vih, observance, greffeAttente, greffeBilan, greffes;

        Brut() {
            for (Cible c : Cible.values()) valeurs.put(c, new ArrayList<>());
        }

        void fusionner(Brut o) {
            for (Cible c : Cible.values()) valeurs.get(c).addAll(o.valeurs.get(c));
            vhb += o.vhb;
            vhc += o.vhc;
            vih += o.vih;
            observance += o.observance;
            greffeAttente += o.greffeAttente;
            greffeBilan += o.greffeBilan;
            greffes += o.greffes;
        }

        IndicateursCliniques publier() {
            return new IndicateursCliniques(repartition(Cible.KT_V), repartition(Cible.HEMOGLOBINE), repartition(Cible.PHOSPHORE),
                    repartition(Cible.PTH), repartition(Cible.ALBUMINE),
                    EffectifMasque.masquer(vhb), EffectifMasque.masquer(vhc), EffectifMasque.masquer(vih),
                    EffectifMasque.masquer(observance), observance > 0,
                    EffectifMasque.masquer(greffeAttente), EffectifMasque.masquer(greffeBilan), EffectifMasque.masquer(greffes));
        }

        private IndicateursCliniquesPublies.RepartitionFaceALaCible repartition(Cible cible) {
            com.hemodialyse.backend.contexts.dossiermedical.domain.RepartitionFaceALaCible r =
                    com.hemodialyse.backend.contexts.dossiermedical.domain.RepartitionFaceALaCible.de(valeurs.get(cible), cible.regle());
            return new IndicateursCliniquesPublies.RepartitionFaceALaCible(r.evalues(), r.pctDansCible(), r.pctSousCible(), r.pctAuDessus());
        }
    }
}
