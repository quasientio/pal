/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.cxn.directory;

import com.google.common.base.Splitter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs preflight health checks on etcd endpoints to ensure they are reachable before attempting
 * to create a jetcd client connection.
 *
 * <p>This class provides a workaround for jetcd's gRPC connection logic which can hang indefinitely
 * when connecting to unreachable servers, despite configured timeouts.
 */
public final class EtcdHealthCheck {

  /** Logger instance for EtcdHealthCheck. */
  private static final Logger logger = LoggerFactory.getLogger(EtcdHealthCheck.class);

  /** HTTP client for making health check requests. */
  private final HttpClient httpClient;

  /** Timeout for HTTP request/response operations. */
  private final Duration requestTimeout;

  /**
   * Creates a new EtcdHealthCheck with the specified timeouts.
   *
   * @param connectTimeout timeout for establishing HTTP connections
   * @param requestTimeout timeout for HTTP request/response
   */
  public EtcdHealthCheck(Duration connectTimeout, Duration requestTimeout) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    this.requestTimeout = requestTimeout;
  }

  /**
   * Checks if the given etcd endpoint is healthy by querying its health endpoint.
   *
   * <p>Tries both /v3/health and /health endpoints, looking for a 200 response with "health":"true"
   * in the body.
   *
   * @param baseUri the base URI of the etcd server (e.g., http://localhost:2379)
   * @return true if the endpoint is healthy, false otherwise
   */
  public boolean isHealthy(URI baseUri) {
    String[] healthPaths = {"/v3/health", "/health"};

    for (String path : healthPaths) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder(baseUri.resolve(path)).timeout(requestTimeout).GET().build();

        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 && response.body().contains("\"health\":\"true\"")) {
          if (logger.isDebugEnabled()) {
            logger.debug("etcd endpoint {} is healthy (via {})", baseUri, path);
          }
          return true;
        }
      } catch (IOException | InterruptedException e) {
        if (logger.isTraceEnabled()) {
          logger.trace("Health check failed for {} via {}: {}", baseUri, path, e.getMessage());
        }
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        // Try next path
      }
    }

    if (logger.isDebugEnabled()) {
      logger.debug("etcd endpoint {} is not healthy", baseUri);
    }
    return false;
  }

  /**
   * Checks if a TCP connection can be established to the given host and port.
   *
   * <p>This is a fallback check for etcd servers that may not expose HTTP health endpoints.
   *
   * @param host the hostname or IP address
   * @param port the port number
   * @param timeoutMs connection timeout in milliseconds
   * @return true if a TCP connection can be established, false otherwise
   */
  public static boolean canConnect(String host, int port, int timeoutMs) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeoutMs);
      if (logger.isDebugEnabled()) {
        logger.debug("TCP connection successful to {}:{}", host, port);
      }
      return true;
    } catch (IOException e) {
      if (logger.isTraceEnabled()) {
        logger.trace("TCP connection failed to {}:{}: {}", host, port, e.getMessage());
      }
      return false;
    }
  }

  /**
   * Validates that at least one of the given endpoints is reachable.
   *
   * <p>Starts with a fast TCP connectivity check and then tries HTTP health checks.
   *
   * @param endpoints list of etcd endpoint strings (e.g., "localhost:2379")
   * @param connectTimeoutMs timeout in milliseconds for connection attempts
   * @throws IllegalStateException if no endpoints are reachable
   */
  public static void assertReachable(List<String> endpoints, int connectTimeoutMs) {
    List<String> healthyEndpoints = new ArrayList<>();
    List<String> connectableEndpoints = new ArrayList<>();

    Duration timeout = Duration.ofMillis(connectTimeoutMs);
    EtcdHealthCheck healthCheck = new EtcdHealthCheck(timeout, timeout);

    for (String endpoint : endpoints) {
      // Try TCP connectivity check first (faster and more reliable)
      List<String> parts = Splitter.on(':').trimResults().omitEmptyStrings().splitToList(endpoint);
      if (parts.size() == 2) {
        try {
          String host = parts.get(0);
          int port = Integer.parseInt(parts.get(1));
          if (canConnect(host, port, connectTimeoutMs)) {
            connectableEndpoints.add(endpoint);
            // If TCP works, try HTTP health check for better validation
            try {
              URI uri = parseEndpoint(endpoint);
              if (healthCheck.isHealthy(uri)) {
                healthyEndpoints.add(endpoint);
              }
            } catch (IllegalArgumentException e) {
              if (logger.isTraceEnabled()) {
                logger.trace(
                    "TCP connected but HTTP health check failed for {}: {}",
                    endpoint,
                    e.getMessage());
              }
            }
          }
        } catch (NumberFormatException e) {
          logger.warn("Invalid port in endpoint: {}", endpoint);
        }
      }
    }

    if (!healthyEndpoints.isEmpty()) {
      logger.info(
          "Found {} healthy etcd endpoint(s): {}", healthyEndpoints.size(), healthyEndpoints);
      return;
    }

    if (!connectableEndpoints.isEmpty()) {
      logger.info(
          "Found {} connectable etcd endpoint(s) (health check unavailable): {}",
          connectableEndpoints.size(),
          connectableEndpoints);
      return;
    }

    throw new IllegalStateException(
        "No etcd endpoints reachable within " + connectTimeoutMs + " ms: " + endpoints);
  }

  /**
   * Parses an endpoint string into a URI, adding http:// scheme if not present.
   *
   * @param endpoint the endpoint string (e.g., "localhost:2379" or "http://localhost:2379")
   * @return the parsed URI
   */
  private static URI parseEndpoint(String endpoint) {
    if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
      endpoint = "http://" + endpoint;
    }
    return URI.create(endpoint);
  }
}
