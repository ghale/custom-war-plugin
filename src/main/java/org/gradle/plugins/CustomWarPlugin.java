package org.gradle.plugins;

import com.google.common.collect.Maps;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.War;
import org.gradle.plugins.ear.EarPluginConvention;

import java.util.Map;

public class CustomWarPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("war");
        War warTask = (War) project.getTasks().findByName("war");

        // Add a contextRoot extension property
        project.getExtensions().getExtraProperties().set("contextRoot", project.getName());

        // If the root project is an ear project, apply additional rules
        Project rootProject = project.getRootProject();
        rootProject.getPluginManager().withPlugin("ear", appliedPlugin -> {
            // Remove runtime dependencies from the WEB-INF/lib directory
            warTask.setClasspath(warTask.getClasspath().minus(project.getConfigurations().getByName("runtime")));

            // Add the runtime dependencies to the ear
            Map<String, String> projectMap = Maps.newHashMap();
            projectMap.put("path", project.getPath());
            projectMap.put("configuration", "runtime");
            rootProject.getDependencies().add("earlib", rootProject.getDependencies().project(projectMap));

            project.afterEvaluate(p -> {
                // Set the context root of the war module to the context root specified by "contextRoot"
                EarPluginConvention ear = (EarPluginConvention) rootProject.getConvention().getPlugins().get("ear");
                ear.deploymentDescriptor(deploymentDescriptor -> {
                    String contextRoot = (String) project.getExtensions().getExtraProperties().get("contextRoot");
                    deploymentDescriptor.webModule(warTask.getArchiveFileName().get(), contextRoot);
                });
            });
        });

    }
}
