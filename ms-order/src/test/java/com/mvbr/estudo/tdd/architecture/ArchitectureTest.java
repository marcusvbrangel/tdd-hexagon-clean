package com.mvbr.estudo.tdd.architecture;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.mvbr.estudo.tdd", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainShouldBeIsolated = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..application..",
                    "..infrastructure..",
                    "org.springframework..",
                    "jakarta.persistence.."
            );

    @ArchTest
    static final ArchRule applicationShouldNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule controllersTalkToApplicationOrDomain = classes()
            .that().resideInAPackage("..infrastructure.adapter.in.web.controller..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "..infrastructure.adapter.in.web..",
                    "..application..",
                    "..domain..",
                    "java..",
                    "jakarta..",
                    "org.springframework.."
            );

    @ArchTest
    static final ArchRule portsDoNotKnowInfrastructure = noClasses()
            .that().resideInAPackage("..application.port..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule queriesDoNotUseDomain = noClasses()
            .that().resideInAPackage("..application.query..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule queriesDoNotUsePersistence = noClasses()
            .that().resideInAPackage("..application.query..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..persistence..",
                    "..adapter.out.persistence..",
                    "jakarta.persistence..",
                    "org.springframework.data.."
            );

    @ArchTest
    static final ArchRule useCasesDoNotDependOnQueries = noClasses()
            .that().resideInAPackage("..application.usecase..")
            .should().dependOnClassesThat()
            .resideInAPackage("..application.query..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule queryAdaptersStayOutOfDomain = noClasses()
            .that().resideInAPackage("..infrastructure.adapter.out.query..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule commandControllersSeparated = noClasses()
            .that().resideInAPackage("..infrastructure.adapter.in.web.controller..")
            .and().haveSimpleNameEndingWith("QueryController")
            .should().dependOnClassesThat()
            .resideInAPackage("..application.usecase..");

    @ArchTest
    static final ArchRule queryControllersSeparated = noClasses()
            .that().resideInAPackage("..infrastructure.adapter.in.web.controller..")
            .and().haveSimpleNameEndingWith("CommandController")
            .should().dependOnClassesThat()
            .resideInAPackage("..application.query..");
}
