package com.hemodialyse.backend.contexts.comptabilite.domain;

import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;

import java.util.Set;

/**
 * Port sortant : ce que le reste du paramétrage et de la comptabilité fait déjà d'un compte ou d'un journal du centre.
 * Un compte ou un journal « utilisé » ne peut être ni désactivé ni supprimé ; un compte ou un journal qui porte des
 * écritures se désactive mais ne se supprime pas. Les lectures portent sur les tables des autres slices (mapping, modèles
 * de pièces, écritures) et sur celle des articles, sans dépendre de leurs modules.
 */
public interface UsagesDuParametrage {

    /**
     * Comptes que le paramétrage comptable (mapping) du centre utilise ; le mapping par défaut tant qu'il n'a rien enregistré.
     */
    Set<NumeroDeCompte> comptesDuParametrage(CentreId centre);

    /**
     * Compte choisi par le mapping, par un payeur ou par un modèle de pièce.
     */
    boolean compteUtilise(CentreId centre, NumeroDeCompte compte);

    /**
     * Compte rattaché à un article du stock (KB-CPT-13a : désormais protégé comme les autres usages).
     */
    boolean compteRattacheAUnArticle(CentreId centre, NumeroDeCompte compte);

    boolean compteAvecEcritures(CentreId centre, NumeroDeCompte compte);

    /**
     * Journal choisi pour une opération du mapping ou pour un modèle de pièce.
     */
    boolean journalUtilise(CentreId centre, CodeJournal journal);

    boolean journalAvecEcritures(CentreId centre, CodeJournal journal);
}
