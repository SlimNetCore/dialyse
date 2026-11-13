package com.hemodialyse.backend.contexts.documents.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Agrégat : l'historique des versions de gabarit d'UN modèle d'UN centre (racine, identité = centre + modèle).
 *
 * <p>Invariants : (1) les numéros se suivent à partir de 1 et ne sont jamais réutilisés (le prochain est le plus grand + 1, même quand
 * plus aucune version n'est en vigueur) ; (2) AU PLUS UNE version est en vigueur ; aucune version en vigueur = le gabarit livré est
 * utilisé ; (3) une version déposée est conservée, jamais modifiée ni supprimée (l'historique ne se retire qu'avec le modèle) ; (4) un
 * dépôt met la nouvelle version en vigueur. Le contenu d'une version n'est pas chargé : il accompagne seulement le dépôt en attente.
 */
public final class HistoriqueDesVersions {

    /** Le dépôt à enregistrer : la version créée et son contenu. */
    public record Depot(VersionDeGabarit version, String contenu) {
    }

    private final UUID centre;
    private final UUID modele;
    private final List<VersionDeGabarit> versions;
    private Depot depotEnAttente;

    private HistoriqueDesVersions(UUID centre, UUID modele, List<VersionDeGabarit> versions) {
        this.centre = centre;
        this.modele = modele;
        this.versions = versions;
    }

    /** Reconstitue l'historique tel que la base le tient (les versions peuvent arriver dans n'importe quel ordre). */
    public static HistoriqueDesVersions de(UUID centre, UUID modele, List<VersionDeGabarit> versions) {
        List<VersionDeGabarit> triees = new ArrayList<>(versions);
        triees.sort(Comparator.comparingInt(VersionDeGabarit::numero));
        return new HistoriqueDesVersions(centre, modele, triees);
    }

    public UUID centre() {
        return centre;
    }

    public UUID modele() {
        return modele;
    }

    /** Les versions, de la plus ancienne à la plus récente. */
    public List<VersionDeGabarit> versions() {
        return List.copyOf(versions);
    }

    /** Les versions, de la plus récente à la plus ancienne (ordre de l'historique affiché). */
    public List<VersionDeGabarit> versionsRecentesDAbord() {
        List<VersionDeGabarit> inversees = new ArrayList<>(versions);
        java.util.Collections.reverse(inversees);
        return List.copyOf(inversees);
    }

    /** Le numéro de la version en vigueur ; vide = le gabarit livré est utilisé. */
    public OptionalInt numeroEnVigueur() {
        return versions.stream().filter(VersionDeGabarit::active).mapToInt(VersionDeGabarit::numero).max();
    }

    public Optional<VersionDeGabarit> version(int numero) {
        return versions.stream().filter(v -> v.numero() == numero).findFirst();
    }

    /**
     * Dépose un fichier : une nouvelle version, la suivante, qui prend la place de celle en vigueur.
     *
     * @param id identifiant de la version (fourni par l'application)
     */
    public VersionDeGabarit deposer(UUID id, FichierTeleverse fichier, String commentaire, String deposePar, Instant deposeLe) {
        int suivant = versions.stream().mapToInt(VersionDeGabarit::numero).max().orElse(0) + 1;
        retirerDeLaVigueur();
        VersionDeGabarit version = new VersionDeGabarit(id, suivant, true, fichier.empreinte(), fichier.tailleEnOctets(),
                CommentaireDeDepot.nettoye(commentaire), deposePar, deposeLe);
        versions.add(version);
        depotEnAttente = new Depot(version, fichier.contenu());
        return version;
    }

    /** Remet en vigueur une version déjà déposée (retour arrière) ; vide si ce numéro n'existe pas (rien ne change alors). */
    public Optional<VersionDeGabarit> mettreEnVigueur(int numero) {
        Optional<VersionDeGabarit> cible = version(numero);
        if (cible.isEmpty()) {
            return Optional.empty();
        }
        retirerDeLaVigueur();
        VersionDeGabarit activee = cible.get().activee();
        versions.replaceAll(v -> v.numero() == numero ? activee : v);
        return Optional.of(activee);
    }

    /** Revient au gabarit livré : plus aucune version en vigueur, l'historique est conservé. Sans effet s'il n'y en avait pas. */
    public void reinitialiser() {
        retirerDeLaVigueur();
    }

    /** Le dépôt créé par {@link #deposer} et pas encore enregistré. */
    public Optional<Depot> depotEnAttente() {
        return Optional.ofNullable(depotEnAttente);
    }

    private void retirerDeLaVigueur() {
        versions.replaceAll(VersionDeGabarit::desactivee);
    }
}
