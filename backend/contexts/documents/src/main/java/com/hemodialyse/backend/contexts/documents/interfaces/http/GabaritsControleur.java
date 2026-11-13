package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits;
import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits.GabaritATelecharger;
import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits.PageDHistorique;
import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits.ResultatDuDepot;
import com.hemodialyse.backend.contexts.documents.domain.VersionDeGabarit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Personnalisation d'un modèle d'impression (DOC3, ex {@code ModeleDocumentTemplateRestController}) : historique des versions, gabarit à
 * éditer, dépôt d'une version (validée strictement côté serveur), retour arrière sur une version, retour au gabarit livré. MÊMES URL,
 * MÊMES rôles (ADMIN ou SUPERADMIN, de classe), mêmes codes. Le centre demandé est confronté à celui du jeton et le modèle doit appartenir
 * à ce centre (sinon 404).
 *
 * <p>Écarts décidés (ADR 0163, KB-DOC3-1 à 3) : l'autorisation précède la lecture de la requête (403 avant 400/415) ; les formes illisibles
 * (identifiant, centre, page, version ou booléen illisibles, partie {@code file} absente, corps qui n'est pas du multipart, verbe non géré)
 * répondent 400/415/405 propres et non plus 500 ; les trois écritures sont journalisées sous leur propre action. Les paramètres sont reçus
 * en texte et lus APRÈS le contrôle du centre, pour que le refus de droit passe avant l'erreur de forme.
 */
@RestController
@RequestMapping("/api/v1/documents/modeles/{modeleId}")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
public class GabaritsControleur {

    /** Attribut de requête lu par le filtre d'audit de la plateforme : libellé précis de l'évènement. */
    static final String ATTRIBUT_LIBELLE_D_AUDIT = "hemodialyse.audit.libelle";

    private final GererLesGabarits gabarits;

    public GabaritsControleur(GererLesGabarits gabarits) {
        this.gabarits = gabarits;
    }

    @GetMapping("/versions")
    public ResponseEntity<VersionsReponse> historique(@PathVariable String modeleId, @RequestParam(required = false) String centerId,
                                                      @RequestParam(required = false) String page,
                                                      @RequestParam(required = false) String size) {
        UUID centre = centre(centerId);
        UUID modele = Entrees.identifiant(modeleId, "modeleId");
        int pageDemandee = Entrees.entier(page, "page", 0);
        int tailleDemandee = Entrees.entier(size, "size", 20);
        PageDHistorique historique = gabarits.historique(centre, modele, pageDemandee, tailleDemandee);
        return ResponseEntity.ok(VersionsReponse.de(historique));
    }

    /**
     * Télécharge le modèle à éditer : la version {@code version} si fournie, sinon le modèle en vigueur (version personnalisée active, à
     * défaut le modèle d'origine). {@code origine=true} force le modèle d'origine.
     */
    @GetMapping("/source")
    public ResponseEntity<byte[]> source(@PathVariable String modeleId, @RequestParam(required = false) String centerId,
                                         @RequestParam(required = false) String version, @RequestParam(required = false) String origine) {
        UUID centre = centre(centerId);
        UUID modele = Entrees.identifiant(modeleId, "modeleId");
        Integer numero = Entrees.entierOuNull(version, "version");
        boolean livre = Entrees.booleen(origine, "origine", false);
        GabaritATelecharger gabarit = gabarits.gabarit(centre, modele, numero, livre);

        String nom = nomDeFichierSur(gabarit.codeDuModele()) + (numero != null ? "-v" + numero : "") + ".jrxml";
        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(MediaType.APPLICATION_XML);
        entetes.setContentDisposition(ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build());
        entetes.add("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(entetes).body(gabarit.contenu().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/versions")
    public ResponseEntity<?> televerser(@PathVariable String modeleId, @RequestParam(required = false) String centerId,
                                        @RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam(required = false) String commentaire, HttpServletRequest requete)
            throws IOException, HttpMediaTypeNotSupportedException {
        UUID centre = centre(centerId);
        UUID modele = Entrees.identifiant(modeleId, "modeleId");
        exigerUnCorpsMultipart(requete);
        if (file == null) {
            throw new PartieAbsente("file");
        }
        ResultatDuDepot depot = gabarits.deposer(centre, modele, file.getOriginalFilename(), file.getBytes(), commentaire);
        if (depot.accepte()) {
            requete.setAttribute(ATTRIBUT_LIBELLE_D_AUDIT, "Gabarit de modèle téléversé : version " + depot.version().numero());
            return ResponseEntity.status(HttpStatus.CREATED).body(VersionReponse.de(depot.version()));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(Map.of("violations", depot.anomalies()));
    }

    @PostMapping("/versions/{version}/activate")
    public ResponseEntity<VersionReponse> activer(@PathVariable String modeleId, @PathVariable String version,
                                                  @RequestParam(required = false) String centerId, HttpServletRequest requete) {
        UUID centre = centre(centerId);
        UUID modele = Entrees.identifiant(modeleId, "modeleId");
        int numero = Entrees.entier(version, "version", 0);
        VersionDeGabarit activee = gabarits.mettreEnVigueur(centre, modele, numero);
        requete.setAttribute(ATTRIBUT_LIBELLE_D_AUDIT, "Version de gabarit activée : version " + numero);
        return ResponseEntity.ok(VersionReponse.de(activee));
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reinitialiser(@PathVariable String modeleId, @RequestParam(required = false) String centerId) {
        UUID centre = centre(centerId);
        UUID modele = Entrees.identifiant(modeleId, "modeleId");
        gabarits.reinitialiser(centre, modele);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ verbes non gérés : 405 (comme PL7 / KB-PL7-1)

    @RequestMapping(value = "/versions", method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> historiqueVerbeNonGere(HttpServletRequest requete) {
        return verbeNonGere(requete, "GET, HEAD, POST, OPTIONS");
    }

    @RequestMapping(value = "/source", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> sourceVerbeNonGere(HttpServletRequest requete) {
        return verbeNonGere(requete, "GET, HEAD, OPTIONS");
    }

    @RequestMapping(value = "/versions/{version}/activate", method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> activationVerbeNonGere(HttpServletRequest requete) {
        return verbeNonGere(requete, "POST, OPTIONS");
    }

    @RequestMapping(value = "/reset", method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> reinitialisationVerbeNonGere(HttpServletRequest requete) {
        return verbeNonGere(requete, "POST, OPTIONS");
    }

    private static ResponseEntity<ProblemDetail> verbeNonGere(HttpServletRequest requete, String autorises) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        probleme.setTitle("Methode non supportee");
        probleme.setDetail("Méthode non supportée : " + requete.getMethod());
        probleme.setProperty("code", "METHOD_NOT_ALLOWED");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, autorises).body(probleme);
    }

    // ------------------------------------------------------------------ outils

    /** Le centre : illisible = 400, autre que celui de la session = 403 (ce contrôle précède celui des autres paramètres). */
    private UUID centre(String centerId) {
        return gabarits.exigerLeCentre(Entrees.identifiant(centerId, "centerId")).valeur();
    }

    private static void exigerUnCorpsMultipart(HttpServletRequest requete) throws HttpMediaTypeNotSupportedException {
        String entete = requete.getContentType();
        try {
            if (entete != null && MediaType.MULTIPART_FORM_DATA.isCompatibleWith(MediaType.parseMediaType(entete))) {
                return;
            }
        } catch (org.springframework.http.InvalidMediaTypeException illisible) {
            // traité comme un type non supporté
        }
        throw entete == null ? new HttpMediaTypeNotSupportedException("Content-Type absent", List.of(MediaType.MULTIPART_FORM_DATA))
                : new HttpMediaTypeNotSupportedException(entete + " non supporté", List.of(MediaType.MULTIPART_FORM_DATA));
    }

    private static String nomDeFichierSur(String code) {
        String base = code == null ? "modele" : code.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return base.isBlank() ? "modele" : base.toLowerCase();
    }

    /** Page d'historique + numéro de la version personnalisée en vigueur ({@code null} = modèle d'origine). */
    public record VersionsReponse(List<VersionReponse> items, long total, int page, int size, Integer activeVersion) {
        static VersionsReponse de(PageDHistorique page) {
            return new VersionsReponse(page.versions().stream().map(VersionReponse::de).toList(), page.total(), page.page(), page.taille(),
                    page.versionEnVigueur());
        }
    }

    /** Une version de gabarit, sans son contenu : mêmes noms de champs qu'avant. */
    public record VersionReponse(UUID id, int version, boolean actif, String sha256, int tailleOctets, String commentaire,
                                 String uploadedBy, java.time.Instant uploadedAt) {
        static VersionReponse de(VersionDeGabarit v) {
            return new VersionReponse(v.id(), v.numero(), v.active(), v.empreinte(), v.tailleEnOctets(), v.commentaire(), v.deposePar(),
                    v.deposeLe());
        }
    }
}
