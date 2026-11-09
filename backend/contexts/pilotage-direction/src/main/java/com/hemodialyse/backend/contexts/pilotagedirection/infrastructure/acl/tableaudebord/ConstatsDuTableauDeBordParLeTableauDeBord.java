package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.tableaudebord;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConstatsDuTableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.GraviteDAlerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Indicateurs;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.IndicateursDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Marqueur;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.CaisseDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Repartition;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.StatistiquesDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.VueDEnsemble;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs.ALERTES;
import static com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs.ANEMIE;
import static com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs.CAISSES;
import static com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs.CLINIQUE;
import static com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs.FINANCE;
import static com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs.PATIENTS;
import static com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs.SEANCES;
import static com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs.STOCK;

/**
 * Adaptateur sortant (ACL) : relit le tableau de bord de la société sur la période par défaut (vue d'ensemble et répartitions du Pilotage,
 * indicateurs que le Pilotage compose lui-même, PD6) et en fait un constat. Les noms des indicateurs ({@code patients}, {@code caTtc},
 * {@code ktV:dansCible}, {@code age:<tranche>}, {@code seances:<caisse>}...) sont ceux des changements diffusés au client : valeurs de contrat,
 * repris à l'identique de l'ancien {@code DirectionRealtimeService.snapshot}. Un indicateur absent (masqué) n'est pas retenu.
 */
public final class ConstatsDuTableauDeBordParLeTableauDeBord implements ConstatsDuTableauDeBord {

    private final TableauDeBordDeLaDirection tableau;
    private final IndicateursDeLaDirection indicateurs;

    public ConstatsDuTableauDeBordParLeTableauDeBord(TableauDeBordDeLaDirection tableau, IndicateursDeLaDirection indicateurs) {
        this.tableau = tableau;
        this.indicateurs = indicateurs;
    }

    @Override
    public Lecture lire(UUID societeId) {
        VueDEnsemble vue = tableau.vueDEnsemble(societeId, null, null);
        Indicateurs ind = indicateurs.indicateurs(societeId, null, null);
        Repartition repartition = tableau.repartition(societeId, null, null);
        return new Lecture(constater(vue, ind, repartition), ind.alertes().stream().map(ConstatsDuTableauDeBordParLeTableauDeBord::lever).toList());
    }

    static AlerteLevee lever(Alerte a) {
        return new AlerteLevee(a.centerId(), a.centre(), a.code(), GraviteDAlerte.valueOf(a.severity().name()), a.valeur());
    }

    static ConstatDesIndicateurs constater(VueDEnsemble vue, Indicateurs ind, Repartition repartition) {
        ConstatDesIndicateurs s = new ConstatDesIndicateurs();
        for (StatistiquesDuCentre c : vue.centres()) {
            s.retenir(c.centerId(), c.nom(), PATIENTS, "patients", c.patients());
            s.retenir(c.centerId(), c.nom(), PATIENTS, "patientsSousKt", c.patientsSousKt());
            s.retenir(c.centerId(), c.nom(), SEANCES, "seances", c.seances());
            s.retenir(c.centerId(), c.nom(), FINANCE, "factures", c.factures());
            s.retenir(c.centerId(), c.nom(), FINANCE, "caTtc", c.caTtc());
            s.retenir(c.centerId(), c.nom(), FINANCE, "encaisse", c.encaisse());
            s.retenir(c.centerId(), c.nom(), FINANCE, "resteARecouvrer", c.resteARecouvrer());
        }
        for (IndicateursDuCentre c : ind.centres()) {
            var k = c.clinique();
            marqueur(s, c, "ktV", k.ktV());
            marqueur(s, c, "hemoglobine", k.hemoglobine());
            marqueur(s, c, "phosphore", k.phosphore());
            marqueur(s, c, "pth", k.pth());
            marqueur(s, c, "albumine", k.albumine());
            s.retenir(c.centerId(), c.nom(), CLINIQUE, "vhbPositifs", k.vhbPositifs());
            s.retenir(c.centerId(), c.nom(), CLINIQUE, "vhcPositifs", k.vhcPositifs());
            s.retenir(c.centerId(), c.nom(), CLINIQUE, "vihPositifs", k.vihPositifs());
            s.retenir(c.centerId(), c.nom(), CLINIQUE, "observanceEnRetard", k.patientsObservanceEnRetard());
            s.retenir(c.centerId(), c.nom(), CLINIQUE, "greffeListeAttente", k.greffeListeAttente());
            s.retenir(c.centerId(), c.nom(), CLINIQUE, "greffesPeriode", k.greffesPeriode());
            var st = c.stock();
            s.retenir(c.centerId(), c.nom(), STOCK, "articlesSousSeuil", st.articlesSousSeuil());
            s.retenir(c.centerId(), c.nom(), STOCK, "lotsPerimes", st.lotsPerimes());
            s.retenir(c.centerId(), c.nom(), STOCK, "lotsPeremptionProche", st.lotsPeremptionProche());
            s.retenir(c.centerId(), c.nom(), STOCK, "valeurStock", st.valeurStock());
        }
        ind.alertes().forEach(a -> s.retenir(a.centerId(), a.centre(), ALERTES, a.code(),
                a.valeur() == null ? BigDecimal.ONE : a.valeur()));

        repartition.sexe().forEach(r -> {
            s.retenir(r.centerId(), r.nom(), PATIENTS, "masculin", r.masculin());
            s.retenir(r.centerId(), r.nom(), PATIENTS, "feminin", r.feminin());
        });
        repartition.ages().forEach(r -> r.tranches().forEach(t ->
                s.retenir(r.centerId(), r.nom(), PATIENTS, "age:" + t.code(), t.count())));
        for (CaisseDuCentre r : repartition.caisses()) {
            String cle = r.caisseCode().isEmpty() ? "?" : r.caisseCode();
            s.retenir(r.centerId(), r.centre(), CAISSES, "seances:" + cle, r.seances());
            s.retenir(r.centerId(), r.centre(), CAISSES, "caHt:" + cle, r.caHt());
            s.retenir(r.centerId(), r.centre(), CAISSES, "patients:" + cle, r.patients());
        }
        repartition.anemie().forEach(r -> {
            s.retenir(r.centerId(), r.nom(), ANEMIE, "administreesEpo", r.administreesEpo());
            s.retenir(r.centerId(), r.nom(), ANEMIE, "administreesFer", r.administreesFer());
            s.retenir(r.centerId(), r.nom(), ANEMIE, "nonAdministrees", r.nonAdministrees());
            s.retenir(r.centerId(), r.nom(), ANEMIE, "patientsEpo", r.patientsEpo());
            s.retenir(r.centerId(), r.nom(), ANEMIE, "patientsFer", r.patientsFer());
        });
        return s;
    }

    private static void marqueur(ConstatDesIndicateurs s, IndicateursDuCentre c, String nom, Marqueur m) {
        s.retenir(c.centerId(), c.nom(), CLINIQUE, nom + ":dansCible", m.pctDansCible());
        s.retenir(c.centerId(), c.nom(), CLINIQUE, nom + ":evalues", m.evalues());
    }
}
