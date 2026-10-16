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

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.plugins.signing.SigningPlugin;

import static infra.util.StringUtils.capitalize;
import static org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME;
import static org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP;

/**
 * Configures Maven Central publishing for a Gradle project.
 *
 * <p>The plugin creates a local staging repository, bundles its contents and
 * registers tasks for uploading the bundle to Maven Central.</p>
 *
 * @author <a href="https://github.com/TAKETODAY">海子 Yang</a>
 * @since 0.0.1
 */
public class PublishingPlugin implements Plugin<Project> {

  private static final Logger log = Logging.getLogger(PublishingPlugin.class);

  private static final String STAGING_REPOSITORY_NAME = "mavenCentralStaging";

  /**
   * Creates a Maven Central publishing plugin.
   */
  public PublishingPlugin() {
  }

  @Override
  public void apply(Project project) {
    log.debug("Applying cn.taketoday.publishing to project '{}'", project.getPath());
    project.getPluginManager().apply(SigningPlugin.class);
    project.getPluginManager().apply(MavenPublishPlugin.class);
    MavenCentralExtension extension = project.getExtensions()
            .create("mavenCentral", MavenCentralExtension.class);
    extension.getCredentials().convention(
            project.getProviders().credentials(PasswordCredentials.class, "mavenCentral"));
    extension.getDeploymentName().convention(
            project.provider(() -> project.getGroup() + ":" + project.getName() + ":" + project.getVersion()));
    PublishingExtension publishingExtension = project.getExtensions().getByType(PublishingExtension.class);
    Provider<Directory> outputDirectory = project.getLayout().getBuildDirectory().dir("publishing");
    Provider<Directory> stagingRepositoryDirectory = outputDirectory.map(it -> it.dir("staging-repository"));
    publishingExtension.getRepositories().maven(repository -> {
      repository.setName(STAGING_REPOSITORY_NAME);
      repository.setUrl(project.uri(stagingRepositoryDirectory));
    });
    log.info("Maven Central publishing configured for project '{}', staging at '{}'",
            project.getPath(), stagingRepositoryDirectory.get());

    TaskProvider<Delete> cleanStagingRepositoryTask = registerCleanStagingRepositoryTask(project, stagingRepositoryDirectory);
    extension.getBundle().getDestinationDirectory().convention(outputDirectory);
    extension.getBundle().getZip64().convention(false);
    TaskProvider<DefaultTask> bundleTask = registerBundleTask(project, stagingRepositoryDirectory, extension);
    TaskProvider<MavenCentralUploadTask> uploadBundleTask = registerUploadBundleTask(project, bundleTask, outputDirectory, extension);
    publishingExtension.getPublications().withType(MavenPublication.class).configureEach(publication -> {
      String publicationName = publication.getName();
      TaskProvider<?> publishToStagingRepositoryTask = getPublishToStagingRepositoryTask(project, publicationName);
      publishToStagingRepositoryTask.configure(task -> task.dependsOn(cleanStagingRepositoryTask));
      bundleTask.configure(task -> task.mustRunAfter(publishToStagingRepositoryTask));
      project.getTasks().named("mavenCentralZipBundle").configure(task -> task.mustRunAfter(publishToStagingRepositoryTask));
      project.getTasks().named("mavenCentralTarGzBundle").configure(task -> task.mustRunAfter(publishToStagingRepositoryTask));
      registerPublishTask(project, publicationName, publishToStagingRepositoryTask, uploadBundleTask);
    });
    TaskProvider<?> publishAllTask = registerPublishAllTask(project, uploadBundleTask);
    project.getTasks().named(PUBLISH_LIFECYCLE_TASK_NAME).configure(task -> task.dependsOn(publishAllTask));
    aggregateSubprojects(project, uploadBundleTask, stagingRepositoryDirectory);
    log.debug("Maven Central publishing tasks registered for project '{}'", project.getPath());
  }

  /**
   * Aggregates the staging publications of every subproject publishing to the
   * shared {@code mavenCentralStaging} repository into this project's bundle,
   * so a single {@code publishToMavenCentral} uploads the whole build.
   *
   * <p>Subprojects applying {@code maven-publish} without a dedicated
   * {@code mavenCentralStaging} repository are configured automatically.</p>
   */
  private void aggregateSubprojects(Project rootProject, TaskProvider<MavenCentralUploadTask> uploadBundleTask,
          Provider<Directory> stagingRepositoryDirectory) {
    if (rootProject.getSubprojects().isEmpty()) {
      return;
    }
    rootProject.getGradle().projectsEvaluated(gradle -> {
      String publishAllToStagingTaskName = "publishAllPublicationsTo" + capitalize(STAGING_REPOSITORY_NAME) + "Repository";
      TaskProvider<?> publishAllStagingTask = rootProject.getTasks().named(publishAllToStagingTaskName);
      int aggregated = 0;
      for (Project subproject : rootProject.getSubprojects()) {
        PublishingExtension publishingExtension = subproject.getExtensions().findByType(PublishingExtension.class);
        if (publishingExtension == null) {
          log.debug("Subproject '{}' does not apply 'maven-publish', skipped from aggregation",
                  subproject.getPath());
          continue;
        }
        if (publishingExtension.getRepositories().findByName(STAGING_REPOSITORY_NAME) == null) {
          publishingExtension.getRepositories().maven(repository -> {
            repository.setName(STAGING_REPOSITORY_NAME);
            repository.setUrl(subproject.uri(stagingRepositoryDirectory));
          });
          log.debug("Auto-configured '{}' repository for subproject '{}'",
                  STAGING_REPOSITORY_NAME, subproject.getPath());
        }
        Task stagingTask = subproject.getTasks().findByName(publishAllToStagingTaskName);
        if (stagingTask == null) {
          log.debug("Subproject '{}' has no '{}' task, skipped from aggregation",
                  subproject.getPath(), publishAllToStagingTaskName);
          continue;
        }
        publishAllStagingTask.configure(task -> task.dependsOn(stagingTask));
        uploadBundleTask.configure(task -> task.dependsOn(stagingTask));
        aggregated++;
        log.debug("Aggregated subproject '{}' publication '{}' into project '{}' publishing",
                subproject.getPath(), stagingTask.getPath(), rootProject.getPath());
      }
      log.info("Aggregated {} subproject publication(s) into '{}' Maven Central publishing",
              aggregated, rootProject.getPath());
    });
  }

