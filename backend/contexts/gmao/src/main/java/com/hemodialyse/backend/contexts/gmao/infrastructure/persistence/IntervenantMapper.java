package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.TarifIntervenant;

/** Mapping explicite agrégat / vue <-> modèle de persistance. */
final class IntervenantMapper {

    private IntervenantMapper() {
    }

    static Intervenant versDomaine(IntervenantJpa e) {
        return Intervenant.reconstituer(new IntervenantId(e.getId()), new CentreId(e.getCentreId()), e.getNom(),
                NatureIntervenant.valueOf(e.getNature()), e.getTelephone(), e.getEmail(),
                TarifIntervenant.ouAucun(e.getTarifHoraire()), e.isActif());
    }

    /** Recopie l'état de l'agrégat dans le modèle de persistance (neuf ou géré). */
    static void recopier(Intervenant intervenant, IntervenantJpa e) {
        e.setCentreId(intervenant.centreId().valeur());
        e.setNom(intervenant.nom());
        e.setNature(intervenant.nature().name());
        e.setTelephone(intervenant.telephone());
        e.setEmail(intervenant.email());
        e.setTarifHoraire(intervenant.tarif() == null ? null : intervenant.tarif().valeur());
        e.setActif(intervenant.estActif());
    }

    static IntervenantVue versVue(IntervenantJpa e) {
        return new IntervenantVue(e.getId(), e.getCentreId(), e.getNom(), NatureIntervenant.valueOf(e.getNature()),
                e.getTelephone(), e.getEmail(), e.getTarifHoraire(), e.isActif());
    }
}
