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

import org.gradle.api.Action;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.util.internal.ConfigureUtil;

import java.net.URI;
import java.time.Duration;

import javax.inject.Inject;

import groovy.lang.Closure;

/**
 * Configures Maven Central publishing defaults for this project.
 *
 * @author <a href="https://github.com/TAKETODAY">海子 Yang</a>
 * @since 0.0.4
 */
public abstract class MavenCentralExtension {

  private final BundleOptions bundle;

  /**
   * Creates Maven Central publishing options.
   *
   * @param objects the factory used to create configuration properties
   */
  @Inject
  public MavenCentralExtension(ObjectFactory objects) {
    this.bundle = new BundleOptions(objects);
    getPublishingType().convention(PublishingType.AUTOMATIC);
    getWaitUntilFullyPublished().convention(false);
    getStatusRequestInterval().convention(Duration.ofSeconds(1));
    getMaxStatusRetries().convention(3);
    getBaseUrl().convention(URI.create("https://central.sonatype.com/"));
    getBundle().getFormat().convention(BundleFormat.ZIP);
    getBundle().getArchiveFileName().convention(
            getBundle().getFormat().map(BundleFormat::getDefaultFileName));
    bundle.getDestinationDirectory().convention(objects.directoryProperty());
  }

  /**
   * Returns AUTOMATIC or USER_MANAGED (default: AUTOMATIC).
   *
   * @return the publishing type
   */
  public abstract Property<PublishingType> getPublishingType();

  /**
   * Returns the Portal token credentials, defaulting to the mavenCentral Gradle credentials.
   *
   * @return the token credentials
   */
  public abstract Property<PasswordCredentials> getCredentials();

  /**
   * Configures explicit Portal token credentials.
   *
   * @param action the credentials configuration
   */
  public void credentials(Action<? super PasswordCredentials> action) {
    PasswordCredentials credentials = getObjects().newInstance(PasswordCredentials.class);
    action.execute(credentials);
    getCredentials().set(credentials);
  }

  /**
   * Returns the factory used to create credentials.
   *
   * @return the object factory
   */
  @Inject
  protected abstract ObjectFactory getObjects();

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

  /**
   * Returns the Maven Central bundle options.
   *
   * @return bundle options
   */
  public BundleOptions getBundle() {
    return bundle;
  }

  /**
   * Configures the Maven Central bundle archive.
   *
   * @param action the bundle configuration
   */
  public void bundle(Action<? super BundleOptions> action) {
    action.execute(bundle);
  }

  /**
   * Configures the Maven Central bundle archive from a Groovy closure.
   *
   * @param closure the bundle configuration closure
   */
  public void bundle(Closure<?> closure) {
    ConfigureUtil.configure(closure, bundle);
  }

  /**
   * Configures the Maven Central bundle archive.
   */
  public static class BundleOptions {

    private final Property<BundleFormat> format;

    private final Property<String> archiveFileName;

    private final DirectoryProperty destinationDirectory;

    BundleOptions(ObjectFactory objects) {
      format = objects.property(BundleFormat.class);
      archiveFileName = objects.property(String.class);
      destinationDirectory = objects.directoryProperty();
    }

    /**
     * Returns the archive format.
     *
     * @return the archive format
     */
    public Property<BundleFormat> getFormat() {
      return format;
    }

    /**
     * Returns the archive file name.
     *
     * @return the archive file name
     */
    public Property<String> getArchiveFileName() {
      return archiveFileName;
    }

    /**
     * Returns the archive destination directory.
     *
     * @return the archive destination directory
     */
    public DirectoryProperty getDestinationDirectory() {
      return destinationDirectory;
    }
  }
}
