package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.ReferentielsCommunsUseCases;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsLectureUseCases;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsOrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerMachineRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerMedecinRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerProtocoleRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ModifierMachineRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ModifierMedecinRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ModifierProtocoleRequete;
import com.hemodialyse.backend.contexts.organisation.domain.DetailPayeur;
import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;
import com.hemodialyse.backend.contexts.organisation.domain.Machine;
import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.Protocole;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisation/referentiels")
public class ReferentielsControleur {
    private final ReferentielsOrganisationUseCases service;
    private final ReferentielsLectureUseCases lectures;
    private final ReferentielsCommunsUseCases communs;

    public ReferentielsControleur(ReferentielsOrganisationUseCases service, ReferentielsLectureUseCases lectures,
                                  ReferentielsCommunsUseCases communs) {
        this.service = service;
        this.lectures = lectures;
        this.communs = communs;
    }

    @PostMapping("/medecins")
    public UUID creerMedecin(@RequestBody CreerMedecinRequete req) {
        return service.referencerMedecin(new CentreId(req.centreId()), req.nom(), req.prenom(), req.specialite()).valeur();
    }

    @PutMapping("/medecins/{id}")
    public void modifierMedecin(@PathVariable UUID id, @RequestBody ModifierMedecinRequete req) {
        service.modifierMedecin(new com.hemodialyse.backend.contexts.organisation.domain.MedecinId(id), req.nom(), req.prenom(), req.specialite());
    }

    @DeleteMapping("/medecins/{id}")
    public void supprimerMedecin(@PathVariable UUID id) {
        service.supprimerMedecin(new com.hemodialyse.backend.contexts.organisation.domain.MedecinId(id));
    }

    @GetMapping("/medecins")
    public PagedResult<Medecin> listerMedecins(@RequestParam UUID centreId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listerMedecins(new CentreId(centreId), page, size);
    }

    @PostMapping("/machines")
    public UUID creerMachine(@RequestBody CreerMachineRequete req) {
        return service.referencerMachine(new CentreId(req.centreId()), req.code(), req.libelle()).valeur();
    }

    @PutMapping("/machines/{id}")
    public void modifierMachine(@PathVariable UUID id, @RequestBody ModifierMachineRequete req) {
        service.modifierMachine(new com.hemodialyse.backend.contexts.organisation.domain.MachineId(id), req.code(), req.libelle(), req.actif());
    }

    @DeleteMapping("/machines/{id}")
    public void supprimerMachine(@PathVariable UUID id) {
        service.supprimerMachine(new com.hemodialyse.backend.contexts.organisation.domain.MachineId(id));
    }

    @GetMapping("/machines")
    public PagedResult<Machine> listerMachines(@RequestParam UUID centreId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listerMachines(new CentreId(centreId), page, size);
    }

    @PostMapping("/protocoles")
    public UUID creerProtocole(@RequestBody CreerProtocoleRequete req) {
        return service.referencerProtocole(new CentreId(req.centreId()), req.code(), req.libelle()).valeur();
    }

    @PutMapping("/protocoles/{id}")
    public void modifierProtocole(@PathVariable UUID id, @RequestBody ModifierProtocoleRequete req) {
        service.modifierProtocole(new com.hemodialyse.backend.contexts.organisation.domain.ProtocoleId(id), req.code(), req.libelle());
    }

    @DeleteMapping("/protocoles/{id}")
    public void supprimerProtocole(@PathVariable UUID id) {
        service.supprimerProtocole(new com.hemodialyse.backend.contexts.organisation.domain.ProtocoleId(id));
    }

    @GetMapping("/protocoles")
    public PagedResult<Protocole> listerProtocoles(@RequestParam UUID centreId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listerProtocoles(new CentreId(centreId), page, size);
    }

    @GetMapping("/articles")
    public PagedResult<ReferentielsLectureUseCases.ArticleLu> listerArticles(
            @RequestParam UUID centreId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lectures.listerArticles(new CentreId(centreId), page, size);
    }

    @GetMapping("/generateurs")
    public List<ReferentielsLectureUseCases.GenerateurLu> listerGenerateurs(@RequestParam UUID centreId) {
        return lectures.listerGenerateurs(new CentreId(centreId));
    }

    @GetMapping("/centres-payeurs")
    public List<EntreeReferentiel> listerCentresPayeurs(@RequestParam UUID centreId) {
        return communs.centresPayeurs(new CentreId(centreId));
    }

    @GetMapping("/centres-payeurs-details")
    public List<DetailPayeur> listerCentresPayeursDetails(@RequestParam UUID centreId) {
        return communs.centresPayeursDetails(new CentreId(centreId));
    }

    @GetMapping("/agences")
    public List<EntreeReferentiel> listerAgences(@RequestParam UUID centreId) {
        return communs.agences(new CentreId(centreId));
    }

    @GetMapping("/caisses")
    public List<EntreeReferentiel> listerCaisses(@RequestParam UUID centreId) {
        return communs.caisses(new CentreId(centreId));
    }

    @GetMapping("/transporteurs")
    public List<EntreeReferentiel> listerTransporteurs(@RequestParam UUID centreId) {
        return communs.transporteurs(new CentreId(centreId));
    }

    @GetMapping("/categories-transport")
    public List<EntreeReferentiel> listerCategoriesTransport(@RequestParam UUID centreId) {
        return communs.categoriesTransport(new CentreId(centreId));
    }

    @GetMapping("/forfaits")
    public List<EntreeReferentiel> listerForfaits(@RequestParam UUID centreId) {
        return communs.forfaits(new CentreId(centreId));
    }

    @GetMapping("/etats-patients")
    public List<EntreeReferentiel> listerEtatsPatients() {
        return communs.etatsPatients();
    }
}
