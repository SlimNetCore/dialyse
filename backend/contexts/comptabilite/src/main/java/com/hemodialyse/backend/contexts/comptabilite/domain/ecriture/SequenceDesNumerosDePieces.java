package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;

import java.util.List;

/**
 * Port sortant : la séquence des numéros de pièces d'un journal, pour une année et un centre. Elle se verrouille : tant que la
 * transaction de l'appelant n'est pas terminée, aucune autre transaction n'obtient de numéro dans la même séquence (RG-CPT-010).
 */
public interface SequenceDesNumerosDePieces {

    /**
     * Verrouille la séquence jusqu'à la fin de la transaction courante (qui doit exister) puis rend les numéros déjà attribués
     * dans le journal et l'année (ceux des écritures que cette transaction a écrites compris), tels qu'ils sont en base.
     */
    List<String> verrouillerEtLireLesNumeros(CentreId centre, CodeJournal journal, int annee);
}
