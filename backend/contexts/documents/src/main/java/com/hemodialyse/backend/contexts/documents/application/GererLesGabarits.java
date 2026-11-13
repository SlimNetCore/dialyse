package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.FichierTeleverse;
import com.hemodialyse.backend.contexts.documents.domain.GabaritRefuse;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersions;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersionsRepository;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCentre;
import com.hemodialyse.backend.contexts.documents.domain.Pagination;
import com.hemodialyse.backend.contexts.documents.domain.VersionDeGabarit;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.JrxmlSecurityValidator;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.JrxmlSecurityValidator.Violation;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.VerificationDeGabarit;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage de la personnalisation des gabarits d'un centre (DOC3, ex {@code ModeleDocumentTemplateService}) : consulter l'historique,
 * lire un gabarit, déposer une version, la remettre en vigueur, revenir au gabarit livré. Toute opération est bornée au centre de la
 * session : le modèle d'un autre centre est traité comme inexistant ({@link GabaritIntrouvable}).
 *
 * <p>Le dépôt applique, dans l'ordre d'avant : forme du fichier (extension, vide, taille), gabarit livré disponible, UTF-8, liste blanche
 * de sécurité ({@link JrxmlSecurityValidator}, inchangée), compilation à l'essai. Rien n'est conservé si une anomalie est relevée.
 */
public class GererLesGabarits {

    private static final Logger journal = LoggerFactory.getLogger(GererLesGabarits.class);

    /** Résultat d'un dépôt : la version créée, ou les anomalies relevées (jamais les deux). */
    public record ResultatDuDepot(VersionDeGabarit version, List<Violation> anomalies) {
        public boolean accepte() {
            return version != null;
        }
    }

    /** Une page de l'historique et le numéro de la version en vigueur ({@code null} = gabarit livré). */
    public record PageDHistorique(List<VersionDeGabarit> versions, long total, int page, int taille, Integer versionEnVigueur) {
    }

    /** Le gabarit à télécharger pour édition. */
    public record GabaritATelecharger(String codeDuModele, String contenu) {
    }

    private final SessionCourante session;
    private final ModelesDuCentre modeles;
    private final GabaritsLivres livres;
    private final HistoriqueDesVersionsRepository historiques;
    private final VerificationDeGabarit compilation;
    private final UniteDeTravail unite;
    private final Clock horloge;
    private final Supplier<UUID> identifiants;
    private final JrxmlSecurityValidator validateur = new JrxmlSecurityValidator();

