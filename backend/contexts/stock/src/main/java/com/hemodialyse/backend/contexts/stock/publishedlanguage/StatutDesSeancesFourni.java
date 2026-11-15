package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.UUID;

/**
 * Port FOURNI AU Stock (ADR 0191, sur le modèle de {@link CentresFournis}) : ce que le Stock a besoin de savoir d'une séance, dans ses termes
 * (RG-STK-032) : est-elle facturée. La Séance dépend du Stock ; elle implémente ce port à partir de son langage publié. Lecture seule.
 */
public interface StatutDesSeancesFourni {

    boolean estFacturee(UUID centre, UUID seance);
}
