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
 * {@code DirectionIndicatorsQueryService} (DM9, ADR 0101). CORRIGÉ le 2026-11-09 (KB-MED-104, ADR 0112, validation DPO attendue) : un
 * consolidé est supprimé (publié {@code null}) dès que l'un des centres qui le composent est masqué ({@link EffectifMasque#consolider}).
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
        Map<UUID, IndicateursCliniques> parCentre = new LinkedHashMap<>();
        bruts.forEach((centre, b) -> parCentre.put(centre, b.publier()));
        return new IndicateursCliniquesDesCentres(parCentre, consolider(bruts.values()));
    }

    /**
     * Les consolidés de la société, avec suppression complémentaire (KB-MED-104, corrigé le 2026-11-09) : chaque effectif consolidé est
     * publié {@code null} dès qu'un centre le porte masqué ; pour un marqueur, les patients évalués ET les taux sont supprimés ensemble
     * (le taux d'un total dont un centre est masqué permettrait la même déduction).
     */
    private static IndicateursCliniques consolider(java.util.Collection<Brut> centres) {
        Brut total = new Brut();
        for (Brut b : centres) total.fusionner(b);
        return new IndicateursCliniques(
                consolider(centres, total, Cible.KT_V), consolider(centres, total, Cible.HEMOGLOBINE),
                consolider(centres, total, Cible.PHOSPHORE), consolider(centres, total, Cible.PTH),
                consolider(centres, total, Cible.ALBUMINE),
                effectif(centres, b -> b.vhb), effectif(centres, b -> b.vhc), effectif(centres, b -> b.vih),
                effectif(centres, b -> b.observance), total.observance > 0,
                effectif(centres, b -> b.greffeAttente), effectif(centres, b -> b.greffeBilan), effectif(centres, b -> b.greffes));
    }

    private static Long effectif(java.util.Collection<Brut> centres, java.util.function.ToLongFunction<Brut> champ) {
        return EffectifMasque.consolider(centres.stream().map(b -> champ.applyAsLong(b)).toList());
    }

    private static IndicateursCliniquesPublies.RepartitionFaceALaCible consolider(java.util.Collection<Brut> centres, Brut total,
                                                                                 Cible cible) {
        boolean composeDeMasques = EffectifMasque.composeDeMasques(centres.stream().map(b -> (long) b.valeurs.get(cible).size()).toList());
        return composeDeMasques ? new IndicateursCliniquesPublies.RepartitionFaceALaCible(null, null, null, null)
                : total.repartition(cible);
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

        IndicateursCliniquesPublies.RepartitionFaceALaCible repartition(Cible cible) {
            com.hemodialyse.backend.contexts.dossiermedical.domain.RepartitionFaceALaCible r =
                    com.hemodialyse.backend.contexts.dossiermedical.domain.RepartitionFaceALaCible.de(valeurs.get(cible), cible.regle());
            return new IndicateursCliniquesPublies.RepartitionFaceALaCible(r.evalues(), r.pctDansCible(), r.pctSousCible(), r.pctAuDessus());
        }
    }
}
