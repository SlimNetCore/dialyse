package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.JournalRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.JournauxUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.List;
import java.util.UUID;

/**
 * Service de domaine pur — journaux comptables d'un centre.
 * <p>
 * Un centre qui n'a rien paramétré voit les journaux par défaut ; ils sont enregistrés à sa première modification, pour
 * qu'il parte d'une liste complète. Un journal choisi pour une opération ne peut être ni désactivé ni supprimé ; un
 * journal qui porte déjà des écritures ne se supprime pas (il se désactive).
 */
public class JournauxService implements JournauxUseCase {

    private final JournalRepositoryPort journaux;
    private final MappingComptablePort mappings;
    private final EcritureComptableRepositoryPort ecritures;

    public JournauxService(JournalRepositoryPort journaux, MappingComptablePort mappings,
                           EcritureComptableRepositoryPort ecritures) {
        this.journaux = journaux;
        this.mappings = mappings;
        this.ecritures = ecritures;
    }

    @Override
    public List<Journal> lister(UUID centerId) {
        List<Journal> enregistres = journaux.findByCenter(centerId);
        return enregistres.isEmpty() ? Journal.parDefaut() : enregistres;
    }

    @Override
    public Journal enregistrer(UUID centerId, Journal journal) {
        materialiserDefauts(centerId);
        if (!journal.actif() && estUtilise(centerId, journal.code())) {
            throw new BusinessException("JOURNAL_UTILISE",
                    "Le journal " + journal.code() + " est choisi pour une opération : changez-la avant de le désactiver");
        }
        journaux.save(centerId, journal);
        return journal;
    }

    @Override
    public void supprimer(UUID centerId, JournalCode code) {
        materialiserDefauts(centerId);
        if (journaux.findByCenter(centerId).stream().noneMatch(j -> j.code().equals(code))) {
            throw new BusinessException("JOURNAL_INTROUVABLE", "Journal " + code + " introuvable");
        }
        if (estUtilise(centerId, code)) {
            throw new BusinessException("JOURNAL_UTILISE",
                    "Le journal " + code + " est choisi pour une opération : changez-la avant de le supprimer");
        }
        if (ecritures.existsByJournal(centerId, code)) {
            throw new BusinessException("JOURNAL_AVEC_ECRITURES",
                    "Le journal " + code + " porte des écritures : désactivez-le au lieu de le supprimer");
        }
        journaux.delete(centerId, code);
    }

    private boolean estUtilise(UUID centerId, JournalCode code) {
        return mappings.findByCenterId(centerId).journaux().containsValue(code);
    }

    /**
     * Première modification : les journaux par défaut deviennent ceux, enregistrés, du centre.
     */
    private void materialiserDefauts(UUID centerId) {
        if (journaux.findByCenter(centerId).isEmpty()) {
            Journal.parDefaut().forEach(j -> journaux.save(centerId, j));
        }
    }
}
