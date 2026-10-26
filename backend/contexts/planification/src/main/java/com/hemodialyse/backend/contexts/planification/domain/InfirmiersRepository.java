package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

import java.util.List;
import java.util.Optional;

/**
 * Port sortant : le référentiel des infirmiers (un dépôt par racine d'agrégat). Les lectures qui précèdent une écriture
 * ({@code pourEcriture}, {@code fichesDuCompte…}) tiennent les fiches verrouillées jusqu'à la fin de la transaction.
 */
public interface InfirmiersRepository {

    /** La fiche du centre, verrouillée pour l'écriture (ADR 0049 : verrou de la ligne {@code infirmier}). */
    Optional<Infirmier> pourEcriture(CentreId centre, InfirmierId id);

    /**
     * La fiche du centre, lue sans verrou (lecture seule).
     */
    Optional<Infirmier> parId(CentreId centre, InfirmierId id);

    /** Une page des fiches du centre, actives et inactives, par nom, prénom puis matricule. */
    PageDeResultats<Infirmier> page(CentreId centre, Pagination pagination);

    /** La fiche du centre qui porte ce matricule, à la casse près. */
    Optional<Infirmier> parMatricule(CentreId centre, Matricule matricule);

    /** La fiche du centre liée à ce compte. */
    Optional<Infirmier> parCompte(CentreId centre, CompteId compte);

    /** Les fiches liées à ce compte dans un centre, verrouillées pour l'écriture. */
    List<Infirmier> fichesDuCompteDuCentre(CentreId centre, CompteId compte);

    /** Les fiches liées à ce compte, tous centres confondus, verrouillées pour l'écriture (suppression du compte). */
    List<Infirmier> fichesDuCompte(CompteId compte);

    /** Enregistre la fiche (création ou remplacement). */
    void enregistrer(Infirmier infirmier);
}
