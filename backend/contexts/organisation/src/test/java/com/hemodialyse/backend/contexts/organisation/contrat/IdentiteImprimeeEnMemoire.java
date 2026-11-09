package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Faux en mémoire de l'identité imprimée : il passe le MÊME contrat ({@link IdentiteImprimeeContrat}) que l'adapter JDBC. */
final class IdentiteImprimeeEnMemoire implements IdentiteImprimee {

    private record Centre(UUID societe, String nom, String adresse, String ville, String wilaya, String telephone, String email,
                          String siteWeb) {
    }

    private final Map<UUID, IdentiteImprimeeContrat.Fiche> societes = new LinkedHashMap<>();
    private final Map<UUID, Centre> centres = new LinkedHashMap<>();

    void societe(UUID id, IdentiteImprimeeContrat.Fiche fiche) {
        societes.put(id, fiche);
    }

    void centre(UUID id, UUID societe, String nom, String adresse, String ville, String wilaya, String telephone, String email,
                String siteWeb) {
        centres.put(id, new Centre(societe, nom, adresse, ville, wilaya, telephone, email, siteWeb));
    }

    private static String texte(String brut) {
        return brut == null ? "" : brut.trim();
    }

    private static IdentiteDeLaSociete publiee(IdentiteImprimeeContrat.Fiche f) {
        byte[] logo = f.logo() != null && f.logo().length > 0 ? f.logo() : null;
        return new IdentiteDeLaSociete(texte(f.raisonSociale()), texte(f.adresse()), texte(f.ville()), texte(f.wilaya()),
                texte(f.telephone()), texte(f.email()), texte(f.siteWeb()), texte(f.nif()), texte(f.nis()), texte(f.rc()),
                texte(f.piedDePage()), logo);
    }

    @Override
    public Optional<IdentiteDuCentre> duCentre(UUID centre) {
        Centre c = centres.get(centre);
        if (c == null) return Optional.empty();
        IdentiteImprimeeContrat.Fiche fiche = societes.get(c.societe());
        return Optional.of(new IdentiteDuCentre(texte(c.nom()), texte(c.adresse()), texte(c.ville()), texte(c.wilaya()),
                texte(c.telephone()), texte(c.email()), texte(c.siteWeb()),
                fiche == null ? IdentiteDeLaSociete.vide() : publiee(fiche)));
    }

    @Override
    public Optional<IdentiteDeLaSociete> deLaSociete(UUID societe) {
        IdentiteImprimeeContrat.Fiche fiche = societes.get(societe);
        return fiche == null ? Optional.empty() : Optional.of(publiee(fiche));
    }
}
