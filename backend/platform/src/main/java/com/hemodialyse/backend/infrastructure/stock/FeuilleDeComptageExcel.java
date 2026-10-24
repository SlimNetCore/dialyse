package com.hemodialyse.backend.infrastructure.stock;

import com.hemodialyse.backend.contexts.stock.application.inventaire.FeuilleDeComptage;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.ComptageImporte;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.ResultatDImport;
import com.hemodialyse.backend.infrastructure.importer.FeuilleComptageExcel;
import org.springframework.stereotype.Component;

/**
 * Feuille de comptage Excel du contexte Stock, au-dessus du classeur historique ({@link FeuilleComptageExcel}) : même disposition,
 * mêmes refus de fichier. Les refus de fichier restent des règles métier de la plateforme (422).
 */
@Component
public class FeuilleDeComptageExcel implements FeuilleDeComptage {
    private final FeuilleComptageExcel classeur;

    public FeuilleDeComptageExcel(FeuilleComptageExcel classeur) {
        this.classeur = classeur;
    }

    @Override
    public byte[] ecrire(Inventaire inventaire, boolean aveugle) {
        return classeur.ecrire(InventaireVersLegacy.depuis(inventaire), aveugle);
    }

    @Override
    public Lecture lire(byte[] contenu, String referenceAttendue) {
        FeuilleComptageExcel.Lecture lecture = classeur.lire(contenu, referenceAttendue);
        return new Lecture(
                lecture.lignes().stream().map(l -> new ComptageImporte(l.ligneFichier(), l.ligneId(), l.articleCode(), l.numeroLot(),
                        l.quantite(), l.motif())).toList(),
                lecture.anomalies().stream().map(a -> new ResultatDImport.Anomalie(a.ligneFichier(), a.message())).toList());
    }
}

