package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CodeScanne;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/**
 * Retrouve le patient d'un QR dans le centre, dans l'ordre du legacy : identifiant (UUID), puis code patient, puis numéro d'assurance,
 * puis comparaison tolérante sur les trois références. Un identifiant lu mais inconnu du centre ne retombe pas sur les autres formes.
 * Rien d'autre qu'un patient DU CENTRE n'est jamais rendu : inconnu ou d'un autre centre, {@link PatientIntrouvable} (404).
 */
public class ResoudreLePatientScanne {

    private final PatientsScannables patients;

    public ResoudreLePatientScanne(PatientsScannables patients) {
        this.patients = patients;
    }

    public PatientId executer(CentreId centre, CodeScanne qr) {
        Optional<java.util.UUID> identifiant = qr.identifiant();
        if (identifiant.isPresent()) {
            return patients.parIdentifiant(centre, identifiant.get()).orElseThrow(PatientIntrouvable::new);
        }
        Optional<PatientId> parCode = patients.parCode(centre, qr.codePatient());
        if (parCode.isPresent()) return parCode.get();
        Optional<PatientId> parNumero = patients.parNumeroDAssurance(centre, qr.numeroDAssurance());
        if (parNumero.isPresent()) return parNumero.get();
        return patients.tous(centre).stream()
                .filter(p -> p != null && qr.correspond(p.code(), p.numeroDAssurance(), p.numeroDAssuranceDeLAssure()))
                .map(PatientsScannables.References::patient)
                .findFirst()
                .orElseThrow(PatientIntrouvable::new);
    }
}
