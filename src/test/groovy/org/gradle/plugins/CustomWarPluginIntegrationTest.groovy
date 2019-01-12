package org.gradle.plugins

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CustomWarPluginIntegrationTest extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File settingsFile
    File buildFile
    BuildResult result

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "plugin adds war plugin"() {
        buildFile << """
            plugins {
                id 'org.gradle.custom-war'
            }
        """

        when:
        succeeds("war")

        then:
        result.task(':war').outcome == SUCCESS
    }

    def "plugin adds contextRoot property that defaults to project name"() {
        buildFile << """
            plugins {
                id 'org.gradle.custom-war'
            }
            
            assert contextRoot == project.name
        """

        expect:
        succeeds("help")
    }

    def "when project is not a part of an ear, libraries are put in WEB-INF/lib"() {
        testProjectDir.newFile("dependency.jar")
        buildFile << """
            plugins {
                id 'org.gradle.custom-war'
            }
            
            dependencies {
                compile files("dependency.jar")
            }
            
            task unzip(type: Copy) {
                dependsOn war
                from { zipTree(war.archivePath) }
                into "\${buildDir}/unzipped"
            }
        """

        when:
        succeeds("unzip")

        then:
        result.task(':war').outcome == SUCCESS

        and:
        assert file("build/unzipped/WEB-INF/lib/dependency.jar").exists()
    }

    def "when project is part of an ear, libraries are not put in WEB-INF/lib"() {
        testProjectDir.newFolder("war")
        testProjectDir.newFile("war/dependency.jar")
        def warBuildFile = testProjectDir.newFile("war/build.gradle")
        settingsFile << """
            include ':war'
        """
        buildFile << """
            plugins {
                id 'ear'
            }
            
            dependencies {
                deploy project(path: ':war', configuration: 'archives')
            }
            
            task unzip(type: Copy) {
                dependsOn ear
                from { zipTree(ear.archivePath) }
                into "\${buildDir}/unzipped"
            }
        """
        warBuildFile << """
            plugins {
                id 'org.gradle.custom-war'
            }
            
            dependencies {
                compile files("dependency.jar")
            }
            
            task unzip(type: Copy) {
                dependsOn war
                from { zipTree(war.archivePath) }
                into "\${buildDir}/unzipped"
            }
        """

        when:
        succeeds("unzip")

        then:
        result.task(':war:war').outcome == SUCCESS
        result.task(':ear').outcome == SUCCESS

        and:
        assert !file("war/build/unzipped/WEB-INF/lib/dependency.jar").exists()
        assert file("build/unzipped/lib/dependency.jar").exists()

        and:
        file("build/unzipped/META-INF/application.xml").text.contains("<web-uri>war.war</web-uri>")
        file("build/unzipped/META-INF/application.xml").text.contains("<context-root>war</context-root>")
    }

    def "when project is part of an ear, context root is reflected in application.xml"() {
        testProjectDir.newFolder("war")
        testProjectDir.newFile("war/dependency.jar")
        def warBuildFile = testProjectDir.newFile("war/build.gradle")
        settingsFile << """
            include ':war'
        """
        buildFile << """
            plugins {
                id 'ear'
            }
            
            dependencies {
                deploy project(path: ':war', configuration: 'archives')
            }
            
            task unzip(type: Copy) {
                dependsOn ear
                from { zipTree(ear.archivePath) }
                into "\${buildDir}/unzipped"
            }
        """
        warBuildFile << """
            plugins {
                id 'org.gradle.custom-war'
            }

            contextRoot = "foo"
            
            dependencies {
                compile files("dependency.jar")
            }
        """

        when:
        succeeds("unzip")

        then:
        result.task(':war:war').outcome == SUCCESS

        and:
        file("build/unzipped/META-INF/application.xml").text.contains("<web-uri>war.war</web-uri>")
        file("build/unzipped/META-INF/application.xml").text.contains("<context-root>foo</context-root>")
    }

    BuildResult succeeds(String... tasks) {
        List<String> args = ["--stacktrace"]
        args.addAll(tasks)
        result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(args)
                .withPluginClasspath()
                .withDebug(true)
                .build()
        println result.output
        return result
    }

    File file(String path) {
        return new File(testProjectDir.root, path)
    }
}
