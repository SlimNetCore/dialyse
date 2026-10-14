package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Intervenant du référentiel GMAO (racine d'agrégat) : technicien interne ou prestataire externe, avec son tarif horaire.
 * Garantit R24 : nom et nature requis, tarif positif ou nul, un intervenant désactivé n'est jamais supprimé.
 * <p>
 * Comportements du legacy reproduits (ADR 0019) : la correction est un remplacement total (téléphone, email et tarif
 * omis sont effacés, KB-GMAO-30) ; la désactivation est idempotente et irréversible par commande (KB-GMAO-24/25) ;
 * un intervenant désactivé reste corrigeable et reste désactivé.
 */
public final class Intervenant {

    private final IntervenantId id;
    private final CentreId centreId;

    private String nom;
    private NatureIntervenant nature;
    private String telephone;
    private String email;
    private TarifIntervenant tarif;
    private boolean actif;

    private final List<EvenementDeIntervenant> evenements = new ArrayList<>();

    private Intervenant(IntervenantId id, CentreId centreId, String nom, NatureIntervenant nature, String telephone,
                        String email, TarifIntervenant tarif, boolean actif) {
        this.id = id;
        this.centreId = centreId;
        this.nom = nom;
        this.nature = nature;
        this.telephone = telephone;
        this.email = email;
        this.tarif = tarif;
        this.actif = actif;
    }

    /** Enregistre un intervenant actif. Le nom n'est ni rogné ni dédoublonné (KB-GMAO-27). {@code tarif} nul = aucun tarif. */
    public static Intervenant enregistrer(IntervenantId id, CentreId centreId, String nom, NatureIntervenant nature,
                                          String telephone, String email, TarifIntervenant tarif, UtilisateurId par,
                                          Instant maintenant) {
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        exigerNomEtNature(nom, nature);
        Intervenant intervenant = new Intervenant(id, centreId, nom, nature, telephone, email, tarif, true);
        intervenant.evenements.add(new IntervenantEnregistre(id, centreId, nature, par, maintenant));
        return intervenant;
    }

    /** Reconstitution depuis la persistance (adapters seulement) : aucun événement, aucune règle rejouée. */
    public static Intervenant reconstituer(IntervenantId id, CentreId centreId, String nom, NatureIntervenant nature,
                                           String telephone, String email, TarifIntervenant tarif, boolean actif) {
        return new Intervenant(id, centreId, nom, nature, telephone, email, tarif, actif);
    }

    /**
     * Remplace nom, nature, coordonnées et tarif (remplacement total, KB-GMAO-30). Ne change ni l'identité, ni le centre,
     * ni l'état actif. Événements : coordonnées corrigées si nom, nature, téléphone ou email changent ; tarif révisé si la
     * valeur du tarif change (l'échelle reçue est conservée même à valeur égale).
     */
    public void corriger(String nouveauNom, NatureIntervenant nouvelleNature, String nouveauTelephone, String nouvelEmail,
                         TarifIntervenant nouveauTarif, UtilisateurId par, Instant maintenant) {
        exigerNomEtNature(nouveauNom, nouvelleNature);
        boolean coordonneesChangees = !nouveauNom.equals(nom) || nouvelleNature != nature
                || !Objects.equals(nouveauTelephone, telephone) || !Objects.equals(nouvelEmail, email);
        boolean tarifChange = !memeTarif(tarif, nouveauTarif);
        TarifIntervenant ancienTarif = tarif;

        nom = nouveauNom;
        nature = nouvelleNature;
        telephone = nouveauTelephone;
        email = nouvelEmail;
        tarif = nouveauTarif;

        if (coordonneesChangees) {
            evenements.add(new CoordonneesIntervenantCorrigees(id, centreId, nature, par, maintenant));
        }
        if (tarifChange) {
            evenements.add(new TarifIntervenantRevise(id, centreId, ancienTarif, nouveauTarif, par, maintenant));
        }
    }

    /** Désactive l'intervenant (jamais supprimé, R24). Idempotent : sans effet ni événement s'il est déjà désactivé (KB-GMAO-25). */
    public void desactiver(UtilisateurId par, Instant maintenant) {
        if (!actif) {
            return;
        }
        actif = false;
        evenements.add(new IntervenantDesactive(id, centreId, par, maintenant));
    }

    /** Retire et retourne les événements levés depuis la dernière publication. */
    public List<EvenementDeIntervenant> tirerEvenements() {
        List<EvenementDeIntervenant> tires = List.copyOf(evenements);
        evenements.clear();
        return tires;
    }

    private static void exigerNomEtNature(String nom, NatureIntervenant nature) {
        if (nom == null || nom.isBlank()) throw new NomRequis();
        if (nature == null) throw new NatureRequise();
    }

    private static boolean memeTarif(TarifIntervenant a, TarifIntervenant b) {
        return a == null ? b == null : a.memeValeurQue(b);
    }

    public IntervenantId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String nom() {
        return nom;
    }

    public NatureIntervenant nature() {
        return nature;
    }

    public String telephone() {
        return telephone;
    }

    public String email() {
        return email;
    }

    /** Tarif horaire, ou {@code null} si l'intervenant n'en a pas. */
    public TarifIntervenant tarif() {
        return tarif;
    }

    public boolean estActif() {
        return actif;
    }
}
