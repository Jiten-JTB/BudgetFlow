package com.budgetflow.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.budgetflow.orchestrator", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("..orchestrator.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses().that().resideInAPackage("..orchestrator.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..orchestrator.adapter..", "..orchestrator.config..");
}
