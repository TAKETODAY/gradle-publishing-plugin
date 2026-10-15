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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;
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

  private static final String STAGING_REPOSITORY_NAME = "mavenCentralStaging";

  /**
   * Creates a Maven Central publishing plugin.
   */
  public PublishingPlugin() {
  }

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(SigningPlugin.class);
    project.getPluginManager().apply(MavenPublishPlugin.class);
    MavenCentralExtension extension = project.getExtensions().create("mavenCentral", MavenCentralExtension.class);
    extension.getUsername().convention(project.getProviders().gradleProperty("mavenCentralUsername"));
    extension.getPassword().convention(project.getProviders().gradleProperty("mavenCentralPassword"));
    extension.getDeploymentName().convention(project.provider(() -> project.getGroup() + ":" + project.getName() + ":" + project.getVersion()));
    PublishingExtension publishingExtension = project.getExtensions().getByType(PublishingExtension.class);
    Provider<Directory> outputDirectory = project.getLayout().getBuildDirectory().dir("publishing");
    Provider<Directory> stagingRepositoryDirectory = outputDirectory.map(it -> it.dir("staging-repository"));
    publishingExtension.getRepositories().maven(repository -> {
      repository.setName(STAGING_REPOSITORY_NAME);
      repository.setUrl(project.uri(stagingRepositoryDirectory));
    });

    TaskProvider<Delete> cleanStagingRepositoryTask = registerCleanStagingRepositoryTask(project, stagingRepositoryDirectory);
    TaskProvider<Zip> bundleTask = registerBundleTask(project, stagingRepositoryDirectory, outputDirectory);
    TaskProvider<MavenCentralUploadTask> uploadBundleTask = registerUploadBundleTask(project, bundleTask, outputDirectory, extension);
    publishingExtension.getPublications().withType(MavenPublication.class).all(publication -> {
      String publicationName = publication.getName();
      TaskProvider<?> publishToStagingRepositoryTask = getPublishToStagingRepositoryTask(project, publicationName);
      publishToStagingRepositoryTask.configure(task -> task.dependsOn(cleanStagingRepositoryTask));
      bundleTask.configure(task -> task.mustRunAfter(publishToStagingRepositoryTask));
      registerPublishTask(project, publicationName, publishToStagingRepositoryTask, uploadBundleTask);
    });
    TaskProvider<?> publishAllTask = registerPublishAllTask(project, uploadBundleTask);
    project.getTasks().named(PUBLISH_LIFECYCLE_TASK_NAME).configure(task -> task.dependsOn(publishAllTask));
  }

  private TaskProvider<Delete> registerCleanStagingRepositoryTask(Project project, Provider<Directory> stagingRepositoryDirectory) {
    return project.getTasks().register("clean" + capitalize(STAGING_REPOSITORY_NAME) + "Repository", Delete.class, task -> {
      task.setGroup(PUBLISH_TASK_GROUP);
      task.setDescription("Cleans the local '" + STAGING_REPOSITORY_NAME + "' repository directory.");
      task.delete(stagingRepositoryDirectory);
    });
  }

  private TaskProvider<Zip> registerBundleTask(Project project, Provider<Directory> stagingRepositoryDirectory, Provider<Directory> outputDirectory) {
    return project.getTasks().register("mavenCentralBundle", Zip.class, task -> {
      task.setGroup(PUBLISH_TASK_GROUP);
      task.setDescription("Bundles the local '" + STAGING_REPOSITORY_NAME + "' repository content.");
      task.from(stagingRepositoryDirectory);
      task.exclude("**/maven-metadata.xml*");
      task.getDestinationDirectory().set(outputDirectory);
      task.getArchiveFileName().set("bundle.zip");
    });
  }

  private TaskProvider<MavenCentralUploadTask> registerUploadBundleTask(Project project, TaskProvider<Zip> bundleTask,
          Provider<Directory> outputDirectory, MavenCentralExtension extension) {
    return project.getTasks().register("uploadMavenCentralBundle", MavenCentralUploadTask.class, task -> {
      task.setGroup(PUBLISH_TASK_GROUP);
      task.setDescription("Uploads, validates and publishes the '" + bundleTask.getName() + "' to Maven Central.");
      task.getBundleFile().set(bundleTask.flatMap(Zip::getArchiveFile));
      task.getDeploymentName().convention(extension.getDeploymentName());
      task.getBaseUrl().convention(extension.getBaseUrl());
      task.getPublishingType().convention(extension.getPublishingType());
      task.getWaitUntilFullyPublished().convention(extension.getWaitUntilFullyPublished());
      task.getStatusRequestInterval().convention(extension.getStatusRequestInterval());
      task.getMaxStatusRetries().convention(extension.getMaxStatusRetries());
      var objects = project.getObjects();
      task.getCredentials().convention(extension.getUsername().zip(extension.getPassword(), (username, password) -> {
        PasswordCredentials credentials = objects.newInstance(PasswordCredentials.class);
        credentials.setUsername(username);
        credentials.setPassword(password);
        return credentials;
      }));
      task.getDeploymentIdFile().set(outputDirectory.map(it -> it.file("deployment-id.txt")));
    });
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
      task.setDescription("Publishes all Maven publications produced by this project to Maven Central.");
      task.dependsOn(project.getTasks().named("publishAllPublicationsTo" + capitalize(STAGING_REPOSITORY_NAME) + "Repository"));
      task.dependsOn(uploadBundleTask);
    });
  }

}
