#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur les ECRITURES legacy de la slice P3 (roulement, absences,
# remplacements, « Mon planning ») : services AffectationInfirmierService, AbsenceInfirmierService, RemplacementInfirmierService,
# MonPlanningInfirmierService, InfirmierService (detail), contrôleurs InfirmierRestController, AbsenceInfirmierRestController,
# MonPlanningInfirmierRestController, PresenceInfirmierRestController (routes de remplacements seulement), adaptateurs JDBC des trois tables,
# agregats AbsenceInfirmier, AffectationInfirmier, RemplacementInfirmier ; testes par le filet HTTP de caracterisation P3 (contexte
# Spring : lent). Les lectures de presence (P4) sont exclues de la cible : elles ne sont exercees ici que comme effets.
# Prerequis : tests compiles et classpath ecrit, SANS lancer deux builds Maven en parallele :
#   ./mvnw -o -q -pl platform -am test-compile dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-p3-roulement.sh > pit-p3.log 2>&1 &
# Rapport : backend/platform/target/pit-reports-p3/
B=C:/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend
M=C:/Users/TS-CONSULT/.m2/repository
CPFILE=${CPFILE:-/tmp/p3-cp.txt}
DEPS=$(cat "$CPFILE" | tr -d '\r\n' | tr '\' '/')
CP="$B/platform/target/test-classes;$B/platform/target/classes;$DEPS;$M/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar"
CP=$(echo "$CP" | tr ';' ',')
PITCP="$M/org/pitest/pitest/1.30.0/pitest-1.30.0.jar;$M/org/pitest/pitest-entry/1.30.0/pitest-entry-1.30.0.jar;$M/org/pitest/pitest-command-line/1.30.0/pitest-command-line-1.30.0.jar;$M/org/pitest/pitest-html-report/1.30.0/pitest-html-report-1.30.0.jar;$M/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar;$M/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.jar;$M/org/apache/commons/commons-text/1.14.0/commons-text-1.14.0.jar;$M/org/apache/commons/commons-lang3/3.16.0/commons-lang3-3.16.0.jar"
P=com.hemodialyse.backend
cd "$B/platform"
java -cp "$PITCP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$B/platform/target/pit-reports-p3" \
  --targetClasses "$P.application.infirmier.AffectationInfirmierService*,$P.application.infirmier.AbsenceInfirmierService*,$P.application.infirmier.RemplacementInfirmierService*,$P.application.infirmier.MonPlanningInfirmierService*,$P.application.infirmier.InfirmierService*,$P.infrastructure.web.rest.InfirmierRestController*,$P.infrastructure.web.rest.AbsenceInfirmierRestController*,$P.infrastructure.web.rest.MonPlanningInfirmierRestController*,$P.infrastructure.web.rest.PresenceInfirmierRestController*,$P.infrastructure.persistence.adapter.infirmier.AffectationInfirmierJdbcAdapter*,$P.infrastructure.persistence.adapter.infirmier.AbsenceInfirmierJdbcAdapter*,$P.infrastructure.persistence.adapter.infirmier.RemplacementInfirmierJdbcAdapter*,$P.domain.infirmier.model.AbsenceInfirmier,$P.domain.infirmier.model.AffectationInfirmier,$P.domain.infirmier.model.RemplacementInfirmier,$P.domain.infirmier.model.TypeAbsence" \
  --excludedMethods "semaine,alertes,remplacants,charge,imprimer,parseMois,alertesSureffectif" \
  --targetTests "$P.characterization.planification.roulement.*" \
  --sourceDirs "$B/platform/src/main/java,$B/bc-seance/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 120000 --timeoutFactor 3 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
