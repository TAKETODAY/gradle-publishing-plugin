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

import org.gradle.api.provider.Property;

import java.net.URI;
import java.time.Duration;

/**
 * Configures Maven Central publishing defaults for this project.
 *
 * @author <a href="https://github.com/TAKETODAY">海子 Yang</a>
 * @since 0.0.4
 */
public abstract class MavenCentralExtension {

  /**
   * Creates Maven Central publishing options.
   */
  public MavenCentralExtension() {
    getPublishingType().convention("AUTOMATIC");
    getWaitUntilFullyPublished().convention(false);
    getStatusRequestInterval().convention(Duration.ofSeconds(1));
    getMaxStatusRetries().convention(3);
    getBaseUrl().convention(URI.create("https://central.sonatype.com/"));
  }

  /**
   * Returns AUTOMATIC or USER_MANAGED (default: AUTOMATIC).
   * @return the publishing type
   */
  public abstract Property<String> getPublishingType();

  /**
   * Returns the Portal token username, defaulting to mavenCentralUsername.
   * @return the token username
   */
  public abstract Property<String> getUsername();

  /**
   * Returns the Portal token password, defaulting to mavenCentralPassword.
   * @return the token password
   */
  public abstract Property<String> getPassword();

  /**
   * Returns whether to wait for PUBLISHED instead of PUBLISHING (default: false).
   * Ignored when automatic publishing is disabled.
   *
   * @return the fully published waiting flag
   */
  public abstract Property<Boolean> getWaitUntilFullyPublished();

  /**
   * Returns the positive interval between status requests (default: one second).
   *
   * @return the status request interval
   */
  public abstract Property<Duration> getStatusRequestInterval();

  /**
   * Returns retries allowed after consecutive status failures (default: three).
   * Zero disables retries; a successful request resets the failure count.
   *
   * @return the nonnegative retry limit
   */
  public abstract Property<Integer> getMaxStatusRetries();

  /**
   * Returns the Publisher API base URL (default: https://central.sonatype.com/).
   *
   * @return the API base URL
   */
  public abstract Property<URI> getBaseUrl();

  /**
   * Returns the deployment name (default: project group:name:version).
   *
   * @return the deployment name
   */
  public abstract Property<String> getDeploymentName();
}
