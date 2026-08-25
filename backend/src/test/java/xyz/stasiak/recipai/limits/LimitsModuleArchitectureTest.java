package xyz.stasiak.recipai.limits;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.conditions.ArchConditions.be;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "xyz.stasiak.recipai", importOptions = ImportOption.DoNotIncludeTests.class)
class LimitsModuleArchitectureTest {

    @ArchTest
    static final ArchRule limitsModuleHasNoDomainKnowledge = noClasses()
            .that().resideInAPackage("..limits..")
            .should().dependOnClassesThat(
                    resideInAPackage("xyz.stasiak.recipai..")
                            .and(not(resideInAPackage("..limits..")))
            );

    private static final DescribedPredicate<JavaClass> IS_A_SHARED_PUBLIC_TYPE =
            simpleName("LimitsFacade")
                    .or(simpleName("LimitExceededException"))
                    .or(simpleName("LimitConfigurationMissingException"))
                    .or(simpleName("LimitKind"))
                    .or(simpleName("LimitQuota"))
                    .or(simpleName("LimitBalance"));

    @ArchTest
    static final ArchRule onlyTheFacadeAndSharedTypesArePublic = classes()
            .that().resideInAPackage("..limits..")
            .and().arePublic()
            .should(be(IS_A_SHARED_PUBLIC_TYPE));
}
