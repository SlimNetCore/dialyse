package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDuCatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Forfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.FicheDeForfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.PageDeForfaits;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire des ports du catalogue de forfaits, de sa lecture et du langage publié (même jeu de tests de contrat que les
 * tables réelles).
 */
public class ForfaitsEnMemoire implements CatalogueDeForfaits, LectureDuCatalogueDeForfaits, ForfaitsDuCentre {

    private final Map<UUID, Forfait> lignes = new LinkedHashMap<>();

    @Override
    public Optional<Forfait> trouver(CentreId centre, UUID id) {
        return Optional.ofNullable(lignes.get(id)).filter(f -> f.centre().equals(centre));
    }

    @Override
    public List<Forfait> duCentre(CentreId centre) {
        return lignes.values().stream().filter(f -> f.centre().equals(centre)).toList();
    }

    @Override
    public void enregistrer(Forfait f) {
        lignes.put(f.id(), f);
    }

    @Override
    public void remplacer(Forfait f) {
        if (lignes.get(f.id()) != null && lignes.get(f.id()).centre().equals(f.centre())) lignes.put(f.id(), f);
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        trouver(centre, id).ifPresent(f -> lignes.remove(id));
    }

    @Override
    public PageDeForfaits page(CentreId centre, String recherche, int page, int taille) {
        String terme = recherche == null || recherche.isBlank() ? null : recherche.trim().toLowerCase(Locale.ROOT);
        List<Forfait> trouves = new ArrayList<>(duCentre(centre).stream()
                .filter(f -> terme == null || f.code().toLowerCase(Locale.ROOT).contains(terme)
                        || f.libelle().toLowerCase(Locale.ROOT).contains(terme))
                .sorted(Comparator.comparing(Forfait::code).thenComparing(Forfait::id)).toList());
        int debut = (int) Math.min((long) page * taille, trouves.size());
        List<FicheDeForfait> lignesDeLaPage = trouves.subList(debut, Math.min(debut + taille, trouves.size())).stream()
                .map(f -> new FicheDeForfait(f.id(), f.code(), f.libelle(), f.prix())).toList();
        return new PageDeForfaits(lignesDeLaPage, trouves.size(), page, taille);
    }

    // ---- langage publié

    private static ForfaitPublie publie(Forfait f) {
        return new ForfaitPublie(f.id(), f.code(), f.libelle(), f.prix());
    }

    @Override
    public List<ForfaitPublie> lister(UUID centre) {
        return duCentre(new CentreId(centre)).stream().sorted(Comparator.comparing(Forfait::code)).map(ForfaitsEnMemoire::publie).toList();
    }

    @Override
    public Optional<ForfaitPublie> trouver(UUID centre, UUID id) {
        return trouver(new CentreId(centre), id).map(ForfaitsEnMemoire::publie);
    }

    @Override
    public Optional<ForfaitPublie> parCode(UUID centre, String code) {
        if (code == null) return Optional.empty();
        String cle = code.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return duCentre(new CentreId(centre)).stream().filter(f -> f.cle().equals(cle)).findFirst().map(ForfaitsEnMemoire::publie);
    }

    @Override
    public Optional<BigDecimal> prix(UUID centre, UUID id) {
        return trouver(new CentreId(centre), id).map(Forfait::prix);
    }

    @Override
    public boolean existe(UUID centre, UUID id) {
        return trouver(new CentreId(centre), id).isPresent();
    }
}
