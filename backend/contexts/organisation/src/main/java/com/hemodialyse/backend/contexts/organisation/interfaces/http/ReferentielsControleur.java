package com.hemodialyse.backend.contexts.organisation.interfaces.http;

import com.hemodialyse.backend.contexts.organisation.application.ReferentielsCommunsUseCases;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsLectureUseCases;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsOrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.application.SessionCourante;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerMachineRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerMedecinRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.CreerProtocoleRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ModifierMachineRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ModifierMedecinRequete;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ModifierProtocoleRequete;
import com.hemodialyse.backend.contexts.organisation.domain.DetailPayeur;
import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;
import com.hemodialyse.backend.contexts.organisation.domain.MachineId;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleId;
import com.hemodialyse.backend.contexts.organisation.interfaces.http.dto.ReponsesDuReferentiel;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Référentiels du centre (O3). Écritures (médecins, machines, protocoles) : ADMIN (son centre) ou SUPERADMIN, comme
 * l'administration des référentiels du legacy ({@code /api/v1/admin/referentials}, routes nouvelles sans équivalent direct).
 * Lectures : tout utilisateur authentifié, bornées au centre de la session. Le centre retenu vient de la session (le SUPERADMIN
 * peut en désigner un autre) ; modifier et supprimer sont filtrés sur ce centre. Le corps n'est lu qu'après les droits.
 */
@RestController
@RequestMapping("/api/v1/organisation/referentiels")
public class ReferentielsControleur {
    private static final String ECRITURE = "hasAnyRole('ADMIN','SUPERADMIN')";

    private final ReferentielsOrganisationUseCases service;
    private final ReferentielsLectureUseCases lectures;
    private final ReferentielsCommunsUseCases communs;
    private final SessionCourante session;
    private final ObjectMapper lecteur;

    public ReferentielsControleur(ReferentielsOrganisationUseCases service, ReferentielsLectureUseCases lectures,
                                  ReferentielsCommunsUseCases communs, SessionCourante session, ObjectMapper lecteur) {
        this.service = service;
        this.lectures = lectures;
        this.communs = communs;
        this.session = session;
        this.lecteur = lecteur;
    }

    @PostMapping("/medecins")
    @PreAuthorize(ECRITURE)
    public UUID creerMedecin(HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        CreerMedecinRequete req = CorpsJson.lire(lecteur, requete, corps, CreerMedecinRequete.class);
        CentreId centre = session.centre(req.centreId());
        return service.referencerMedecin(centre, req.nom(), req.prenom(), req.specialite()).valeur();
    }

