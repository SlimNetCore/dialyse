package com.hemodialyse.backend.contexts.comptabilite.application.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.ExportComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.ExportComptablePort;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Produit le fichier du journal et marque uniquement les écritures validées qui viennent d'être exportées.
 */
public final class ExporterUnJournal {
    private final EcritureComptableRepository ecritures;
    private final ExportComptablePort exportateur;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;

    public ExporterUnJournal(EcritureComptableRepository ecritures, ExportComptablePort exportateur,
                             UniteDeTravail uniteDeTravail, Clock horloge) {
        this.ecritures = ecritures;
        this.exportateur = exportateur;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public byte[] executer(CentreId centre, LocalDate du, LocalDate au, CodeJournal journal) {
        return uniteDeTravail.executer(() -> {
            ExportComptable export = ExportComptable.depuis(ecritures.duJournal(centre, journal, du, au));
            byte[] fichier = exportateur.exporter(export.ecritures());
            for (EcritureComptable ecriture : export.marquerExportees(horloge.instant(), null)) {
                ecritures.enregistrer(ecriture);
            }
            return fichier;
        });
    }
}
