package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pièce jointe d'une intervention : un justificatif (bon, facture, photo) rangé à côté de l'intervention, jamais dedans, pour
 * ne pas charger jusqu'à 5 Mo d'octets avec elle (agrégat satellite, une seule entité). Garantit R22 : fichier non vide, de
 * 5 Mo au plus (octets exacts), de format PDF, PNG ou JPEG reconnu <b>sur son contenu entier</b> et dont l'extension du nom
 * est cohérente, nom assaini de 150 caractères au plus. R23 (50 pièces au plus par intervention) se vérifie avant de joindre,
 * par {@link #verifierLeQuota}, parce qu'il dépend des autres pièces. L'intervention n'est référencée que par son identité ;
 * son statut est examiné par {@link InterventionHote}.
 * <p>
 * Comportements du legacy reproduits (ADR 0019) : doublons acceptés (KB-GMAO-68), suppression physique (KB-GMAO-65), le type
 * de pièce n'a aucun lien avec le format. Corrigés le 2026-10-12 : structure vérifiée (KB-GMAO-59), nom assaini (KB-GMAO-60),
 * nom et type MIME re-contrôlés à la lecture (KB-GMAO-61).
 */
public final class PieceJointe {

    public static final long TAILLE_MAX_OCTETS = 5L * 1024 * 1024;
    public static final int MAX_PAR_INTERVENTION = 50;

    private static final String TYPE_PAR_DEFAUT_AU_TELECHARGEMENT = "application/octet-stream";

    private final PieceJointeId id;
    private final InterventionId interventionId;
    private final CentreId centreId;
    private final TypeDePiece type;
    private final String nom;
    private final String contentType;
    private final long taille;
    private final byte[] octets;
    private final UtilisateurId ajoutePar;
    private final Instant ajouteLe;

    private final List<EvenementDePieceJointe> evenements = new ArrayList<>();

    private PieceJointe(PieceJointeId id, InterventionId interventionId, CentreId centreId, TypeDePiece type, String nom,
                        String contentType, long taille, byte[] octets, UtilisateurId ajoutePar, Instant ajouteLe) {
        this.id = id;
        this.interventionId = interventionId;
        this.centreId = centreId;
        this.type = type;
        this.nom = nom;
        this.contentType = contentType;
        this.taille = taille;
        this.octets = octets;
        this.ajoutePar = ajoutePar;
        this.ajouteLe = ajouteLe;
    }

    /** R23 : refuse si l'intervention porte déjà {@value #MAX_PAR_INTERVENTION} pièces. À appeler avant {@link #joindre}. */
    public static void verifierLeQuota(long dejaJointes) {
        if (dejaJointes >= MAX_PAR_INTERVENTION) throw new QuotaDePiecesAtteint();
    }

    /** Joint un fichier (R22). Ordre des refus : vide, trop gros, format inconnu, fichier altéré, extension incohérente. */
    public static PieceJointe joindre(PieceJointeId id, InterventionId interventionId, CentreId centreId, TypeDePiece type,
                                      String nomFichier, byte[] contenu, UtilisateurId par, Instant maintenant) {
        if (contenu == null || contenu.length == 0) throw FichierRefuse.vide();
        if (contenu.length > TAILLE_MAX_OCTETS) throw FichierRefuse.tropVolumineux();
        FormatReconnu format = FormatReconnu.reconnaitre(contenu);
        NomDeFichier nom = NomDeFichier.assainir(nomFichier);
        nom.extension().ifPresent(extension -> {
            if (!format.extensions().contains(extension)) throw FichierRefuse.extensionIncoherente(extension, format);
        });
        PieceJointe piece = new PieceJointe(id, interventionId, centreId, type, nom.valeur(), format.contentType(),
                contenu.length, contenu, par, maintenant);
        piece.evenements.add(new PieceJointeAjoutee(id, interventionId, centreId, type, piece.nom, piece.taille, par, maintenant));
        return piece;
    }

    /** Reconstitution depuis la persistance (adapters seulement) : aucun événement, aucune règle rejouée. */
    public static PieceJointe reconstituer(PieceJointeId id, InterventionId interventionId, CentreId centreId, TypeDePiece type,
                                           String nom, String contentType, long taille, byte[] octets, UtilisateurId ajoutePar,
                                           Instant ajouteLe) {
        return new PieceJointe(id, interventionId, centreId, type, nom, contentType, taille, octets, ajoutePar, ajouteLe);
    }

    /** Retire la pièce (suppression physique, c'est l'appelant qui la fait) : lève {@link PieceJointeRetiree}. */
    public void retirer(UtilisateurId par, Instant maintenant) {
        evenements.add(new PieceJointeRetiree(id, interventionId, centreId, nom, taille, par, maintenant));
    }

    /** Nom à montrer ou à servir : assaini de nouveau, même s'il a été stocké avant la correction (KB-GMAO-61). */
    public String nomAffiche() {
        return NomDeFichier.assainir(nom).valeur();
    }

    /**
     * Type MIME à servir : celui du format détecté à l'envoi, tel que stocké, seulement s'il fait partie des trois formats
     * acceptés ; toute autre valeur héritée ({@code text/html}, type invalide...) est servie en {@code application/octet-stream}
     * (KB-GMAO-61). Jamais un type déclaré par le client.
     */
    public String contentTypeServi() {
        return FormatReconnu.depuisContentType(contentType).map(FormatReconnu::contentType)
                .orElse(TYPE_PAR_DEFAUT_AU_TELECHARGEMENT);
    }

    /** Événements levés depuis le dernier appel ; la liste est vidée. */
    public List<EvenementDePieceJointe> tirerEvenements() {
        List<EvenementDePieceJointe> leves = List.copyOf(evenements);
        evenements.clear();
        return leves;
    }

    public PieceJointeId id() {
        return id;
    }

    public InterventionId interventionId() {
        return interventionId;
    }

    public CentreId centreId() {
        return centreId;
    }

    public TypeDePiece type() {
        return type;
    }

    /** Nom tel que stocké ; pour l'affichage ou le téléchargement, voir {@link #nomAffiche()}. */
    public String nom() {
        return nom;
    }

    public String contentType() {
        return contentType;
    }

    public long taille() {
        return taille;
    }

    /** Octets du fichier (le tableau n'est pas copié : ne pas le modifier). */
    public byte[] octets() {
        return octets;
    }

    /** Auteur de l'ajout ; absent pour une pièce héritée sans auteur. */
    public UtilisateurId ajoutePar() {
        return ajoutePar;
    }

    public Instant ajouteLe() {
        return ajouteLe;
    }
}