    public GererLesGabarits(SessionCourante session, ModelesDuCentre modeles, GabaritsLivres livres,
                            HistoriqueDesVersionsRepository historiques, VerificationDeGabarit compilation, UniteDeTravail unite,
                            Clock horloge, Supplier<UUID> identifiants) {
        this.session = session;
        this.modeles = modeles;
        this.livres = livres;
        this.historiques = historiques;
        this.compilation = compilation;
        this.unite = unite;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    /** Le centre à utiliser (celui de la session, ou le centre demandé pour un accès transverse) ; 403 sinon. */
    public CentreId exigerLeCentre(UUID centreDemande) {
        return session.exigerLeCentre(centreDemande);
    }

    public PageDHistorique historique(UUID centre, UUID modele, int page, int taille) {
        trouver(centre, modele);
        HistoriqueDesVersions historique = historiques.historique(centre, modele);
        Pagination pagination = Pagination.de(page, taille);
        List<VersionDeGabarit> toutes = historique.versionsRecentesDAbord();
        Integer enVigueur = historique.numeroEnVigueur().isPresent() ? historique.numeroEnVigueur().getAsInt() : null;
        return new PageDHistorique(pagination.tranche(toutes), toutes.size(), pagination.page(), pagination.taille(), enVigueur);
    }

    /**
     * Le gabarit à éditer : la version {@code numero} si fournie, sinon le gabarit en vigueur (version en vigueur, à défaut le gabarit
     * livré) ; {@code livre} force le gabarit livré.
     */
    public GabaritATelecharger gabarit(UUID centre, UUID modele, Integer numero, boolean livre) {
        ModeleDuCentre decrit = trouver(centre, modele);
        Optional<String> contenu;
        if (numero != null) {
            contenu = historiques.contenuDe(centre, modele, numero);
        } else if (livre) {
            contenu = livres.source(decrit.cheminDuGabarit());
        } else {
            contenu = historiques.contenuEnVigueur(centre, modele).or(() -> livres.source(decrit.cheminDuGabarit()));
        }
        return new GabaritATelecharger(decrit.code(), contenu.orElseThrow(GabaritIntrouvable::new));
    }

    public ResultatDuDepot deposer(UUID centre, UUID modele, String nomDuFichier, byte[] octets, String commentaire) {
        ModeleDuCentre decrit = trouver(centre, modele);
        String deposePar = session.utilisateur();
        FichierTeleverse fichier;
        String contenu;
        try {
            fichier = FichierTeleverse.accepter(nomDuFichier, octets, JrxmlSecurityValidator.MAX_BYTES);
            Optional<String> reference = livres.source(decrit.cheminDuGabarit());
            if (reference.isEmpty()) {
                return refus("NOT_CUSTOMIZABLE", "Ce modèle n'a pas de modèle d'origine personnalisable.");
            }
            contenu = fichier.contenu();
            List<Violation> anomalies = validateur.validate(contenu, reference.get());
            if (!anomalies.isEmpty()) {
                journal.warn("Modèle refusé (centre={}, modèle={}, par={}) : {}", centre, modele, deposePar, anomalies);
                return new ResultatDuDepot(null, anomalies);
            }
        } catch (GabaritRefuse e) {
            return refus(e.code(), e.detail());
        }
        try {
            compilation.verifierQuIlSeCompile(contenu);
        } catch (Exception e) {
            journal.warn("Compilation impossible (centre={}, modèle={}) : {}", centre, modele, e.getMessage());
            return refus("COMPILE_FAILED", messageRacine(e));
        }
        VersionDeGabarit version = unite.executer(() -> {
            HistoriqueDesVersions historique = historiques.historique(centre, modele);
            VersionDeGabarit deposee = historique.deposer(identifiants.get(), fichier, commentaire, deposePar, horloge.instant());
            historiques.enregistrer(historique);
            return deposee;
        });
        journal.info("Modèle personnalisé enregistré : centre={}, modèle={}, version={}, sha256={}, par={}", centre, modele,
                version.numero(), version.empreinte(), deposePar);
        return new ResultatDuDepot(version, List.of());
    }

    /** Remet une version en vigueur (retour arrière) ; {@link GabaritIntrouvable} si le modèle ou la version n'existe pas. */
    public VersionDeGabarit mettreEnVigueur(UUID centre, UUID modele, int numero) {
        trouver(centre, modele);
        VersionDeGabarit version = unite.executer(() -> {
            HistoriqueDesVersions historique = historiques.historique(centre, modele);
            Optional<VersionDeGabarit> activee = historique.mettreEnVigueur(numero);
            activee.ifPresent(v -> historiques.enregistrer(historique));
            return activee.orElse(null);
        });
        if (version == null) {
            throw new GabaritIntrouvable();
        }
        journal.info("Version de modèle activée : centre={}, modèle={}, version={}", centre, modele, numero);
        return version;
    }

    /** Revient au gabarit livré ; l'historique est conservé. Sans effet s'il n'y avait pas de version en vigueur. */
    public void reinitialiser(UUID centre, UUID modele) {
        trouver(centre, modele);
        unite.executer(() -> {
            HistoriqueDesVersions historique = historiques.historique(centre, modele);
            historique.reinitialiser();
            historiques.enregistrer(historique);
            return null;
        });
        journal.info("Retour au modèle d'origine : centre={}, modèle={}", centre, modele);
    }

    /** À la suppression d'un modèle : retire son historique. Sans effet si le modèle n'appartient pas au centre. */
    public void retirerLHistorique(UUID centre, UUID modele) {
        if (modeles.duCentre(centre, modele).isPresent()) {
            unite.executer(() -> {
                historiques.retirerTout(modele);
                return null;
            });
        }
    }

    private ModeleDuCentre trouver(UUID centre, UUID modele) {
        return modeles.duCentre(centre, modele).orElseThrow(GabaritIntrouvable::new);
    }

    private static ResultatDuDepot refus(String code, String detail) {
        return new ResultatDuDepot(null, List.of(new Violation(code, detail)));
    }

    private static String messageRacine(Throwable t) {
        Throwable racine = t;
        while (racine.getCause() != null && racine.getCause() != racine) {
            racine = racine.getCause();
        }
        String message = racine.getMessage();
        return message == null ? racine.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
    }
}
