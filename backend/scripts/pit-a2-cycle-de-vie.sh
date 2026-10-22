#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur les ECRITURES legacy d'absences de patients (slice A2
# cycle de vie) : AbsencePatientService (declarer, qualifier, rattraper, annuler), agregat AbsencePatient, ValeurAbsence,
# ValorisationAbsenceService, AbsencePatientRestController (routes d'ecriture), AbsencePatientJdbcAdapter (save, findById,
# findByPatientAndDate, datesRattrapage), AbsenceDonneesJdbcAdapter (seanceRealisee, periodeFacturee, nomPatient),
# ForfaitAbsenceJdbcAdapter (forfait de la prise en charge, taux), ACL AbsencesDesPatientsLegacy ; testes par le filet HTTP
# de caracterisation A2 (contexte Spring : lent). Les methodes de detection / reconciliation de nuit (A3) et les lectures (A1)
# sont exclues de la cible : elles ne sont pas exercees par ce filet.
# Prerequis : tests compiles et classpath ecrit, SANS lancer deux builds Maven en parallele :
#   ./mvnw -o -q -pl platform -am test-compile dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-a2-cycle-de-vie.sh > pit-a2.log 2>&1 &
B=C:/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend
M=C:/Users/TS-CONSULT/.m2/repository
CPFILE=${CPFILE:-/tmp/abs-cp.txt}
DEPS=$(cat "$CPFILE" | tr -d '\r\n' | tr '\' '/')
CP="$B/platform/target/test-classes;$B/platform/target/classes;$DEPS;$M/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar"
CP=$(echo "$CP" | tr ';' ',')
PITCP="$M/org/pitest/pitest/1.30.0/pitest-1.30.0.jar;$M/org/pitest/pitest-entry/1.30.0/pitest-entry-1.30.0.jar;$M/org/pitest/pitest-command-line/1.30.0/pitest-command-line-1.30.0.jar;$M/org/pitest/pitest-html-report/1.30.0/pitest-html-report-1.30.0.jar;$M/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar;$M/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.jar;$M/org/apache/commons/commons-text/1.14.0/commons-text-1.14.0.jar;$M/org/apache/commons/commons-lang3/3.16.0/commons-lang3-3.16.0.jar"
P=com.hemodialyse.backend
cd "$B/platform"
java -cp "$PITCP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$B/platform/target/pit-reports-a2" \
  --targetClasses "$P.application.absence.AbsencePatientService*,$P.infrastructure.web.rest.AbsencePatientRestController*,$P.infrastructure.persistence.adapter.absence.AbsencePatientJdbcAdapter*,$P.infrastructure.persistence.adapter.absence.AbsenceDonneesJdbcAdapter*,$P.infrastructure.persistence.adapter.absence.ForfaitAbsenceJdbcAdapter*,$P.infrastructure.acl.facturation.AbsencesDesPatientsLegacy*,$P.domain.absence.model.AbsencePatient,$P.domain.absence.model.ValeurAbsence,$P.domain.absence.model.MotifAbsence,$P.domain.absence.model.StatutAbsence,$P.domain.absence.service.ValorisationAbsenceService" \
  --excludedMethods "detecter,reconcilier,controlerCentre,rattraperPeriode,rattraperDetection,centres,synthese,patientsAttendusSansSeance,centresActifs,colonneJour,findPaged,countAQualifier,countEnRetard,detectee,nomComplet" \
  --targetTests "$P.characterization.absencespatients.cyclevie.*" \
  --sourceDirs "$B/platform/src/main/java,$B/bc-seance/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 120000 --timeoutFactor 3 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
