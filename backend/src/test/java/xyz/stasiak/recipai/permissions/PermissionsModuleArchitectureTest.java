package xyz.stasiak.recipai.permissions;

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
class PermissionsModuleArchitectureTest {

    @ArchTest
    static final ArchRule permissionsModuleHasNoDomainKnowledge = noClasses()
            .that().resideInAPackage("..permissions..")
            .should().dependOnClassesThat(
                    resideInAPackage("xyz.stasiak.recipai..")
                            .and(not(resideInAPackage("..permissions..")))
            );

    private static final DescribedPredicate<JavaClass> IS_A_SHARED_PUBLIC_TYPE =
            simpleName("PermissionsFacade")
                    .or(simpleName("ResourceRole"))
                    .or(simpleName("PermissionDto"))
                    .or(simpleName("ShareRequest"))
                    .or(simpleName("UnshareRequest"))
                    .or(simpleName("PendingInviteDto"))
                    .or(simpleName("ResourceAccessDeniedException"))
                    .or(simpleName("InviteNotFoundException"))
                    .or(simpleName("InviteRefusedException"))
                    .or(simpleName("InvalidInviteRoleException"))
                    // InviteRefusedException's nested Reason enum is public for its reason() accessor;
                    // ArchUnit sees it as its own class in ..permissions.. with simpleName "Reason".
                    .or(simpleName("Reason"));

    @ArchTest
    static final ArchRule onlyTheFacadeAndSharedTypesArePublic = classes()
            .that().resideInAPackage("..permissions..")
            .and().arePublic()
            .should(be(IS_A_SHARED_PUBLIC_TYPE));

    @ArchTest
    static final ArchRule permissionServiceOwnsOnlyThePermissionRepository = noClasses()
            .that().haveSimpleName("PermissionService")
            .should().dependOnClassesThat(
                    simpleName("ResourceInviteRepository").or(simpleName("InviteService"))
            );

    @ArchTest
    static final ArchRule inviteServiceOwnsOnlyTheInviteRepository = noClasses()
            .that().haveSimpleName("InviteService")
            .should().dependOnClassesThat(
                    simpleName("ResourcePermissionRepository").or(simpleName("PermissionService"))
            );
}
