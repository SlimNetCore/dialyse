package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.CapaciteDesCentres.Capacite;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CapaciteDesCentres.Niveau;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CapaciteDesCentres.ReglesDeCapacite;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SeuilDAnonymat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consulter la capacité théorique des centres d'une société et le taux d'occupation de leur file active sur la période (PL10b2, ADR 0152) :
 * par centre et consolidés. Agrégats uniquement ; une file active de 1 à 4 patients est masquée par le seuil unique du Pilotage
 * ({@link SeuilDAnonymat}, KB-PL10b-5) avec son taux, sa capacité, son niveau et « atteinte » (ils la borneraient), et ce centre est exclu du
 * total consolidé (KB-PLN-75, champ {@code centresMasques}). Fin de période par défaut : le jour d'Alger.
 */
public final class ConsulterLaCapaciteDesCentres {

    private final SocietesSuivies societes;
    private final CapaciteDesCentres capacites;
    private final HorlogeDuPilotage horloge;

    public ConsulterLaCapaciteDesCentres(SocietesSuivies societes, CapaciteDesCentres capacites, HorlogeDuPilotage horloge) {
        this.societes = societes;
        this.capacites = capacites;
        this.horloge = horloge;
    }

    public CapaciteOverview execute(UUID societeId, LocalDate from, LocalDate to) {
        Periode periode = Periode.demandee(from, to, horloge.aujourdhui());
        ReglesDeCapacite regles = capacites.regles();
        OffsetDateTime maintenant = horloge.maintenant();
        List<CentreDeLaSociete> centres = societes.societe(societeId).centres();
        if (centres.isEmpty()) {
            return new CapaciteOverview(societeId, periode.debut(), periode.fin(), maintenant, regles,
                    ligne(capacites.consolidee(List.of())), List.of(), 0);
        }
        Map<UUID, Capacite> parCentre = capacites.parCentre(centres.stream().map(CentreDeLaSociete::id).toList(), periode);

        // KB-PLN-75 : un centre dont la file active est de 1 à 4 est masqué ET exclu du total consolidé ; sinon la différence entre le
        // total et les centres publiés le redonnerait.
        List<Capacite> publies = new ArrayList<>();
        List<CentreCapacite> lignes = new ArrayList<>();
        int centresMasques = 0;
        for (CentreDeLaSociete c : centres) {
            Capacite r = parCentre.get(c.id());
            lignes.add(new CentreCapacite(c.id(), c.nom(), c.actif(), ligne(r)));
            if (SeuilDAnonymat.estMasque(r.fileActive())) {
                centresMasques++;
            } else {
                publies.add(r);
            }
        }
        return new CapaciteOverview(societeId, periode.debut(), periode.fin(), maintenant, regles,
                ligne(capacites.consolidee(publies)), lignes, centresMasques);
    }

    private static Ligne ligne(Capacite r) {
        Long fileMasquee = SeuilDAnonymat.masquer(r.fileActive());
        boolean masquee = fileMasquee == null;
        // Pour une file masquée, la capacité, le niveau et « atteinte » la bornent (capacité 2 + ATTEINTE = file de 2 à 4) : masqués avec
        // elle. Les équipements (générateurs, postes, séries) ne sont pas des données patient.
        return new Ligne(r.generateurs(), r.generateursSecours(), r.postesActifs(), r.series(), r.patientsParPosteEtSerie(),
                masquee ? null : r.capacite(), fileMasquee, masquee ? null : r.tauxOccupation(), masquee ? null : r.niveau(),
                masquee ? null : r.atteinte());
    }

    /**
     * Capacité d'un centre (ou totaux) : {@code series} et {@code patientsParPosteEtSerie} valent 0 pour les totaux ; {@code fileActive},
     * {@code tauxOccupation}, {@code capacite}, {@code niveau} et {@code atteinte} sont {@code null} si la file active est trop faible.
     */
    public record Ligne(int generateurs, int generateursSecours, int postesActifs, int series, int patientsParPosteEtSerie,
                        Integer capacite, Long fileActive, BigDecimal tauxOccupation, Niveau niveau, Boolean atteinte) {
    }

    public record CentreCapacite(UUID centerId, String nom, boolean actif, Ligne capacite) {
    }

    public record CapaciteOverview(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt, ReglesDeCapacite regle,
                                   Ligne total, List<CentreCapacite> centres, int centresMasques) {
    }
}
