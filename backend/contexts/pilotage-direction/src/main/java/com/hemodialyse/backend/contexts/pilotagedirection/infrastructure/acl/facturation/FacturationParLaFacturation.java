package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.FacturationDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ChiffresDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ChiffresFinanciers;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDeChiffres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ChiffreDeLaCaisse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ACL : la part financière des centres lue dans le langage publié de la Facturation ({@link SyntheseFinanciereDesCentres}, F5). */
public final class FacturationParLaFacturation implements FacturationDesCentres {

    private final SyntheseFinanciereDesCentres facturation;

    public FacturationParLaFacturation(SyntheseFinanciereDesCentres facturation) {
        this.facturation = facturation;
    }

    @Override
    public Map<UUID, ChiffresDuCentre> parCentre(List<UUID> centres, Periode periode) {
        Map<UUID, ChiffresDuCentre> chiffres = new HashMap<>();
        facturation.chiffresParCentre(centres, periode.debut(), periode.fin()).forEach((centre, c) -> chiffres.put(centre,
                new ChiffresDuCentre(c.factures(), new ChiffresFinanciers(c.chiffreDAffairesHt(), c.chiffreDAffairesTtc(), c.encaisse()))));
        return chiffres;
    }

    @Override
    public ChiffresFinanciers cumules(List<UUID> centres, Periode periode) {
        var cumul = facturation.chiffresCumules(centres, periode.debut(), periode.fin());
        return new ChiffresFinanciers(cumul.chiffreDAffairesHt(), cumul.chiffreDAffairesTtc(), cumul.encaisse());
    }

    @Override
    public List<MoisDeChiffres> parMois(List<UUID> centres, Periode periode) {
        return facturation.chiffresParMois(centres, periode.debut(), periode.fin()).stream()
                .map(m -> new MoisDeChiffres(m.centre(), m.annee(), m.mois(), m.chiffreDAffairesHt(), m.chiffreDAffairesTtc())).toList();
    }

    @Override
    public List<ChiffreDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
        return facturation.chiffreDAffairesParCaisse(centres, periode.debut(), periode.fin()).stream()
                .map(c -> new ChiffreDeLaCaisse(c.centre(), c.codeCaisse(), c.nomCaisse(), c.chiffreDAffairesHt())).toList();
    }
}
