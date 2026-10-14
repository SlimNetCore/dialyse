package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.intervention.DocumentImprime;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DonneesDuBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ImpressionDuBon;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.time.ZoneId;

/** Double du port {@link ImpressionDuBon} : retient ce qu'on lui a demandé d'imprimer. */
public class ImpressionEnMemoire implements ImpressionDuBon {

    public CentreId centre;
    public DonneesDuBon donnees;
    public ZoneId zone;
    public String editePar;
    public int impressions;

    @Override
    public DocumentImprime imprimer(CentreId centre, DonneesDuBon donnees, ZoneId zone, String editePar) {
        this.centre = centre;
        this.donnees = donnees;
        this.zone = zone;
        this.editePar = editePar;
        impressions++;
        return new DocumentImprime("PDF", new byte[]{'%', 'P', 'D', 'F'});
    }
}