  private TaskProvider<Delete> registerCleanStagingRepositoryTask(Project project, Provider<Directory> stagingRepositoryDirectory) {
    return project.getTasks().register("clean" + capitalize(STAGING_REPOSITORY_NAME) + "Repository", Delete.class, task -> {
      task.setGroup(PUBLISH_TASK_GROUP);
      task.setDescription("Cleans the local '" + STAGING_REPOSITORY_NAME + "' repository directory.");
      task.delete(stagingRepositoryDirectory);
    });
  }

  private TaskProvider<DefaultTask> registerBundleTask(Project project, Provider<Directory> stagingRepositoryDirectory, MavenCentralExtension extension) {
    TaskProvider<Zip> zipTask = registerArchiveTask(project, "mavenCentralZipBundle", Zip.class,
            stagingRepositoryDirectory, extension, BundleFormat.ZIP);
    TaskProvider<Tar> tarTask = registerArchiveTask(project, "mavenCentralTarGzBundle", Tar.class,
            stagingRepositoryDirectory, extension, BundleFormat.TAR_GZ);
    return project.getTasks().register("mavenCentralBundle", DefaultTask.class, task -> {
      task.setGroup(PUBLISH_TASK_GROUP);
      task.setDescription("Bundles the local '" + STAGING_REPOSITORY_NAME + "' repository content.");
      task.dependsOn(zipTask, tarTask);
    });
  }

  private <T extends AbstractArchiveTask> TaskProvider<T> registerArchiveTask(Project project, String name, Class<T> type,
          Provider<Directory> stagingRepositoryDirectory, MavenCentralExtension extension, BundleFormat format) {
    return project.getTasks().register(name, type, task -> {
      task.from(stagingRepositoryDirectory);
      task.exclude("**/maven-metadata.xml*");
      task.getDestinationDirectory().set(extension.getBundle().getDestinationDirectory());
      task.getArchiveFileName().set(extension.getBundle().getArchiveFileName());
      task.onlyIf(ignored -> extension.getBundle().getFormat().get() == format);
      if (task instanceof Tar tar) {
        tar.setCompression(Compression.GZIP);
      }
      else if (task instanceof Zip zip) {
        zip.doFirst(ignored -> zip.setZip64(extension.getBundle().getZip64().get()));
      }
    });
  }

  private TaskProvider<MavenCentralUploadTask> registerUploadBundleTask(Project project, TaskProvider<DefaultTask> bundleTask,
          Provider<Directory> outputDirectory, MavenCentralExtension extension) {
    TaskProvider<MavenCentralUploadTask> uploadTask = project.getTasks()
            .register("uploadMavenCentralBundle", MavenCentralUploadTask.class, extension);
    uploadTask.configure(task -> {
      task.setGroup(PUBLISH_TASK_GROUP);
      task.setDescription("Uploads, validates and publishes the '" + bundleTask.getName() + "' to Maven Central.");
      Provider<RegularFile> zipFile = project.getTasks().named("mavenCentralZipBundle", Zip.class).flatMap(Zip::getArchiveFile);
      Provider<RegularFile> tarFile = project.getTasks().named("mavenCentralTarGzBundle", Tar.class).flatMap(Tar::getArchiveFile);
      task.getBundleFile().set(extension.getBundle().getFormat()
              .flatMap(format -> format == BundleFormat.ZIP ? zipFile : tarFile));
      task.getDeploymentIdFile().set(outputDirectory.map(it -> it.file("deployment-id.txt")));
    });
    return uploadTask;
  }

  private TaskProvider<?> getPublishToStagingRepositoryTask(Project project, String publicationName) {
    return project.getTasks().named("publish" + capitalize(publicationName) + "PublicationTo" + capitalize(STAGING_REPOSITORY_NAME) + "Repository");
  }

  private TaskProvider<?> registerPublishTask(Project project, String publicationName,
          TaskProvider<?> publishToStagingRepositoryTask, TaskProvider<MavenCentralUploadTask> uploadBundleTask) {
    return project.getTasks().register("publish" + capitalize(publicationName) + "PublicationToMavenCentral", task -> {
      task.setGroup(PUBLISH_TASK_GROUP);
      task.setDescription("Publishes Maven publication '" + publicationName + "' to Maven Central.");
      task.dependsOn(publishToStagingRepositoryTask);
      task.dependsOn(uploadBundleTask);
    });
  }

  private TaskProvider<?> registerPublishAllTask(Project project, TaskProvider<MavenCentralUploadTask> uploadBundleTask) {
    return project.getTasks().register("publishToMavenCentral", task -> {
      task.setGroup(PUBLISH_TASK_GROUP);
      task.setDescription("Publishes all Maven publications produced by this project (and its subprojects) to Maven Central.");
      task.dependsOn(project.getTasks().named("publishAllPublicationsTo" + capitalize(STAGING_REPOSITORY_NAME) + "Repository"));
      task.dependsOn(uploadBundleTask);
    });
  }

}
