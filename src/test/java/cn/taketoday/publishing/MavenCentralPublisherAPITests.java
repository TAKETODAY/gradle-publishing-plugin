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

import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class MavenCentralPublisherAPITests {

  @TempDir
  Path directory;

  @Test
  void statusAcceptsOkResponse() throws Exception {
    verifyStatusResponse(200);
  }

  @Test
  void statusRejectsUnauthorizedResponse() throws Exception {
    verifyStatusResponse(401);
  }

  private void verifyStatusResponse(int statusCode) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/publisher/status", exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] response = "{\"deploymentState\":\"PUBLISHED\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(statusCode, response.length);
      try (var body = exchange.getResponseBody()) {
        body.write(response);
      }
    });
    server.start();
    try {
      var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
      PasswordCredentials credentials = project.getObjects().newInstance(PasswordCredentials.class);
      credentials.setUsername("test-user");
      credentials.setPassword("test-password");
      var api = new MavenCentralPublisherAPI(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), credentials);
      if (statusCode == 200) {
        assertThat(api.getStatus("deployment-id").getString("deploymentState")).isEqualTo("PUBLISHED");
      }
      else {
        assertThatIllegalStateException().isThrownBy(() -> api.getStatus("deployment-id"))
                .withMessageContaining("unexpected response code " + statusCode);
      }
    }
    finally {
      server.stop(0);
    }
  }

  @Test
  void uploadAuthenticatesWithUsernameAndPassword() throws Exception {
    String token = Base64.getEncoder().encodeToString("test-user:test-password".getBytes(StandardCharsets.UTF_8));
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/publisher/upload", exchange -> {
      exchange.getRequestBody().readAllBytes();
      boolean authenticated = ("Bearer " + token).equals(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] response = (authenticated ? "deployment-id" : "Unauthorized").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(authenticated ? 201 : 401, response.length);
      try (var body = exchange.getResponseBody()) {
        body.write(response);
      }
    });
    server.start();
    try {
      var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
      PasswordCredentials credentials = project.getObjects().newInstance(PasswordCredentials.class);
      credentials.setUsername("test-user");
      credentials.setPassword("test-password");
      var api = new MavenCentralPublisherAPI(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), credentials);
      Path bundle = Files.write(directory.resolve("bundle.zip"), new byte[] { 1, 2, 3 });

      assertThat(api.upload(bundle.toFile(), "test-deployment", true)).isEqualTo("deployment-id");
    }
    finally {
      server.stop(0);
    }
  }
}
