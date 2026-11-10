package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLesPayeurs;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.UUID;

/**
 * Caches des listes de choix des payeurs par centre (caches {@code ref.caisses}, {@code ref.agences}, {@code ref.centresPayeurs},
 * {@code ref.centresPayeursDetails}, comme aujourd'hui, clé = identifiant du centre). La garde de centre est exercée par le contrôleur
 * AVANT l'appel. Invalidés par niveau à chaque écriture (après commit, {@code InvalidationDesListesDePayeurs}).
 */
public class ListesDePayeursEnCache {

    private final ConsulterLesPayeurs consulter;

    public ListesDePayeursEnCache(ConsulterLesPayeurs consulter) {
        this.consulter = consulter;
    }

    @Cacheable(cacheNames = "ref.caisses", key = "#centre.toString()")
    public List<ChoixDePayeur> caisses(UUID centre) {
        return consulter.caisses(centre).stream()
                .map(c -> new ChoixDePayeur(c.id().toString(), c.code(), c.nom(), null, null, c.type(), null)).toList();
    }

    @Cacheable(cacheNames = "ref.agences", key = "#centre.toString()")
    public List<ChoixDePayeur> agences(UUID centre) {
        return consulter.agences(centre).stream()
                .map(a -> new ChoixDePayeur(a.id().toString(), a.code(), a.nom(), null, null, null, null)).toList();
    }

    @Cacheable(cacheNames = "ref.centresPayeurs", key = "#centre.toString()")
    public List<ChoixDePayeur> centresPayeurs(UUID centre) {
        return consulter.centresPayeurs(centre).stream()
                .map(c -> new ChoixDePayeur(c.id().toString(), c.code(), c.nom(), c.adresse(), null, null, null)).toList();
    }

    @Cacheable(cacheNames = "ref.centresPayeursDetails", key = "#centre.toString()")
    public List<DetailDePayeur> details(UUID centre) {
        return consulter.details(centre).stream()
                .map(d -> new DetailDePayeur(d.id().toString(), d.codeCentrePayeur(), d.libelleCentrePayeur(), d.adresseCentrePayeur(),
                        d.codeAgence(), d.libelleAgence(), d.codeCaisse(), d.libelleCaisse())).toList();
    }

    /** Une ligne de liste de choix : même forme JSON que les autres référentiels communs (sept champs, nuls compris). */
    public record ChoixDePayeur(String id, String code, String nom, String adresse, String prenom, String libelle,
                                Boolean gereParLot) {
    }

    /** Un centre payeur avec son agence et sa caisse : même forme JSON qu'avant. */
    public record DetailDePayeur(String id, String codeCentrePayeur, String libelleCentrePayeur, String adresseCentrePayeur,
                                 String codeAgence, String libelleAgence, String codeCaisse, String libelleCaisse) {
    }
}
