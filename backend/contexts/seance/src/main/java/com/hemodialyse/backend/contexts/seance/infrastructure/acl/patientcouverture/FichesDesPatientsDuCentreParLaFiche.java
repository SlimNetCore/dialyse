package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import com.hemodialyse.backend.contexts.seance.domain.FichesDesPatientsDuCentre;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers Patient et couverture (LEC-PAT sous-slice ii, ADR 0177) : la fiche de facturation, le centre payeur et l'identité des
 * patients du centre, par le langage publié {@link LotsDePatients}. Traduction pure, sans règle : remplace la jointure
 * {@code seances x patients} de {@code FacturationDesSeancesJdbc}, {@code SeancesRealiseesDesCentresJdbc} et
 * {@code LecturesDesSeancesRealiseesJdbc} (résidus C2-R6, D-R3, E-R2).
 */
public class FichesDesPatientsDuCentreParLaFiche implements FichesDesPatientsDuCentre {

    private final LotsDePatients lots;

    public FichesDesPatientsDuCentreParLaFiche(LotsDePatients lots) {
        this.lots = lots;
    }

    @Override
    public List<PatientAFacturer> aFacturer(UUID centre, Collection<UUID> patients) {
        return lots.aFacturerDuCentre(centre, patients).stream()
                .map(p -> new PatientAFacturer(p.patient(), p.code(), p.nom(), p.prenom(), p.etat(), p.numeroAssurance(),
                        p.centrePayeur(), p.rang()))
                .toList();
    }

    @Override
    public Map<UUID, UUID> centresPayeurs(UUID centre, Collection<UUID> patients) {
        return lots.centresPayeursDuCentre(centre, patients);
    }

    @Override
    public Map<UUID, NomDuPatient> noms(UUID centre) {
        Map<UUID, NomDuPatient> noms = new LinkedHashMap<>();
        lots.identitesDuCentre(centre).forEach((patient, nom) -> noms.put(patient, new NomDuPatient(nom.prenom(), nom.nom())));
        return noms;
    }
}
