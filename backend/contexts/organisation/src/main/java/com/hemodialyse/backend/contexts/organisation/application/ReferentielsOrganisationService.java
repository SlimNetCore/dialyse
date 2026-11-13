package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Machine;
import com.hemodialyse.backend.contexts.organisation.domain.MachineId;
import com.hemodialyse.backend.contexts.organisation.domain.MachineRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinRepository;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeMedecin;
import com.hemodialyse.backend.contexts.organisation.domain.Protocole;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleId;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.UUID;

public class ReferentielsOrganisationService implements ReferentielsOrganisationUseCases {

    private final MedecinRepository medecins;
    private final MachineRepository machines;
    private final ProtocoleRepository protocoles;

    private final GenerateurDIdentifiants identifiants;
    private final GererLesMedecins gestionDesMedecins;

    public ReferentielsOrganisationService(MedecinRepository medecins, MachineRepository machines, ProtocoleRepository protocoles,
                                           GenerateurDIdentifiants identifiants, GererLesMedecins gestionDesMedecins) {
        this.medecins = medecins;
        this.machines = machines;
        this.protocoles = protocoles;
        this.identifiants = identifiants;
        this.gestionDesMedecins = gestionDesMedecins;
    }

    /** PL11-3, D7 : la route d'Organisation applique la règle d'unicité et de saisie de l'administration (D6 : réactive). */
    @Override
    public MedecinId referencerMedecin(CentreId centreId, String nom, String prenom, String specialite) {
        try {
            return new MedecinId(gestionDesMedecins.creer(centreId.valeur(), nom, prenom, specialite).id());
        } catch (RefusDeMedecin refus) {
            throw switch (refus.motif()) {
                case DEJA_EXISTANT -> new IllegalArgumentException("MEDECIN_DEJA_EXISTANT");
                case SAISIE_INVALIDE -> new IllegalArgumentException(refus.anomalies().stream()
                        .anyMatch(a -> "nom".equals(a.champ()) && "REQUIRED".equals(a.code())) ? "Nom requis"
                        : refus.anomalies().getFirst().message());
                case INTROUVABLE -> new com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable("Medecin inconnu");
            };
        }
    }

    @Override
    public void modifierMedecin(CentreId centreId, MedecinId id, String nom, String prenom, String specialite) {
        try {
            gestionDesMedecins.ajuster(centreId, id, nom, prenom, specialite);
        } catch (RefusDeMedecin refus) {
            throw new IllegalArgumentException("MEDECIN_DEJA_EXISTANT");
        }
    }

    @Override
    public void desactiverMedecin(CentreId centreId, MedecinId id) {
        gestionDesMedecins.desactiverSansEchec(centreId, id);
    }

    @Override
    public PagedResult<Medecin> listerMedecins(CentreId centreId, int page, int size) {
        return medecins.listerParCentre(centreId, page, size);
    }

    @Override
    public MachineId referencerMachine(CentreId centreId, String code, String libelle) {
        MachineId id = new MachineId(identifiants.suivant());
        Machine m = new Machine(id, centreId, code, libelle, true);
        machines.sauvegarder(m);
        return id;
    }

    @Override
    public void modifierMachine(CentreId centreId, MachineId id, String code, String libelle, Boolean actif) {
        Machine m = machines.trouver(id).filter(x -> x.centreId().equals(centreId))
                .orElseThrow(() -> new com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable("Machine inconnue"));
        m.modifier(code, libelle, actif);
        machines.sauvegarder(m);
    }

    @Override
    public void supprimerMachine(CentreId centreId, MachineId id) {
        machines.supprimer(centreId, id);
    }

    @Override
    public PagedResult<Machine> listerMachines(CentreId centreId, int page, int size) {
        return machines.listerParCentre(centreId, page, size);
    }

    @Override
    public ProtocoleId referencerProtocole(CentreId centreId, String code, String libelle) {
        ProtocoleId id = new ProtocoleId(identifiants.suivant());
        Protocole p = new Protocole(id, centreId, code, libelle);
        protocoles.sauvegarder(p);
        return id;
    }

    @Override
    public void modifierProtocole(CentreId centreId, ProtocoleId id, String code, String libelle) {
        Protocole p = protocoles.trouver(id).filter(x -> x.centreId().equals(centreId))
                .orElseThrow(() -> new com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable("Protocole inconnu"));
        p.modifier(code, libelle);
        protocoles.sauvegarder(p);
    }

    @Override
    public void supprimerProtocole(CentreId centreId, ProtocoleId id) {
        protocoles.supprimer(centreId, id);
    }

    @Override
    public PagedResult<Protocole> listerProtocoles(CentreId centreId, int page, int size) {
        return protocoles.listerParCentre(centreId, page, size);
    }
}
