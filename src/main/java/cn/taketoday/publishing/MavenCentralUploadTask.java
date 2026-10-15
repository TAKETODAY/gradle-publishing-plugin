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
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Bundle File upload to Maven Central Task
 *
 * @author <a href="https://github.com/TAKETODAY">海子 Yang</a>
 * @since 0.0.1
 */
@DisableCachingByDefault(because = "Uploading files")
public abstract class MavenCentralUploadTask extends DefaultTask {

  /**
   * Returns the ZIP bundle containing the Maven publications to upload.
   *
   * @return the Maven Central bundle file
   */
  @InputFile
  @PathSensitive(PathSensitivity.NAME_ONLY)
  public abstract RegularFileProperty getBundleFile();

  /**
   * Returns the name assigned to the Maven Central deployment.
   *
   * @return the deployment name
   */
  @Input
  public abstract Property<String> getDeploymentName();

  /**
   * Returns the credentials used to authenticate with Maven Central.
   *
   * @return the Maven Central credentials
   */
  @Internal
  public abstract Property<PasswordCredentials> getCredentials();

  /**
   * Returns the base URL of the Maven Central Publisher API.
   *
   * @return the Publisher API base URL
   */
  @Input
  public abstract Property<URI> getBaseUrl();

  /**
   * Returns AUTOMATIC or USER_MANAGED (default: AUTOMATIC).
   * @return the publishing type
   */
  @Input
  public abstract Property<String> getPublishingType();

  /**
   * Returns whether the task should wait until the deployment is fully published.
   *
   * @return {@code true} to wait for the {@code PUBLISHED} state
   */
  @Input
  public abstract Property<Boolean> getWaitUntilFullyPublished();

  /**
   * Returns the interval between deployment status requests.
   *
   * @return the status request interval
   */
  @Internal
  public abstract Property<Duration> getStatusRequestInterval();

  /**
   * Returns the number of retries allowed after consecutive status request failures.
   * A successful request resets the failure count. Defaults to three.
   *
   * @return the nonnegative retry limit; zero disables retries
   */
  @Internal
  public abstract Property<Integer> getMaxStatusRetries();

  /**
   * Returns the file in which the Maven Central deployment ID is written.
   *
   * @return the deployment ID output file
   */
  @OutputFile
  public abstract RegularFileProperty getDeploymentIdFile();

  /**
   * Creates an upload task with the Maven Central defaults.
   */
  public MavenCentralUploadTask() {
    getBaseUrl().convention(URI.create("https://central.sonatype.com/"));
    getPublishingType().convention("AUTOMATIC");
    getWaitUntilFullyPublished().convention(false);
    getStatusRequestInterval().convention(Duration.ofSeconds(1));
    getMaxStatusRetries().convention(3);
  }

  /**
   * Uploads the configured bundle and monitors the resulting deployment.
   *
   * @throws Exception if uploading fails or Maven Central reports a failed deployment
   */
  @TaskAction
  public void run() throws Exception {
    File bundleFile = getBundleFile().get().getAsFile();
    String deploymentName = getDeploymentName().get();
    PasswordCredentials credentials = getCredentials().get();
    String publishingType = getPublishingType().get();
    if (!publishingType.equals("AUTOMATIC") && !publishingType.equals("USER_MANAGED")) {
      throw new IllegalArgumentException("publishingType must be AUTOMATIC or USER_MANAGED");
    }
    boolean isPublish = publishingType.equals("AUTOMATIC");
    boolean waitUntilFullyPublished = getWaitUntilFullyPublished().get();
    Duration statusRequestInterval = getStatusRequestInterval().get();
    int maxStatusRetries = getMaxStatusRetries().get();
    if (maxStatusRetries < 0) {
      throw new IllegalArgumentException("maxStatusRetries must be nonnegative");
    }
    if (statusRequestInterval.isNegative() || statusRequestInterval.toMillis() == 0) {
      throw new IllegalArgumentException("statusRequestInterval must be at least one millisecond");
    }
    File deploymentIdFile = getDeploymentIdFile().get().getAsFile();

    var publisherApi = new MavenCentralPublisherAPI(getBaseUrl().get(), credentials);
    String deploymentId = publisherApi.upload(bundleFile, deploymentName, publishingType);
    Files.writeString(deploymentIdFile.toPath(), deploymentId);

    String message = "maven central deployment id: " + deploymentId;
    getLogger().quiet(message);
    Set<String> expectedStates = !isPublish ? Collections.singleton("VALIDATED") : !waitUntilFullyPublished ? Set.of("PUBLISHING", "PUBLISHED") : Collections.singleton("PUBLISHED");
    Set<String> unexpectedStates = !isPublish ? Set.of("PUBLISHING", "PUBLISHED", "FAILED")
            : Collections.singleton("FAILED");
    String previousState = null;
    int errorCount = 0;
    while (true) {
      JSONObject status;
      try {
        status = publisherApi.getStatus(deploymentId);
      }
      catch (IllegalStateException e) {
        if (++errorCount > maxStatusRetries) {
          throw e;
        }
        getLogger().lifecycle(message + ", state request failed for " + errorCount + " times, treating as temporary error: " + e.getMessage());
        Thread.sleep(statusRequestInterval.toMillis());
        continue;
      }
      errorCount = 0;
      String state = status.getString("deploymentState");
      String stateMessage = message + ", state: " + state;
      if (expectedStates.contains(state)) {
        getLogger().lifecycle(stateMessage);
        break;
      }
      if (unexpectedStates.contains(state)) {
        String failedMessage = stateMessage + " (expected " + String.join(" or ", expectedStates) + ")";
        getLogger().lifecycle(failedMessage);
        JSONObject errors = status.optJSONObject("errors");
        String errorMessage = errors == null ? failedMessage : failedMessage + ", errors:\n" + errors.toString(2);
        throw new IllegalStateException(errorMessage);
      }
      String waitingMessage = stateMessage + " (waiting for " + String.join(" or ", expectedStates) + ")";
      if (!state.equals(previousState)) {
        getLogger().lifecycle(waitingMessage);
      }
      else {
        getLogger().info(waitingMessage);
      }
      previousState = state;
      Thread.sleep(statusRequestInterval.toMillis());
    }
  }
}
