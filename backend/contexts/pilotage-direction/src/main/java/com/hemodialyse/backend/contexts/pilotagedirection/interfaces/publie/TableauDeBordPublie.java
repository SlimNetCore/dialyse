package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaRepartition;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaVueDEnsemble;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.BilanDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SeuilDAnonymat;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TrancheDAge;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.VueDEnsembleDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adaptateur entrant du langage publié {@link TableauDeBordDeLaDirection} : appelle les cas d'usage et traduit le domaine vers les
 * enregistrements publiés (mêmes noms de champs que le JSON servi, ADR 0102). C'est la plateforme (contrôleurs, instantanés, temps réel,
 * rapport) qui l'appelle ; le contrôle d'accès est le sien.
 */
public final class TableauDeBordPublie implements TableauDeBordDeLaDirection {

    private final ConsulterLaSociete societe;
    private final ConsulterLaVueDEnsemble vueDEnsemble;
    private final ConsulterLaRepartition repartition;

    public TableauDeBordPublie(ConsulterLaSociete societe, ConsulterLaVueDEnsemble vueDEnsemble, ConsulterLaRepartition repartition) {
        this.societe = societe;
        this.vueDEnsemble = vueDEnsemble;
        this.repartition = repartition;
    }

    @Override
    public SocieteDeLaDirection societe(UUID societeId) {
        var s = societe.execute(societeId);
        return new SocieteDeLaDirection(s.id(), s.raisonSociale(),
                s.centres().stream().map(c -> new CentreDeLaSociete(c.id(), c.nom(), c.actif())).toList());
    }

    @Override
    public VueDEnsemble vueDEnsemble(UUID societeId, LocalDate from, LocalDate to) {
        VueDEnsembleDeLaSociete v = vueDEnsemble.execute(societeId, from, to);
        PeriodePrecedente precedente = null;
        if (v.precedente() != null) {
            var p = v.precedente();
            precedente = new PeriodePrecedente(p.periode().debut(), p.periode().fin(), p.seances(), p.finances().caHt(),
                    p.finances().caTtc(), p.finances().encaisse(), p.finances().resteARecouvrer(), p.finances().tauxEncaissement());
        }
        return new VueDEnsemble(v.societe().id(), v.societe().raisonSociale(), v.periode().debut(), v.periode().fin(), v.genereLe(),
                SeuilDAnonymat.SEUIL, v.centres().stream().map(TableauDeBordPublie::statistiques).toList(), statistiques(v.totaux()),
                v.mensuel().stream().map(m -> new PointMensuel(m.mois(), m.centreId(), m.seances(), m.caHt(), m.caTtc())).toList(),
                precedente);
    }

    private static StatistiquesDuCentre statistiques(BilanDuCentre b) {
        return new StatistiquesDuCentre(b.centreId(), b.nom(), b.actif(), b.patientsPublies(), b.patientsSousKtPublies(), b.seances(),
                b.factures(), b.finances().caHt(), b.finances().caTtc(), b.finances().encaisse(), b.finances().resteARecouvrer(),
                b.finances().tauxEncaissement());
    }

    @Override
    public Repartition repartition(UUID societeId, LocalDate from, LocalDate to) {
        RepartitionDeLaSociete r = repartition.execute(societeId, from, to);
        List<RepartitionParAge> ages = new ArrayList<>();
        List<RepartitionParSexe> sexe = new ArrayList<>();
        for (var p : r.profils()) {
            sexe.add(new RepartitionParSexe(p.centreId(), p.nom(), p.masculinPublie(), p.femininPublie(), p.autrePublie()));
            List<Tranche> tranches = new ArrayList<>();
            p.tranchesPubliees().forEach((t, n) -> tranches.add(new Tranche(t.code(), n)));
            ages.add(new RepartitionParAge(p.centreId(), p.nom(), tranches));
        }
        return new Repartition(r.societe().id(), r.periode().debut(), r.periode().fin(), r.genereLe(), SeuilDAnonymat.SEUIL, sexe, ages,
                r.caisses().stream().map(c -> new CaisseDuCentre(c.centreId(), c.centre(), c.code(), c.nom(), c.patientsPublies(),
                        c.seances(), c.caHt())).toList(),
                r.caissesDeLaSociete().stream().map(c -> new CaisseDeLaSociete(c.code(), c.nom(), c.patientsPublies(), c.seances(),
                        c.caHt())).toList(),
                r.anemie().stream().map(a -> new TraitementDeLAnemie(a.centreId(), a.nom(), a.patientsEpoPublies(),
                        a.patientsFerPublies(), a.administreesEpo(), a.administreesFer(), a.nonAdministrees(), a.tauxAdministration(),
                        a.sousEpoPublies(), a.sousFerPublies())).toList());
    }
}
