package com.hemodialyse.backend.contexts.facturation.domain.parametres;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.util.Optional;

/**
 * Port sortant : les paramètres de facturation enregistrés par centre (table {@code facturation_settings}).
 */
public interface ParametresDeFacturationRepository {

    /**
     * Les paramètres enregistrés du centre ; vide s'il n'a rien enregistré (la lecture n'écrit rien, KB-FAC-08 corrigé).
     */
    Optional<ParametresDeFacturation> duCentre(CentreId centre);

    /**
     * Enregistre (crée ou remplace) les paramètres du centre au nom de leur auteur.
     */
    void enregistrer(CentreId centre, ParametresDeFacturation parametres, String auteur);
}
