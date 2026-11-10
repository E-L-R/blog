package io.github.elr.blog;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Checks the module boundaries without starting Spring: no dependency cycles, no module using
 * another's internal types, and only the dependencies each package-info.java allows.
 */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(BlogApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    // Writes PlantUML diagrams of the modules to target/spring-modulith-docs/.
    @Test
    void writesDocumentationSnippets() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
