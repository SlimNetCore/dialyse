package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.Machine;
import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.Protocole;
import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Réponses JSON des routes {@code /api/v1/organisation/*} : jamais d'entité de domaine sérialisée (le domaine n'a pas d'accesseurs
 * JavaBean et son état interne n'est pas un contrat).
 */
public final class ReponsesDuReferentiel {
    private ReponsesDuReferentiel() {
    }

    public record CentreDetail(UUID id, UUID societeId, String code, String nom, String adresse, String ville, String wilaya,
                               String telephone, String email, String siteWeb, String fuseauHoraire, boolean actif) {
        public static CentreDetail de(Centre c) {
            var k = c.coordonnees();
            return new CentreDetail(c.id().valeur(), c.societeId().valeur(), c.code(), c.nom(), k.adresse(), k.ville(),
                    k.wilaya(), k.telephone(), k.email(), k.siteWeb(), c.fuseauHoraire(), c.actif());
        }
    }

    public record SalleReponse(UUID id, UUID centreId, String code, String nom, boolean ouverte) {
        public static SalleReponse de(Salle s) {
            return new SalleReponse(s.id().valeur(), s.centreId().valeur(), s.code(), s.nom(), s.ouverte());
        }
    }

    public record CreneauReponse(UUID id, UUID centreId, String code, String libelle) {
        public static CreneauReponse de(Creneau c) {
            return new CreneauReponse(c.id().valeur(), c.centreId().valeur(), c.code(), c.libelle());
        }
    }

    public record MedecinReponse(UUID id, UUID centreId, String nom, String prenom, String specialite, boolean actif) {
        public static MedecinReponse de(Medecin m) {
            return new MedecinReponse(m.id().valeur(), m.centreId().valeur(), m.nom(), m.prenom(), m.specialite(), m.actif());
        }
    }

    public record MachineReponse(UUID id, UUID centreId, String code, String libelle, boolean actif) {
        public static MachineReponse de(Machine m) {
            return new MachineReponse(m.id().valeur(), m.centreId().valeur(), m.code(), m.libelle(), m.actif());
        }
    }

    public record ProtocoleReponse(UUID id, UUID centreId, String code, String libelle) {
        public static ProtocoleReponse de(Protocole p) {
            return new ProtocoleReponse(p.id().valeur(), p.centreId().valeur(), p.code(), p.libelle());
        }
    }

    public static <T, R> List<R> liste(List<T> source, Function<T, R> conversion) {
        return source.stream().map(conversion).toList();
    }

    public static <T, R> PagedResult<R> page(PagedResult<T> source, Function<T, R> conversion) {
        return PagedResult.of(liste(source.items(), conversion), source.total(), source.page(), source.size());
    }
}
