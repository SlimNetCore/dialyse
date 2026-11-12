package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.SeancesDuPatient.TensionsDeSeance;
import com.hemodialyse.backend.contexts.dossiermedical.application.StatistiquesMedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.StatistiquesParamedicales;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les reponses JSON des statistiques d'un patient, MEMES cles, MEME ordre et MEMES types que le legacy ({@code PatientStatsQueryService}) : les
 * clients (ecran du patient) lisent ces formes. Les dates de seance du volet de tension sont des {@code java.sql.Date} (serialisees comme
 * celles du volet de poids, issues du SGBD).
 */
public final class StatistiquesReponses {

    private StatistiquesReponses() {
    }

    public static Map<String, Object> paramedicales(StatistiquesParamedicales s) {
        List<Map<String, Object>> tensions = new ArrayList<>();
        for (TensionsDeSeance t : s.evolutionDeLaTension()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("date_seance", t.date() == null ? null : java.sql.Date.valueOf(t.date()));
            ligne.put("ta_systolique_avant", t.avant().systolique());
            ligne.put("ta_diastolique_avant", t.avant().diastolique());
            ligne.put("ta_systolique_apres", t.apres().systolique());
            ligne.put("ta_diastolique_apres", t.apres().diastolique());
            tensions.add(ligne);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seanceCount", s.nombreDeSeances());
        out.put("avgPoidsAvantKg", s.moyennePoidsAvant());
        out.put("avgPoidsApresKg", s.moyennePoidsApres());
        out.put("avgUfReelleMl", s.moyenneUltrafiltration());
        out.put("poidsSecCibleKg", s.poidsSecCible());
        out.put("poidsEvolution", s.evolutionDuPoids());
        out.put("taEvolution", tensions);
        return out;
    }

    public static Map<String, Object> medicales(StatistiquesMedicales s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("avgHbGDl", s.hemoglobine());
        out.put("avgKtV", s.ktV());
        out.put("avgFerritineNgMl", s.ferritine());
        out.put("hbTrend", s.tendanceDesBilans());
        out.put("epoTrend", s.tendanceDesPrescriptionsDEpo());
        return out;
    }
}
