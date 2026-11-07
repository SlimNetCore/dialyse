package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.application.PatientsDeLaSeance;
import com.hemodialyse.backend.contexts.seance.application.PatientsScannables;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** ACL des patients : traduit le langage publié {@link FichesFournies} en fiches et références de la Séance. */
public class PatientsParLeLegacy implements PatientsDeLaSeance, PatientsScannables {

    private final FichesFournies fiches;

    public PatientsParLeLegacy(FichesFournies fiches) {
        this.fiches = fiches;
    }

    @Override
    public Optional<FicheDuPatient> fiche(CentreId centre, PatientId patient) {
        return fiches.fiche(centre.valeur(), patient.valeur()).map(PatientsParLeLegacy::traduire);
    }

    @Override
    public Optional<PatientId> parIdentifiant(CentreId centre, UUID identifiant) {
        return fiches.parIdentifiant(centre.valeur(), identifiant).map(PatientId::new);
    }

    @Override
    public Optional<PatientId> parCode(CentreId centre, String code) {
        return fiches.parCode(centre.valeur(), code).map(PatientId::new);
    }

    @Override
    public Optional<PatientId> parNumeroDAssurance(CentreId centre, String numero) {
        return fiches.parNumeroDAssurance(centre.valeur(), numero).map(PatientId::new);
    }

    @Override
    public List<References> tous(CentreId centre) {
        return fiches.tous(centre.valeur()).stream()
                .map(r -> new References(new PatientId(r.patient()), r.code(), r.numeroDAssurance(), r.numeroDAssuranceDeLAssure()))
                .toList();
    }

    static FicheDuPatient traduire(FichesFournies.Fiche f) {
        return new FicheDuPatient(new PatientId(f.id()), f.code(), f.nom(), f.prenom(), f.sexe(), f.dateDeNaissance(),
                f.numeroDAssurance(), f.groupeSanguin(), f.telephoneMobile(), f.medecinTraitant(), f.salle(), f.position(),
                f.generateur(), f.generateurNom(), f.generateurMarque(), f.generateurEtat());
    }
}
