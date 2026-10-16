/*
 * Copyright 2026 the TODAY authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.taketoday.publishing;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for subproject aggregation with the TestKit.
 */
class PublishingPluginIntegrationTests {

  @TempDir
  Path directory;

  @Test
  void aggregatesSubprojectStagingPublicationsIntoRootPublish() throws IOException {
    write("settings.gradle", """
        rootProject.name = 'aggregate'
        include 'library'
        include 'bom'
        """);
    write("build.gradle", """
        plugins {
          id 'cn.taketoday.publishing'
        }
        subprojects {
          apply plugin: 'java'
          apply plugin: 'maven-publish'
          publishing {
            repositories {
              maven {
                name = 'mavenCentralStaging'
                url = rootProject.layout.buildDirectory.dir('publishing/staging-repository')
              }
            }
            publications {
              maven(MavenPublication) {
                from components.java
              }
            }
          }
        }
        """);
    Files.createDirectories(directory.resolve("library"));
    Files.createDirectories(directory.resolve("bom"));;

    BuildResult result = run("publishToMavenCentral", "--dry-run");
    String output = result.getOutput();
    assertThat(output)
            .contains(":library:publishAllPublicationsToMavenCentralStagingRepository")
            .contains(":bom:publishAllPublicationsToMavenCentralStagingRepository")
            .contains(":publishAllPublicationsToMavenCentralStagingRepository")
            .contains(":uploadMavenCentralBundle");
  }

  @Test
  void skipsSubprojectsWithoutPublishingAndBundlesStagedArtifacts() throws IOException {
    write("settings.gradle", """
        rootProject.name = 'aggregate'
        include 'library'
        include 'plain'
        """);
    write("build.gradle", """
        plugins {
          id 'cn.taketoday.publishing'
        }
        project(':library') {
          apply plugin: 'java'
          apply plugin: 'maven-publish'
          group = 'com.example'
          version = '1.0.0'
          publishing {
            repositories {
              maven {
                name = 'mavenCentralStaging'
                url = rootProject.layout.buildDirectory.dir('publishing/staging-repository')
              }
            }
            publications {
              maven(MavenPublication) {
                from components.java
              }
            }
          }
        }
        project(':plain') {
          apply plugin: 'java'
        }
        """);
    Files.createDirectories(directory.resolve("library/src/main/java/example"));
    Files.createDirectories(directory.resolve("plain/src/main/java/example"));

    BuildResult result = run("publishAllPublicationsToMavenCentralStagingRepository", "mavenCentralZipBundle");

    Path stagingRepository = directory.resolve("build/publishing/staging-repository");
    assertThat(stagingRepository.resolve("com/example/library/1.0.0/library-1.0.0.jar")).exists();
    assertThat(stagingRepository.resolve("com/example/plain")).doesNotExist();
    assertThat(result.getOutput())
            .doesNotContain(":plain:publishAllPublicationsToMavenCentralStagingRepository");
    assertThat(directory.resolve("build/publishing/bundle.zip")).exists();
  }

  @Test
  void publishesSingleProjectWhenNoSubprojectsPublishToStaging() throws IOException {
    write("settings.gradle", """
        rootProject.name = 'standalone'
        """);
    write("build.gradle", """
        plugins {
          id 'cn.taketoday.publishing'
        }
        apply plugin: 'java'
        apply plugin: 'maven-publish'
        publishing {
          repositories {
            maven {
              name = 'mavenCentralStaging'
              url = layout.buildDirectory.dir('publishing/staging-repository')
            }
          }
          publications {
            maven(MavenPublication) {
              from components.java
            }
          }
        }
        """);

    BuildResult result = run("publishToMavenCentral", "--dry-run");
    assertThat(result.getOutput())
            .contains(":publishAllPublicationsToMavenCentralStagingRepository")
            .contains(":uploadMavenCentralBundle");
  }

  private BuildResult run(String... arguments) {
    return GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .build();
  }

  private void write(String name, String content) throws IOException {
    Files.writeString(directory.resolve(name), content);
  }

}
