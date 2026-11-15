package com.hemodialyse.backend.contexts.dossiermedical.interfaces.publie;

import com.hemodialyse.backend.contexts.dossiermedical.application.LigneDeLOrdonnanceImprimee;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traducteur des lignes de l'ordonnance imprimée en champs du gabarit {@code ordonnance.jrxml} (DOC5d, ADR 0196) : mêmes noms (en
 * majuscules) et mêmes types que les champs déclarés par le gabarit ({@code java.sql.Date} pour les deux dates, entier pour la durée et la
 * quantité, texte sinon ; une valeur absente reste nulle).
 */
final class ChampsDeLOrdonnanceImprimee {

    private ChampsDeLOrdonnanceImprimee() {
    }

    static List<Map<String, Object>> champs(List<LigneDeLOrdonnanceImprimee> lignes) {
        return lignes.stream().map(ChampsDeLOrdonnanceImprimee::champs).toList();
    }

    static Map<String, Object> champs(LigneDeLOrdonnanceImprimee l) {
        Map<String, Object> c = new HashMap<>();
        c.put("NOM", l.nom());
        c.put("PRENOM", l.prenom());
        c.put("DATE_NAISSANCE", date(l.dateNaissance()));
        c.put("NUMERO_ASSURANCE", l.numeroAssurance());
        c.put("CENTER_NAME", l.nomDuCentre());
        c.put("NUMERO", l.numero());
        c.put("DATE_PRESCRIPTION", date(l.datePrescription()));
        c.put("MEDECIN_ID", l.medecin());
        c.put("MEDICAMENT_LIBELLE", l.medicament());
        c.put("POSOLOGIE", l.posologie());
        c.put("VOIE", l.voie());
        c.put("DUREE_JOURS", l.dureeJours());
        c.put("QUANTITE", l.quantite());
        c.put("INSTRUCTIONS", l.instructions());
        return c;
    }

    private static Date date(LocalDate jour) {
        return jour == null ? null : Date.valueOf(jour);
    }
}
