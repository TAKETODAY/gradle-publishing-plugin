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

import com.sun.net.httpserver.HttpServer;

import org.gradle.api.Project;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class MavenCentralConfigurationTests {

  @TempDir
  Path directory;

  @Test
  void extensionProvidesLazyDefaultsAndAllowsTaskOverrides() {
    Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
    project.getPluginManager().apply(PublishingPlugin.class);
    var extension = project.getExtensions().getByType(MavenCentralExtension.class);
    var task = (MavenCentralUploadTask) project.getTasks().getByName("uploadMavenCentralBundle");
    project.setGroup("example");
    project.setVersion("1.2.3");

    assertThat(task.getDeploymentName().get()).isEqualTo("example:" + project.getName() + ":1.2.3");
    assertThat(task.getPublishingType().get()).isEqualTo("AUTOMATIC");
    assertThat(task.getWaitUntilFullyPublished().get()).isFalse();
    assertThat(task.getStatusRequestInterval().get()).isEqualTo(Duration.ofSeconds(1));
    assertThat(task.getMaxStatusRetries().get()).isEqualTo(3);
    assertThat(task.getBaseUrl().get()).isEqualTo(URI.create("https://central.sonatype.com/"));

    extension.getUsername().set("custom-user");
    extension.getPassword().set("custom-password");
    assertThat(task.getCredentials().get().getUsername()).isEqualTo("custom-user");
    assertThat(task.getCredentials().get().getPassword()).isEqualTo("custom-password");
    extension.getPublishingType().set("USER_MANAGED");
    assertThat(task.getPublishingType().get()).isEqualTo("USER_MANAGED");
    extension.getWaitUntilFullyPublished().set(true);
    extension.getStatusRequestInterval().set(Duration.ofSeconds(5));
    extension.getMaxStatusRetries().set(7);
    extension.getBaseUrl().set(URI.create("https://example.test/"));
    extension.getDeploymentName().set("custom-deployment");

    assertThat(task.getPublishingType().get()).isEqualTo("USER_MANAGED");
    assertThat(task.getWaitUntilFullyPublished().get()).isTrue();
    assertThat(task.getStatusRequestInterval().get()).isEqualTo(Duration.ofSeconds(5));
    assertThat(task.getMaxStatusRetries().get()).isEqualTo(7);
    assertThat(task.getBaseUrl().get()).isEqualTo(URI.create("https://example.test/"));
    assertThat(task.getDeploymentName().get()).isEqualTo("custom-deployment");

    task.getMaxStatusRetries().set(1);
    extension.getMaxStatusRetries().set(9);
    assertThat(task.getMaxStatusRetries().get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 2 })
  void stopsAfterConfiguredConsecutiveFailures(int retries) throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = server(requests, false);
    try {
      MavenCentralUploadTask task = uploadTask(server);
      task.getMaxStatusRetries().set(retries);

      assertThatIllegalStateException().isThrownBy(task::run).withMessageContaining("503");
      assertThat(requests.get()).isEqualTo(retries + 1);
    }
    finally {
      server.stop(0);
    }
  }

  @Test
  void successfulStatusResetsRetryCountAndWaitsUntilPublished() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = server(requests, true);
    try {
      MavenCentralUploadTask task = uploadTask(server);
      task.getMaxStatusRetries().set(1);
      task.getWaitUntilFullyPublished().set(true);

      task.run();

      assertThat(requests.get()).isEqualTo(4);
      assertThat(Files.readString(task.getDeploymentIdFile().get().getAsFile().toPath())).isEqualTo("deployment-id");
    }
    finally {
      server.stop(0);
    }
  }

  @Test
  void invalidPollingOptionsFailBeforeUploading() throws Exception {
    MavenCentralUploadTask task = uploadTask(null);
    task.getMaxStatusRetries().set(-1);
    assertThatIllegalArgumentException().isThrownBy(task::run).withMessageContaining("maxStatusRetries");

    task.getMaxStatusRetries().set(0);
    task.getStatusRequestInterval().set(Duration.ZERO);
    assertThatIllegalArgumentException().isThrownBy(task::run).withMessageContaining("statusRequestInterval");
  }

  private MavenCentralUploadTask uploadTask(HttpServer server) throws Exception {
    Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
    var task = project.getTasks().register("upload", MavenCentralUploadTask.class).get();
    PasswordCredentials credentials = project.getObjects().newInstance(PasswordCredentials.class);
    credentials.setUsername("test-user");
    credentials.setPassword("test-password");
    task.getCredentials().set(credentials);
    task.getBundleFile().set(Files.write(directory.resolve("bundle.zip"), new byte[] { 1 }).toFile());
    task.getDeploymentName().set("test-deployment");
    task.getDeploymentIdFile().set(directory.resolve("deployment-id.txt").toFile());
    task.getStatusRequestInterval().set(Duration.ofMillis(1));
    if (server != null) {
      task.getBaseUrl().set(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }
    return task;
  }

  private HttpServer server(AtomicInteger requests, boolean recover) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/publisher/", exchange -> {
      exchange.getRequestBody().readAllBytes();
      boolean upload = exchange.getRequestURI().getPath().endsWith("/upload");
      int request = upload ? 0 : requests.incrementAndGet();
      int status = upload ? 201 : recover && request % 2 == 0 ? 200 : 503;
      String state = request == 4 ? "PUBLISHED" : "PUBLISHING";
      byte[] response = (upload ? "deployment-id" : "{\"deploymentState\":\"" + state + "\"}").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, response.length);
      try (var body = exchange.getResponseBody()) {
        body.write(response);
      }
    });
    server.start();
    return server;
  }
}
