package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;

import java.util.UUID;

/**
 * Corrige le résultat et la conduite à tenir d'une sérologie, puis fait réévaluer la place du patient, dans UNE transaction. La
 * conduite du corps REMPLACE l'ancienne, même absente (KB-MED-51, reproduit, à confirmer avec les métiers). Sérologie inconnue, d'un
 * autre patient ou d'un autre centre : refus 400 (reproduit, KB-MED-49). Droit : MEDECIN.
 */
public class CorrigerUneSerologie {

    private final SessionCourante session;
    private final Serologies serologies;
    private final IsolementDesPatients isolement;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public CorrigerUneSerologie(SessionCourante session, Serologies serologies, IsolementDesPatients isolement,
                                UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.serologies = serologies;
        this.isolement = isolement;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Serologie executer(UUID centreDemande, UUID patientDemande, UUID id, String resultatSaisi, String conduiteATenir) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        var resultat = ResultatSerologique.lire(resultatSaisi);
        return uniteDeTravail.executer(() -> {
            isolement.verrouillerLesPlaces(centre, patient);
            Serologie serologie = serologies.trouverEnVerrouillant(centre, patient, id).orElseThrow(SerologieIntrouvable::new);
            serologie.corrigerLeResultat(resultat, conduiteATenir, horloge.maintenant());
            serologies.enregistrerLaCorrection(serologie);
            isolement.reevaluer(centre, patient);
            return serologie;
        });
    }
}
