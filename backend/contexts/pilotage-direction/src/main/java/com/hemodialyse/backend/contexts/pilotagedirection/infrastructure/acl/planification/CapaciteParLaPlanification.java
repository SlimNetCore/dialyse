package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursInstalles;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CapaciteDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteTheoriqueDesCentres;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL : la capacité théorique lue dans le langage publié de la Planification ({@link CapaciteTheoriqueDesCentres}, SE7f, ADR 0100) et les
 * générateurs EN SERVICE de la GMAO ({@link GenerateursInstalles}, KB-PLN-76 : installés au plus tard à la fin de la période, comme le
 * planning de la semaine). Le fuseau de la fin de période est celui de la planification (une société consolidée n'a pas un seul centre).
 * Traduction pure, aucun SQL.
 */
public final class CapaciteParLaPlanification implements CapaciteDesCentres {

    private final GenerateursInstalles installes;
    private final CapaciteTheoriqueDesCentres capacites;
    private final ZoneId fuseau;

    public CapaciteParLaPlanification(GenerateursInstalles installes, CapaciteTheoriqueDesCentres capacites, ZoneId fuseau) {
        this.installes = installes;
        this.capacites = capacites;
        this.fuseau = fuseau;
    }

    @Override
    public ReglesDeCapacite regles() {
        return new ReglesDeCapacite(CapaciteTheoriqueDesCentres.GENERATEURS_PAR_SECOURS, CapaciteTheoriqueDesCentres.SEUIL_PROCHE_POURCENT);
    }

    @Override
    public Map<UUID, Capacite> parCentre(List<UUID> centres, Periode periode) {
        Map<UUID, Long> generateurs = installes.enServiceParCentre(centres, periode.fin().plusDays(1).atStartOfDay(fuseau).toInstant());
        Map<UUID, Capacite> traduites = new HashMap<>();
        for (UUID centre : centres) {
            traduites.put(centre, traduire(capacites.duCentre(centre, generateurs.getOrDefault(centre, 0L).intValue(),
                    periode.debut(), periode.fin())));
        }
        return traduites;
    }

    @Override
    public Capacite consolidee(List<Capacite> centres) {
        return traduire(capacites.consolidee(centres.stream().map(CapaciteParLaPlanification::vers).toList()));
    }

    static Capacite traduire(CapaciteTheoriqueDesCentres.Capacite c) {
        return new Capacite(c.generateurs(), c.generateursSecours(), c.postesActifs(), c.series(), c.patientsParPosteEtSerie(),
                c.capacite(), c.fileActive(), c.tauxOccupation(), Niveau.valueOf(c.niveau().name()));
    }

    static CapaciteTheoriqueDesCentres.Capacite vers(Capacite c) {
        return new CapaciteTheoriqueDesCentres.Capacite(c.generateurs(), c.generateursSecours(), c.postesActifs(), c.series(),
                c.patientsParPosteEtSerie(), c.capacite(), c.fileActive(), c.tauxOccupation(),
                CapaciteTheoriqueDesCentres.Niveau.valueOf(c.niveau().name()));
    }
}
