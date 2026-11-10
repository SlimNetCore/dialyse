package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CarnetDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Transporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CategoriesDeTransport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.FicheDeTransporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.PageDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire des ports du carnet de transporteurs, des lectures et du langage publié (même jeu de tests de contrat que les
 * tables réelles). Les références (patients qui utilisent un transporteur) sont posées par {@link #utiliser}.
 */
public class TransporteursEnMemoire implements CarnetDeTransporteurs, LectureDesTransporteurs, Transporteurs {

    private final Map<UUID, Transporteur> lignes = new LinkedHashMap<>();
    private final Map<ReferenceDePatient, Long> references = new HashMap<>();
    private final Map<UUID, CategorieEnBase> categories = new LinkedHashMap<>();

    private record CategorieEnBase(UUID centre, String libelle) {
    }

    private record ReferenceDePatient(CentreId centreDuPatient, UUID transporteur) {
    }

    /** Un patient du centre donné utilise ce transporteur {@code fois} fois (aller et retour comptent chacun). */
    public void utiliser(CentreId centreDuPatient, UUID transporteur, int fois) {
        references.merge(new ReferenceDePatient(centreDuPatient, transporteur), (long) fois, Long::sum);
    }

    public UUID categorie(UUID centre, String libelle) {
        UUID id = UUID.randomUUID();
        categories.put(id, new CategorieEnBase(centre, libelle));
        return id;
    }

    @Override
    public Optional<Transporteur> trouver(CentreId centre, UUID id) {
        return Optional.ofNullable(lignes.get(id)).filter(t -> t.centre().equals(centre));
    }

    @Override
    public List<Transporteur> duCentre(CentreId centre) {
        return lignes.values().stream().filter(t -> t.centre().equals(centre))
                .sorted(Comparator.comparing(Transporteur::nom).thenComparing(Transporteur::id)).toList();
    }

    @Override
    public void enregistrer(Transporteur t) {
        lignes.put(t.id(), t);
    }

    @Override
    public void remplacer(Transporteur t) {
        if (lignes.get(t.id()) != null && lignes.get(t.id()).centre().equals(t.centre())) lignes.put(t.id(), t);
    }

    @Override
    public void supprimer(CentreId centre, UUID id) {
        trouver(centre, id).ifPresent(t -> lignes.remove(id));
    }

    @Override
    public long nombreDeReferences(CentreId centre, UUID id) {
        return references.getOrDefault(new ReferenceDePatient(centre, id), 0L);
    }

    @Override
    public PageDeTransporteurs page(CentreId centre, String recherche, int page, int taille) {
        String terme = recherche == null || recherche.isBlank() ? null : recherche.trim().toLowerCase(Locale.ROOT);
        List<Transporteur> trouves = new ArrayList<>(duCentre(centre).stream()
                .filter(t -> terme == null || t.nom().toLowerCase(Locale.ROOT).contains(terme)
                        || (t.telephone() != null && t.telephone().toLowerCase(Locale.ROOT).contains(terme)))
                .sorted(Comparator.comparing(Transporteur::nom).thenComparing(Transporteur::id)).toList());
        int debut = (int) Math.min((long) page * taille, trouves.size());
        List<FicheDeTransporteur> lignesDeLaPage = trouves.subList(debut, Math.min(debut + taille, trouves.size())).stream()
                .map(t -> new FicheDeTransporteur(t.id(), t.nom(), t.telephone())).toList();
        return new PageDeTransporteurs(lignesDeLaPage, trouves.size(), page, taille);
    }

    @Override
    public List<TransporteurPublie> lister(UUID centre) {
        return duCentre(new CentreId(centre)).stream().sorted(Comparator.comparing(Transporteur::nom).thenComparing(Transporteur::id))
                .map(t -> new TransporteurPublie(t.id(), t.nom())).toList();
    }

    @Override
    public Optional<String> nom(UUID centre, UUID id) {
        return trouver(new CentreId(centre), id).map(Transporteur::nom);
    }

    @Override
    public boolean existe(UUID centre, UUID id) {
        return trouver(new CentreId(centre), id).isPresent();
    }

    /** Le langage publié des catégories de transport (même double, autre vue). */
    public CategoriesDeTransport categories() {
        return centre -> categories.entrySet().stream().filter(e -> e.getValue().centre().equals(centre))
                .sorted(Comparator.comparing(e -> e.getValue().libelle()))
                .map(e -> new CategoriesDeTransport.CategorieDeTransportPubliee(e.getKey(), e.getValue().libelle())).toList();
    }
}
