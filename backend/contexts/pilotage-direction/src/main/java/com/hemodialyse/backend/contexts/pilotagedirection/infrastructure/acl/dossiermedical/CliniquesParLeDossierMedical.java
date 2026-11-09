package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.dossiermedical;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies.IndicateursCliniques;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies.RepartitionFaceALaCible;
import com.hemodialyse.backend.contexts.pilotagedirection.application.IndicateursCliniquesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CliniquesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.IndicateursCliniquesDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MarqueurClinique;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL : les indicateurs cliniques des centres lus dans {@code dossier-medical.v1} ({@link IndicateursCliniquesPublies}, DM9, ADR 0101),
 * déjà masqués (seuil 5) ; le consolidé est celui du Dossier médical (calculé sur les bruts : KB-MED-104, reproduit). Traduction pure.
 */
public final class CliniquesParLeDossierMedical implements IndicateursCliniquesDesCentres {

    private final IndicateursCliniquesPublies dossierMedical;

    public CliniquesParLeDossierMedical(IndicateursCliniquesPublies dossierMedical) {
        this.dossierMedical = dossierMedical;
    }

    @Override
    public CliniquesDesCentres desCentres(List<UUID> centres, Periode periode) {
        var lus = dossierMedical.desCentres(centres, periode.debut(), periode.fin());
        Map<UUID, IndicateursCliniquesDuCentre> parCentre = new HashMap<>();
        lus.parCentre().forEach((centre, i) -> parCentre.put(centre, traduire(i)));
        return new CliniquesDesCentres(parCentre, traduire(lus.consolides()));
    }

    static IndicateursCliniquesDuCentre traduire(IndicateursCliniques i) {
        return new IndicateursCliniquesDuCentre(marqueur(i.ktV()), marqueur(i.hemoglobine()), marqueur(i.phosphore()), marqueur(i.pth()),
                marqueur(i.albumine()), i.vhbPositifs(), i.vhcPositifs(), i.vihPositifs(), i.patientsObservanceEnRetard(),
                i.observanceEnRetard(), i.greffeListeAttente(), i.greffeBilanEnCours(), i.greffesPeriode());
    }

    private static MarqueurClinique marqueur(RepartitionFaceALaCible r) {
        return new MarqueurClinique(r.evalues(), r.pctDansCible(), r.pctSousCible(), r.pctAuDessus());
    }
}
