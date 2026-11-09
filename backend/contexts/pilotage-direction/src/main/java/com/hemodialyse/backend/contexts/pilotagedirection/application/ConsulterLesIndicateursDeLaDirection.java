package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.CliniquesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.IndicateursDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.StockDuCentre;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consulter les indicateurs de la direction : cliniques (Dossier médical) et stock (Stock) par centre et au total, avec les alertes levées
 * (PD6, ADR 0106). La période est validée AVANT toute lecture (défaut : du 1er janvier à aujourd'hui, 5 ans au plus) ; le stock est évalué à
 * la FIN de la période (KB-DIR-07). Le périmètre est la société fournie par l'appelant (le jeton, revérifié). Une société sans centre n'est
 * pas lue dans le Stock mais le consolidé clinique est tout de même demandé, comme avant.
 */
public final class ConsulterLesIndicateursDeLaDirection {

    private final SocietesSuivies societes;
    private final IndicateursCliniquesDesCentres cliniques;
    private final StockDesCentres stocks;
    private final HorlogeDuPilotage horloge;

    public ConsulterLesIndicateursDeLaDirection(SocietesSuivies societes, IndicateursCliniquesDesCentres cliniques, StockDesCentres stocks,
                                                HorlogeDuPilotage horloge) {
        this.societes = societes;
        this.cliniques = cliniques;
        this.stocks = stocks;
        this.horloge = horloge;
    }

    public IndicateursDeLaSociete execute(UUID societeId, LocalDate from, LocalDate to) {
        Periode periode = Periode.demandee(from, to, horloge.aujourdhui());
        SocieteSuivie societe = societes.societe(societeId);
        List<UUID> centres = societe.identifiantsDesCentres();
        var genereLe = horloge.maintenant();
        CliniquesDesCentres lues = cliniques.desCentres(centres, periode);
        Map<UUID, StockDuCentre> stock = societe.estSansCentre() ? Map.of() : stocks.aLaDate(centres, periode.fin());
        return IndicateursDeLaSociete.composer(societe, periode, genereLe, lues, stock);
    }
}