    @PutMapping("/medecins/{id}")
    @PreAuthorize(ECRITURE)
    public void modifierMedecin(@PathVariable UUID id, @RequestParam(required = false) UUID centreId,
                                HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centreId);
        ModifierMedecinRequete req = CorpsJson.lire(lecteur, requete, corps, ModifierMedecinRequete.class);
        service.modifierMedecin(centre, new MedecinId(id), req.nom(), req.prenom(), req.specialite());
    }

    @DeleteMapping("/medecins/{id}")
    @PreAuthorize(ECRITURE)
    public void supprimerMedecin(@PathVariable UUID id, @RequestParam(required = false) UUID centreId) {
        service.desactiverMedecin(session.centre(centreId), new MedecinId(id));
    }

    @GetMapping("/medecins")
    public PagedResult<ReponsesDuReferentiel.MedecinReponse> listerMedecins(@RequestParam(required = false) UUID centreId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ReponsesDuReferentiel.page(service.listerMedecins(session.centre(centreId), page, size), ReponsesDuReferentiel.MedecinReponse::de);
    }

    @PostMapping("/machines")
    @PreAuthorize(ECRITURE)
    public UUID creerMachine(HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        CreerMachineRequete req = CorpsJson.lire(lecteur, requete, corps, CreerMachineRequete.class);
        CentreId centre = session.centre(req.centreId());
        return service.referencerMachine(centre, req.code(), req.libelle()).valeur();
    }

    @PutMapping("/machines/{id}")
    @PreAuthorize(ECRITURE)
    public void modifierMachine(@PathVariable UUID id, @RequestParam(required = false) UUID centreId,
                                HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centreId);
        ModifierMachineRequete req = CorpsJson.lire(lecteur, requete, corps, ModifierMachineRequete.class);
        service.modifierMachine(centre, new MachineId(id), req.code(), req.libelle(), req.actif());
    }

    @DeleteMapping("/machines/{id}")
    @PreAuthorize(ECRITURE)
    public void supprimerMachine(@PathVariable UUID id, @RequestParam(required = false) UUID centreId) {
        service.supprimerMachine(session.centre(centreId), new MachineId(id));
    }

    @GetMapping("/machines")
    public PagedResult<ReponsesDuReferentiel.MachineReponse> listerMachines(@RequestParam(required = false) UUID centreId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ReponsesDuReferentiel.page(service.listerMachines(session.centre(centreId), page, size), ReponsesDuReferentiel.MachineReponse::de);
    }

    @PostMapping("/protocoles")
    @PreAuthorize(ECRITURE)
    public UUID creerProtocole(HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        CreerProtocoleRequete req = CorpsJson.lire(lecteur, requete, corps, CreerProtocoleRequete.class);
        CentreId centre = session.centre(req.centreId());
        return service.referencerProtocole(centre, req.code(), req.libelle()).valeur();
    }

    @PutMapping("/protocoles/{id}")
    @PreAuthorize(ECRITURE)
    public void modifierProtocole(@PathVariable UUID id, @RequestParam(required = false) UUID centreId,
                                  HttpServletRequest requete, @RequestBody(required = false) byte[] corps)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centreId);
        ModifierProtocoleRequete req = CorpsJson.lire(lecteur, requete, corps, ModifierProtocoleRequete.class);
        service.modifierProtocole(centre, new ProtocoleId(id), req.code(), req.libelle());
    }

    @DeleteMapping("/protocoles/{id}")
    @PreAuthorize(ECRITURE)
    public void supprimerProtocole(@PathVariable UUID id, @RequestParam(required = false) UUID centreId) {
        service.supprimerProtocole(session.centre(centreId), new ProtocoleId(id));
    }

    @GetMapping("/protocoles")
    public PagedResult<ReponsesDuReferentiel.ProtocoleReponse> listerProtocoles(@RequestParam(required = false) UUID centreId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ReponsesDuReferentiel.page(service.listerProtocoles(session.centre(centreId), page, size), ReponsesDuReferentiel.ProtocoleReponse::de);
    }

    @GetMapping("/articles")
    public PagedResult<ReferentielsLectureUseCases.ArticleLu> listerArticles(
            @RequestParam(required = false) UUID centreId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lectures.listerArticles(session.centre(centreId), page, size);
    }

    @GetMapping("/generateurs")
    public List<ReferentielsLectureUseCases.GenerateurLu> listerGenerateurs(@RequestParam(required = false) UUID centreId) {
        return lectures.listerGenerateurs(session.centre(centreId));
    }

    @GetMapping("/centres-payeurs")
    public List<EntreeReferentiel> listerCentresPayeurs(@RequestParam(required = false) UUID centreId) {
        return communs.centresPayeurs(session.centre(centreId));
    }

    @GetMapping("/centres-payeurs-details")
    public List<DetailPayeur> listerCentresPayeursDetails(@RequestParam(required = false) UUID centreId) {
        return communs.centresPayeursDetails(session.centre(centreId));
    }

    @GetMapping("/agences")
    public List<EntreeReferentiel> listerAgences(@RequestParam(required = false) UUID centreId) {
        return communs.agences(session.centre(centreId));
    }

    @GetMapping("/caisses")
    public List<EntreeReferentiel> listerCaisses(@RequestParam(required = false) UUID centreId) {
        return communs.caisses(session.centre(centreId));
    }

    // /transporteurs et /categories-transport : servis par patient-couverture (RF1, ADR 0122), mêmes URLs et même JSON.

    @GetMapping("/forfaits")
    public List<EntreeReferentiel> listerForfaits(@RequestParam(required = false) UUID centreId) {
        return communs.forfaits(session.centre(centreId));
    }

    @GetMapping("/etats-patients")
    public List<EntreeReferentiel> listerEtatsPatients() {
        return communs.etatsPatients();
    }
}
