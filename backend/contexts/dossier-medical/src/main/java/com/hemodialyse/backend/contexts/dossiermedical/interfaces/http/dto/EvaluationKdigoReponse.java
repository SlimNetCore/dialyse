package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.EvaluerLeCandidatALaGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteSerologiqueKdigo;
import com.hemodialyse.backend.contexts.dossiermedical.domain.FonctionRenale;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RisqueImmunologique;

import java.math.BigDecimal;
import java.util.List;

/** Réponse de l'évaluation KDIGO du candidat : mêmes champs que {@code KdigoGreffeResponse} du legacy (énumérations par leur nom). */
public record EvaluationKdigoReponse(RisqueImmunologiqueReponse risqueImmunologique, FonctionRenaleReponse fonctionRenale,
                                     List<AlerteSerologiqueReponse> alertesSerologiques) {

    public record RisqueImmunologiqueReponse(String code, BigDecimal valeur, String unite, String niveau, String referenceKdigo) {
        static RisqueImmunologiqueReponse de(RisqueImmunologique r) {
            return new RisqueImmunologiqueReponse(r.code(), r.valeur(), r.unite(), r.niveau().name(), r.referenceKdigo());
        }
    }

    public record FonctionRenaleReponse(BigDecimal egfrMlMin173m2, String stade, String referenceKdigo) {
        static FonctionRenaleReponse de(FonctionRenale f) {
            return new FonctionRenaleReponse(f.egfrMlMin173m2(), f.stade().name(), f.referenceKdigo());
        }
    }

    public record AlerteSerologiqueReponse(String marqueur, String resultat, String message, String referenceKdigo) {
        static AlerteSerologiqueReponse de(AlerteSerologiqueKdigo a) {
            return new AlerteSerologiqueReponse(a.marqueur(), a.resultat(), a.message(), a.referenceKdigo());
        }
    }

    public static EvaluationKdigoReponse de(EvaluerLeCandidatALaGreffe.EvaluationDuCandidat e) {
        return new EvaluationKdigoReponse(RisqueImmunologiqueReponse.de(e.risqueImmunologique()), FonctionRenaleReponse.de(e.fonctionRenale()),
                e.alertesSerologiques().stream().map(AlerteSerologiqueReponse::de).toList());
    }
}
