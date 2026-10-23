#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur la DETECTION legacy des absences de patients (slice A3
# detection de nuit) : AbsencePatientScheduler (cron 02:30, rattrapage au demarrage, notification), AbsencePatientService
# (detecter, reconcilier, controlerCentre, rattraperPeriode, synthese, centres), AbsencePatientRestController.rattraperDetection,
# AbsenceDonneesJdbcAdapter (requete « patient attendu », seanceRealisee, periodeFacturee, centresActifs),
# AbsencePatientJdbcAdapter (findPaged, countAQualifier, countEnRetard, save, saveIfAQualifier, findByPatientAndDate),
# AbsencePatient (detectee, annuler), ValorisationAbsenceService ; testes par le filet HTTP / scheduler de la slice A3
# (contexte Spring : lent). Les ecritures du cycle de vie (A2) et les lectures (A1) sont hors cible (autres filets).
# Prerequis : tests compiles et classpath ecrit, SANS lancer deux builds Maven en parallele :
#   ./mvnw -o -q -pl platform -am test-compile dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-a3-detection.sh > pit-a3.log 2>&1 &
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
  --reportDir "$B/platform/target/pit-reports-a3" \
  --targetClasses "$P.infrastructure.scheduling.AbsencePatientScheduler*,$P.application.absence.AbsencePatientService*,$P.infrastructure.web.rest.AbsencePatientRestController*,$P.infrastructure.persistence.adapter.absence.AbsenceDonneesJdbcAdapter*,$P.infrastructure.persistence.adapter.absence.AbsencePatientJdbcAdapter*,$P.domain.absence.model.AbsencePatient,$P.domain.absence.service.ValorisationAbsenceService" \
  --excludedMethods "nomPatient,nomComplet,mapRow,toString,hashCode,equals" \
  --targetTests "$P.characterization.absencespatients.detection.*" \
  --sourceDirs "$B/platform/src/main/java,$B/bc-seance/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 120000 --timeoutFactor 3 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
