package com.hemodialyse.backend.domain.organisation.model;

import com.hemodialyse.backend.domain.organisation.specification.SocieteDoitAvoirAuMoinsUnCentreActifSpecification;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aggregate Root — une société chapeaute un ou plusieurs centres d'hémodialyse.
 * <p>
 * <b>Invariant</b> : une société possède toujours au moins un centre, et au moins un centre <i>actif</i> tant
 * qu'elle est active. Une société ne peut donc être créée qu'avec son premier centre, et on ne peut ni
 * désactiver ni retirer (transférer) son dernier centre actif.
 * <p>
 * Classe pure : aucune dépendance Spring ni JPA (AGENTS.md §3).
 */
public class Societe {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,29}");
    private static final SocieteDoitAvoirAuMoinsUnCentreActifSpecification AU_MOINS_UN_CENTRE_ACTIF =
            new SocieteDoitAvoirAuMoinsUnCentreActifSpecification();

    private final UUID id;
    private final OffsetDateTime createdAt;
    private final List<Centre> centres = new ArrayList<>();
    private String code;
    private String raisonSociale;
    private String nif;
    private String nis;
    private String rc;
    private Coordonnees coordonnees;
    private String piedDePage;
    private boolean actif;

    /**
     * Reconstitution depuis la persistance : l'invariant est revérifié.
     */
    public Societe(UUID id, String code, String raisonSociale, String nif, String nis, String rc,
                   Coordonnees coordonnees, boolean actif, OffsetDateTime createdAt, List<Centre> centres) {
        if (id == null) throw new IllegalArgumentException("id de la société obligatoire");
        this.id = id;
        this.createdAt = createdAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : createdAt;
        this.actif = actif;
        appliquerIdentite(code, raisonSociale, nif, nis, rc, coordonnees);
        if (centres == null || centres.isEmpty()) {
            throw new BusinessException("SOCIETE_SANS_CENTRE", "Une société doit avoir au moins un centre");
        }
        this.centres.addAll(centres);
    }

    /**
     * Crée une société avec son premier centre (obligatoire).
     */
    public static Societe creer(String code, String raisonSociale, String nif, String nis, String rc,
                                Coordonnees coordonnees, Centre premierCentre) {
        if (premierCentre == null) {
            throw new BusinessException("SOCIETE_SANS_CENTRE", "Une société doit avoir au moins un centre");
        }
        return new Societe(UUID.randomUUID(), code, raisonSociale, nif, nis, rc, coordonnees, true,
                OffsetDateTime.now(ZoneOffset.UTC), List.of(premierCentre));
    }

    // ───────────────────────────── Société ─────────────────────────────

    private static String legalId(String value, String label) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > 40) {
            throw new BusinessException("SOCIETE_IDENTIFIANT_INVALIDE", "Le champ " + label + " dépasse 40 caractères");
        }
        return trimmed;
    }

    public void modifier(String code, String raisonSociale, String nif, String nis, String rc,
                         Coordonnees coordonnees) {
        appliquerIdentite(code, raisonSociale, nif, nis, rc, coordonnees);
    }

    /**
     * Mention imprimée en pied de page de tous les documents de la société (500 caractères maximum).
     */
    public void definirPiedDePage(String texte) {
        String trimmed = texte == null ? "" : texte.trim();
        if (trimmed.length() > 500) {
            throw new BusinessException("SOCIETE_PIED_PAGE_TROP_LONG", "Le pied de page dépasse 500 caractères");
        }
        this.piedDePage = trimmed.isEmpty() ? null : trimmed;
    }

    public void activer() {
        this.actif = true;
    }

    /**
     * Une société désactivée n'est plus proposée à la connexion ; ses centres sont conservés.
     */
    public void desactiver() {
        this.actif = false;
    }

    private void appliquerIdentite(String code, String raisonSociale, String nif, String nis, String rc,
                                   Coordonnees coordonnees) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalizedCode).matches()) {
            throw new BusinessException("SOCIETE_CODE_INVALIDE",
                    "Code de société invalide (2 à 30 caractères : lettres, chiffres, tiret, souligné)");
        }
        String name = raisonSociale == null ? "" : raisonSociale.trim();
        if (name.isEmpty() || name.length() > 200) {
            throw new BusinessException("SOCIETE_NOM_INVALIDE", "Raison sociale obligatoire (200 caractères maximum)");
        }
        this.code = normalizedCode;
        this.raisonSociale = name;
        this.nif = legalId(nif, "NIF");
        this.nis = legalId(nis, "NIS");
        this.rc = legalId(rc, "RC");
        this.coordonnees = coordonnees == null ? Coordonnees.VIDES : coordonnees;
    }

    // ───────────────────────────── Centres ─────────────────────────────

    public Centre ajouterCentre(Centre centre) {
        if (centre == null) throw new IllegalArgumentException("centre obligatoire");
        if (centres.stream().anyMatch(c -> c.id().equals(centre.id()))) {
            throw new BusinessException("CENTRE_DEJA_AFFECTE", "Ce centre est déjà rattaché à cette société");
        }
        centres.add(centre);
        return centre;
    }

    public void modifierCentre(UUID centreId, String code, String nom, Coordonnees coordonnees) {
        trouver(centreId).modifier(code, nom, coordonnees);
    }

    public void activerCentre(UUID centreId) {
        trouver(centreId).activer();
    }

    /**
     * @throws BusinessException si c'est le dernier centre actif de la société
     */
    public void desactiverCentre(UUID centreId) {
        Centre centre = trouver(centreId);
        if (!centre.actif()) return;
        List<Centre> apres = new ArrayList<>(centres);
        apres.remove(centre);
        garantirAuMoinsUnCentreActif(apres);
        centre.desactiver();
    }

    /**
     * Retire un centre de la société (pour le transférer ailleurs).
     *
     * @return le centre retiré
     * @throws BusinessException si la société n'aurait plus de centre actif
     */
    public Centre retirerCentre(UUID centreId) {
        Centre centre = trouver(centreId);
        List<Centre> apres = new ArrayList<>(centres);
        apres.remove(centre);
        garantirAuMoinsUnCentreActif(apres);
        centres.remove(centre);
        return centre;
    }

    private void garantirAuMoinsUnCentreActif(List<Centre> apres) {
        if (!AU_MOINS_UN_CENTRE_ACTIF.isSatisfiedBy(apres)) {
            throw new BusinessException("SOCIETE_DERNIER_CENTRE",
                    "Une société doit conserver au moins un centre actif : impossible de désactiver ou retirer son dernier centre");
        }
    }

    private Centre trouver(UUID centreId) {
        return centres.stream().filter(c -> c.id().equals(centreId)).findFirst()
                .orElseThrow(() -> new BusinessException("CENTRE_INTROUVABLE", "Centre introuvable dans cette société"));
    }

    public Optional<Centre> centre(UUID centreId) {
        return centres.stream().filter(c -> c.id().equals(centreId)).findFirst();
    }

    // ───────────────────────────── Accès ─────────────────────────────

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String raisonSociale() {
        return raisonSociale;
    }

    public String nif() {
        return nif;
    }

    public String nis() {
        return nis;
    }

    public String rc() {
        return rc;
    }

    public Coordonnees coordonnees() {
        return coordonnees;
    }

    public String piedDePage() {
        return piedDePage;
    }

    public boolean actif() {
        return actif;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public List<Centre> centres() {
        return Collections.unmodifiableList(centres);
    }
}
