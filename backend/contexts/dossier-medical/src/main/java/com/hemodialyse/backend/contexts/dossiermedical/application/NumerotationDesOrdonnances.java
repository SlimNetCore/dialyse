package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** Port sortant : la séquence légale des numéros d'ordonnance du centre ({@code ORD-nnnnn}), sans réutilisation. */
public interface NumerotationDesOrdonnances {

    /** Attribue le numéro suivant du centre ; à appeler dans la transaction de la signature (le verrou tient jusqu'à sa fin). */
    String suivant(CentreId centre);
}
