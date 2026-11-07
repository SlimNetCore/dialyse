package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeSuiviDeLAnemie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EvaluationDeCible;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Le suivi de l'anémie, champ par champ comme le legacy. */
public record SuiviDeLAnemieReponse(LocalDate dateDernierBilan, List<Evaluation> evaluations, List<Point> courbe, PrescriptionReponse prescriptionActive,
                                    List<AdministrationReponse> administrationsRecentes) {

    public record Evaluation(String code, BigDecimal valeur, String unite, String statut, BigDecimal borneMin, BigDecimal borneMax,
                             String referenceKdigo) {
        static Evaluation de(EvaluationDeCible e) {
            return new Evaluation(e.code(), e.valeur(), e.unite(), e.statut(), e.borneMin(), e.borneMax(), e.referenceKdigo());
        }
    }

    public record Point(LocalDate date, BigDecimal hbGDl, BigDecimal ferritineNgMl, BigDecimal cstfPct, BigDecimal albumineGDl) {
        static Point de(BilanBiologique b) {
            return new Point(b.date(), b.hemoglobine(), b.ferritine(), b.cst(), b.albumine());
        }
    }

    public static SuiviDeLAnemieReponse de(ConsulterLeSuiviDeLAnemie.Suivi s) {
        return new SuiviDeLAnemieReponse(s.dateDernierBilan(), s.evaluations().stream().map(Evaluation::de).toList(),
                s.courbe().stream().map(Point::de).toList(), s.prescriptionActive().map(PrescriptionReponse::de).orElse(null),
                s.administrationsRecentes().stream().map(AdministrationReponse::de).toList());
    }
}
