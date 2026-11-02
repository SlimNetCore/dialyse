package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Machine;
import com.hemodialyse.backend.contexts.organisation.domain.MachineId;
import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.Protocole;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.UUID;

public interface ReferentielsOrganisationUseCases {
    MedecinId referencerMedecin(CentreId centreId, String nom, String prenom, String specialite);

    void modifierMedecin(CentreId centreId, MedecinId id, String nom, String prenom, String specialite);

    void supprimerMedecin(CentreId centreId, MedecinId id);

    PagedResult<Medecin> listerMedecins(CentreId centreId, int page, int size);

    MachineId referencerMachine(CentreId centreId, String code, String libelle);

    void modifierMachine(CentreId centreId, MachineId id, String code, String libelle, Boolean actif);

    void supprimerMachine(CentreId centreId, MachineId id);

    PagedResult<Machine> listerMachines(CentreId centreId, int page, int size);

    ProtocoleId referencerProtocole(CentreId centreId, String code, String libelle);

    void modifierProtocole(CentreId centreId, ProtocoleId id, String code, String libelle);

    void supprimerProtocole(CentreId centreId, ProtocoleId id);

    PagedResult<Protocole> listerProtocoles(CentreId centreId, int page, int size);
}
