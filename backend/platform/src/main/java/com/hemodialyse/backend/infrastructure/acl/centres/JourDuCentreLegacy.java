package com.hemodialyse.backend.infrastructure.acl.centres;

import com.hemodialyse.backend.application.centre.JourDuCentre;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * ACL vers le langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : le fuseau d'un centre est celui de sa fiche ; centre
 * inconnu, fuseau absent, vide ou illisible : le fuseau par défaut ({@code app.planification.fuseau-par-defaut}, Africa/Algiers).
 * Plus aucun SQL. Le nom {@code ...Legacy} est conservé : renommer cette classe de la plateforme créerait un fichier absent de
 * {@code covered.json} (hook de sécurité legacy), ce qui demande l'accord de l'utilisateur (voir O4-R1).
 */
@Component
public class JourDuCentreLegacy implements JourDuCentre {

    private final Function<UUID, Optional<String>> fuseaux;
    private final Clock horloge;
    private final ZoneId fuseauParDefaut;

    @Autowired
    public JourDuCentreLegacy(AnnuaireDesCentres centres,
                              @Value("${app.planification.fuseau-par-defaut:Africa/Algiers}") String fuseauParDefaut) {
        this(centre -> centres.fuseauHoraireDe(centre).map(ZoneId::getId), Clock.systemUTC(), ZoneId.of(fuseauParDefaut));
    }

    /** Pour les tests : les fuseaux et l'horloge sont fournis. */
    public JourDuCentreLegacy(Function<UUID, Optional<String>> fuseaux, Clock horloge, ZoneId fuseauParDefaut) {
        this.fuseaux = fuseaux;
        this.horloge = horloge;
        this.fuseauParDefaut = fuseauParDefaut;
    }

    @Override
    public LocalDate aujourdhui(UUID centre) {
        return LocalDate.now(horloge.withZone(fuseauDe(centre)));
    }

    ZoneId fuseauDe(UUID centre) {
        if (centre == null) return fuseauParDefaut;
        String identifiant = fuseaux.apply(centre).orElse(null);
        if (identifiant == null) return fuseauParDefaut;
        try {
            return ZoneId.of(identifiant.trim());
        } catch (DateTimeException e) {
            return fuseauParDefaut;
        }
    }
}
