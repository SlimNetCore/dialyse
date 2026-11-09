package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CapaciteTheorique;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.BaseDeLaCapaciteTheorique;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteTheoriqueDesCentres;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lecture publiée de la capacité théorique d'un centre (pilotage de la Direction ; SE7f, ADR 0100) : la base (séries, patients par poste et
 * par série, file active de la période) est lue par {@link BaseDeLaCapaciteTheorique}, le calcul est celui du domaine
 * ({@link CapaciteTheorique}) ; les générateurs en service sont fournis par l'appelant (GMAO). Même résultat que l'ancien
 * {@code CapaciteTheoriqueCalculator} appelé par la plateforme (goldens P6 inchangés).
 */
public class ConsulterLaCapaciteTheorique implements CapaciteTheoriqueDesCentres {

    private final BaseDeLaCapaciteTheorique base;

    public ConsulterLaCapaciteTheorique(BaseDeLaCapaciteTheorique base) {
        this.base = base;
    }

    @Override
    public Capacite duCentre(UUID centre, int generateursEnService, LocalDate du, LocalDate au) {
        BaseDeLaCapaciteTheorique.Base b = base.duCentre(centre, du, au);
        return publiee(CapaciteTheorique.calculer(generateursEnService, b.creneaux(), b.patientsParPosteEtSerie(), b.fileActive()));
    }

    @Override
    public Capacite consolidee(List<Capacite> centres) {
        return publiee(CapaciteTheorique.agreger(centres.stream().map(ConsulterLaCapaciteTheorique::duDomaine).toList()));
    }

    static Capacite publiee(CapaciteTheorique.Resultat r) {
        return new Capacite(r.generateurs(), r.generateursSecours(), r.postesActifs(), r.series(), r.patientsParPosteEtSerie(), r.capacite(),
                r.fileActive(), r.tauxOccupation(), Niveau.valueOf(r.niveau().name()));
    }

    static CapaciteTheorique.Resultat duDomaine(Capacite c) {
        return new CapaciteTheorique.Resultat(c.generateurs(), c.generateursSecours(), c.postesActifs(), c.series(), c.patientsParPosteEtSerie(),
                c.capacite(), c.fileActive(), c.tauxOccupation(), CapaciteTheorique.Niveau.valueOf(c.niveau().name()));
    }
}
