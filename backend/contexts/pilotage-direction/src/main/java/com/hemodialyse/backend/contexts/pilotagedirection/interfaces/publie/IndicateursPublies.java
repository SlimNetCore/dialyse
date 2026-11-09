package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLesIndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.IndicateursCliniquesDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.IndicateursDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MarqueurClinique;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SeuilDAnonymat;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.StockDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Gravite;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;

import java.time.LocalDate;
import java.util.UUID;

/** Adaptateur entrant du langage publié {@link IndicateursDeLaDirection} : traduit les indicateurs du domaine en enregistrements du contrat. */
public final class IndicateursPublies implements IndicateursDeLaDirection {

    private final ConsulterLesIndicateursDeLaDirection consulter;

    public IndicateursPublies(ConsulterLesIndicateursDeLaDirection consulter) {
        this.consulter = consulter;
    }

    @Override
    public Indicateurs indicateurs(UUID societeId, LocalDate from, LocalDate to) {
        IndicateursDeLaSociete i = consulter.execute(societeId, from, to);
        return new Indicateurs(i.societeId(), i.periode().debut(), i.periode().fin(), i.genereLe(), SeuilDAnonymat.SEUIL,
                i.centres().stream().map(IndicateursPublies::publier).toList(), publier(i.totaux()),
                i.alertes().stream().map(IndicateursPublies::publier).toList());
    }

    static IndicateursDuCentre publier(com.hemodialyse.backend.contexts.pilotagedirection.domain.IndicateursDuCentre c) {
        return new IndicateursDuCentre(c.centreId(), c.nom(), c.actif(), publier(c.clinique()), publier(c.stock()));
    }

    private static Clinique publier(IndicateursCliniquesDuCentre k) {
        return new Clinique(publier(k.ktV()), publier(k.hemoglobine()), publier(k.phosphore()), publier(k.pth()), publier(k.albumine()),
                k.vhbPositifs(), k.vhcPositifs(), k.vihPositifs(), k.patientsObservanceEnRetard(), k.greffeListeAttente(),
                k.greffeBilanEnCours(), k.greffesPeriode());
    }

    private static Marqueur publier(MarqueurClinique m) {
        return new Marqueur(m.evalues(), m.pctDansCible(), m.pctSousCible(), m.pctAuDessus());
    }

    private static Stock publier(StockDuCentre s) {
        return new Stock(s.articlesActifs(), s.articlesSousSeuil(), s.lotsPerimes(), s.lotsProches(), s.valeurStock());
    }

    private static Alerte publier(AlerteLevee a) {
        return new Alerte(a.centreId(), a.centre(), a.code(), Gravite.valueOf(a.gravite().name()), a.valeur());
    }
}
