package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.GererLesModeles;
import com.hemodialyse.backend.contexts.documents.domain.DefinitionDeModele;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCatalogue;
import com.hemodialyse.backend.contexts.documents.domain.RechercheDeModeles;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des modèles de document d'un centre (DOC4a, ex {@code DocumentRestController}) : liste, recherche, création, modification,
 * suppression. MÊMES URL, MÊMES rôles (création, modification et suppression : ADMIN ou SUPERADMIN ; liste et recherche : toute session),
 * mêmes codes de succès (200) et mêmes champs. Le centre demandé est confronté à celui du jeton.
 *
 * <p>Écarts décidés (ADR 0164, KB-DOC4A-1 à 5) : l'autorisation précède la lecture du corps et des paramètres (403 avant 400/415) ; les formes
 * illisibles (paramètre ou identifiant illisible ou absent, corps absent, mal formé, qui n'est pas du JSON, verbe non géré, représentation non
 * disponible) répondent 400/415/405/406 propres et non plus 500 ; un code déjà pris répond 409 et un champ obligatoire absent ou trop long
 * 400, et non 500 {@code DATA_ACCESS_ERROR} ; chaque écriture, et la recherche, est journalisée sous son action.
 */
@RestController
@RequestMapping("/api/v1/documents/modeles")
public class ModelesControleur {

    private final GererLesModeles modeles;
    private final ObjectMapper mapper;

    public ModelesControleur(GererLesModeles modeles, ObjectMapper mapper) {
        this.modeles = modeles;
        this.mapper = mapper;
    }

    /** Corps de la recherche : les champs sont lus en texte pour que leur forme soit contrôlée après le centre. */
    record RechercheRequete(String centerId, String typeDocument, Integer page, Integer size) {
    }

    /** Corps de la création et de la modification : mêmes champs qu'avant. */
    record ModeleRequete(String id, String centerId, String code, String libelle, String typeDocument, String cheminJrxml,
                         String formatImpression, String description, Boolean active) {
    }

    @GetMapping
    public ResponseEntity<ArrayList<Map<String, Object>>> lister(@RequestParam(required = false) String centerId,
                                                                 @RequestParam(required = false) String typeDocument) {
        UUID demande = Entrees.identifiantObligatoire(centerId, "centerId");
        UUID centre = modeles.exigerLeCentre(demande).valeur();
        return ResponseEntity.ok(lignes(modeles.lister(centre, typeDocument)));
    }

    @PostMapping("/search")
    public ResponseEntity<ArrayList<Map<String, Object>>> rechercher(HttpServletRequest requete) throws HttpMediaTypeNotSupportedException {
        RechercheRequete corps = CorpsJson.lire(requete, mapper, RechercheRequete.class);
        UUID demande = Entrees.identifiantObligatoire(corps.centerId(), "centerId");
        UUID centre = modeles.exigerLeCentre(demande).valeur();
        RechercheDeModeles recherche = RechercheDeModeles.de(centre, corps.typeDocument(), corps.page(), corps.size());
        return ResponseEntity.ok(lignes(modeles.rechercher(recherche)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping
    public ResponseEntity<Map<String, UUID>> creer(HttpServletRequest requete) throws HttpMediaTypeNotSupportedException {
        ModeleRequete corps = CorpsJson.lire(requete, mapper, ModeleRequete.class);
        UUID centre = modeles.exigerLeCentre(Entrees.identifiant(corps.centerId(), "centerId")).valeur();
        UUID id = Entrees.identifiant(corps.id(), "id");
        UUID cree = modeles.creer(id, definition(centre, corps));
        return ResponseEntity.ok(Map.of("id", cree));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, UUID>> modifier(@PathVariable String id, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        ModeleRequete corps = CorpsJson.lire(requete, mapper, ModeleRequete.class);
        UUID centre = modeles.exigerLeCentre(Entrees.identifiant(corps.centerId(), "centerId")).valeur();
        UUID modele = Entrees.identifiantObligatoire(id, "id");
        modeles.modifier(modele, definition(centre, corps));
        return ResponseEntity.ok(Map.of("id", modele));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> supprimer(@PathVariable String id, @RequestParam(required = false) String centerId) {
        UUID centre = modeles.exigerLeCentre(Entrees.identifiantObligatoire(centerId, "centerId")).valeur();
        UUID modele = Entrees.identifiantObligatoire(id, "id");
        modeles.supprimer(centre, modele);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ------------------------------------------------------------------ verbes non gérés : 405 (comme DOC3 / KB-DOC3-1)

    @RequestMapping(value = "", method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> collectionVerbeNonGere(HttpServletRequest requete) {
        return verbeNonGere(requete, "GET, HEAD, POST, OPTIONS");
    }

    @RequestMapping(value = "/search", method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> rechercheVerbeNonGere(HttpServletRequest requete) {
        return verbeNonGere(requete, "POST, OPTIONS");
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<ProblemDetail> modeleVerbeNonGere(HttpServletRequest requete) {
        return verbeNonGere(requete, "PUT, DELETE, OPTIONS");
    }

    private static ResponseEntity<ProblemDetail> verbeNonGere(HttpServletRequest requete, String autorises) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        probleme.setTitle("Methode non supportee");
        probleme.setDetail("Méthode non supportée : " + requete.getMethod());
        probleme.setProperty("code", "METHOD_NOT_ALLOWED");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, autorises).body(probleme);
    }

    // ------------------------------------------------------------------ outils

    private static DefinitionDeModele definition(UUID centre, ModeleRequete c) {
        return DefinitionDeModele.definir(centre, c.code(), c.libelle(), c.typeDocument(), c.cheminJrxml(), c.formatImpression(), c.description(),
                c.active());
    }

    /** Les lignes avec les noms de colonnes de la table, comme la liste les rendait (le contrat lu par l'écran des modèles). */
    private static ArrayList<Map<String, Object>> lignes(List<ModeleDuCatalogue> modeles) {
        ArrayList<Map<String, Object>> lignes = new ArrayList<>();
        for (ModeleDuCatalogue m : modeles) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("id", m.id());
            ligne.put("center_id", m.centre());
            ligne.put("code", m.code());
            ligne.put("libelle", m.libelle());
            ligne.put("type_document", m.type());
            ligne.put("chemin_jrxml", m.cheminDuGabarit());
            ligne.put("format_impression", m.format());
            ligne.put("description", m.description());
            ligne.put("active", m.actif());
            lignes.add(ligne);
        }
        return lignes;
    }
}
