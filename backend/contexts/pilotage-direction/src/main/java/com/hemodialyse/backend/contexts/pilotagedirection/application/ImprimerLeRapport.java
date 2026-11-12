package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ImpressionDuRapport.Impression;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SourceDUnInstantane.SourceDUnMois;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.Rapport;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Imprimer le rapport de synthèse de la direction (PL9, ADR 0148) : en direct sur une période, ou pour un mois figé. Constitue le rapport,
 * le met en page ({@link MiseEnPageDuRapport}) puis le confie à l'impression par un centre de la société (le premier par nom : le modèle
 * de document et le papier en-tête du rapport sont ceux de Documents). Une société sans centre n'a pas de modèle : refus 422.
 */
public final class ImprimerLeRapport {

    private final ConstituerLeRapportDeSynthese rapports;
    private final SourceDUnInstantane instantanes;
    private final SocietesSuivies societes;
    private final ImpressionDuRapport impression;
    private final ZoneId fuseau;

    public ImprimerLeRapport(ConstituerLeRapportDeSynthese rapports, SourceDUnInstantane instantanes, SocietesSuivies societes,
                             ImpressionDuRapport impression, ZoneId fuseau) {
        this.rapports = rapports;
        this.instantanes = instantanes;
        this.societes = societes;
        this.impression = impression;
        this.fuseau = fuseau;
    }

    public Impression enDirect(UUID societeId, LocalDate from, LocalDate to) {
        Rapport rapport = rapports.enDirect(societeId, from, to);
        Impression imprime = imprimer(societeId, rapport);
        return new Impression(imprime.octets(), "rapport-direction.pdf");
    }

    public Impression mensuel(UUID societeId, String mois) {
        SourceDUnMois source = instantanes.source(societeId, mois);
        Impression imprime = imprimer(societeId, rapports.constituer(source.source()));
        return new Impression(imprime.octets(), "rapport-direction-" + source.mois() + ".pdf");
    }

    private Impression imprimer(UUID societeId, Rapport rapport) {
        SocieteSuivie societe = societes.societe(societeId);
        // société sans centre : 200 comme avant (gabarit standard, papier en-tête de la société) ; sinon le modèle du premier centre par nom
        UUID centre = societe.estSansCentre() ? null : societe.identifiantsDesCentres().getFirst();
        return impression.imprimer(societeId, centre, MiseEnPageDuRapport.mettreEnPage(rapport, null, fuseau));
    }
}
