package com.hemodialyse.backend.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.JournauxDuCentre;
import com.hemodialyse.backend.domain.comptabilite.port.JournalRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * ACL temporaire (retirée avec les slices C2 à C7) : les journaux sont tenus par le contexte {@code comptabilite} (C1) ; les
 * services legacy du mapping et des pièces les lisent par le langage publié.
 */
@Component
public class JournauxDuContexte implements JournalRepositoryPort {

    private final JournauxDuCentre journaux;

    public JournauxDuContexte(JournauxDuCentre journaux) {
        this.journaux = journaux;
    }

    @Override
    public List<Journal> findByCenter(UUID centerId) {
        return journaux.journaux(centerId).stream()
                .map(j -> new Journal(JournalCode.de(j.code()), j.libelle(), j.actif())).toList();
    }
}
