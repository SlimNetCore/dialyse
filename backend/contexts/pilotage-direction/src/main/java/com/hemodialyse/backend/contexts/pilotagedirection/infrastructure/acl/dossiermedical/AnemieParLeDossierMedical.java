package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.dossiermedical;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.TraitementDeLAnemiePublie;
import com.hemodialyse.backend.contexts.pilotagedirection.application.TraitementsDeLAnemieDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.AdministrationsDeLAnemie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ACL : les administrations de l'anémie lues dans {@code dossier-medical.v1} ({@link TraitementDeLAnemiePublie}, DM9, ADR 0101). */
public final class AnemieParLeDossierMedical implements TraitementsDeLAnemieDesCentres {

    private final TraitementDeLAnemiePublie dossierMedical;

    public AnemieParLeDossierMedical(TraitementDeLAnemiePublie dossierMedical) {
        this.dossierMedical = dossierMedical;
    }

    @Override
    public Map<UUID, AdministrationsDeLAnemie> parCentre(List<UUID> centres, Periode periode) {
        Map<UUID, AdministrationsDeLAnemie> administrations = new HashMap<>();
        dossierMedical.parCentre(centres, periode.debut(), periode.fin()).forEach((centre, c) -> administrations.put(centre,
                new AdministrationsDeLAnemie(c.patientsEpo(), c.patientsFer(), c.administreesEpo(), c.administreesFer(),
                        c.nonAdministrees())));
        return administrations;
    }
}
