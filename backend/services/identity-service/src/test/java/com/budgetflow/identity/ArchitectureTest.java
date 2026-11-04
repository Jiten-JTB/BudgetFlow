package com.budgetflow.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.budgetflow.identity", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("..identity.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses().that().resideInAPackage("..identity.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.adapter..", "..identity.config..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses().that().resideInAPackage("..identity.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..identity.application..", "..identity.adapter..", "..identity.config..");
}
