package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire de la reprise des soldes : mêmes règles que l'adaptateur JDBC (vérifié par le contrat partagé).
 */
public class RepriseDesSoldesEnMemoire implements RepriseDesSoldes {

    private record Reglement(String auteur) {
    }

    private static final class Facture {
        final UUID centre;
        final String numero;
        final UUID patient;
        BigDecimal ttc;
        int lignes = 1;
        final List<Reglement> reglements = new ArrayList<>();

        Facture(UUID centre, String numero, UUID patient, BigDecimal ttc) {
            this.centre = centre;
            this.numero = numero;
            this.patient = patient;
            this.ttc = ttc;
        }
    }

    private final Map<UUID, Facture> factures = new LinkedHashMap<>();

    private static boolean regle(SoldeDOuverture s) {
        return s.montantRegle() != null && s.montantRegle().signum() > 0;
    }

    @Override
    public Optional<UUID> factureDuNumero(UUID centre, String numero) {
        return factures.entrySet().stream().filter(e -> e.getValue().centre.equals(centre) && e.getValue().numero.equals(numero))
                .map(Map.Entry::getKey).findFirst();
    }

    @Override
    public long reglementsHorsReprise(UUID centre, UUID facture) {
        Facture f = factures.get(facture);
        return f == null || !f.centre.equals(centre) ? 0
                : f.reglements.stream().filter(r -> !AUTEUR_DE_LA_REPRISE.equals(r.auteur())).count();
    }

    @Override
    public UUID ouvrir(UUID centre, SoldeDOuverture solde) {
        UUID id = UUID.randomUUID();
        Facture f = new Facture(centre, solde.numero(), solde.patient(), solde.montantTtc());
        if (regle(solde)) f.reglements.add(new Reglement(AUTEUR_DE_LA_REPRISE));
        factures.put(id, f);
        return id;
    }

    @Override
    public void remplacer(UUID centre, UUID facture, SoldeDOuverture solde) {
        Facture f = factures.get(facture);
        if (f == null || !f.centre.equals(centre)) return;
        f.ttc = solde.montantTtc();
        f.lignes = 1;
        f.reglements.removeIf(r -> AUTEUR_DE_LA_REPRISE.equals(r.auteur()));
        if (regle(solde)) f.reglements.add(new Reglement(AUTEUR_DE_LA_REPRISE));
    }

    @Override
    public void supprimer(UUID centre, UUID facture) {
        Facture f = factures.get(facture);
        if (f != null && f.centre.equals(centre)) factures.remove(facture);
    }

    @Override
    public List<String> facturesDuPatient(UUID patient) {
        return factures.entrySet().stream().filter(e -> e.getValue().patient.equals(patient))
                .map(e -> e.getKey().toString().toLowerCase()).toList();
    }

    public void poserUnReglementDeLaPlateforme(UUID facture) {
        factures.get(facture).reglements.add(new Reglement("caissier"));
    }

    public int lignes(UUID facture) {
        Facture f = factures.get(facture);
        return f == null ? 0 : f.lignes;
    }

    public int reglementsDeReprise(UUID facture) {
        Facture f = factures.get(facture);
        return f == null ? 0 : (int) f.reglements.stream().filter(r -> AUTEUR_DE_LA_REPRISE.equals(r.auteur())).count();
    }

    public BigDecimal totalTtc(UUID facture) {
        return factures.get(facture).ttc;
    }
}
