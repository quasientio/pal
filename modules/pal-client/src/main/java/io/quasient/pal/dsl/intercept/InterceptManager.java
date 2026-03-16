/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.Interceptable;
import io.quasient.pal.cxn.directory.NoPeerInfoNodeException;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central orchestration class for managing intercept bundles.
 *
 * <p>This class bridges the high-level DSL model ({@link InterceptBundleSpec}) and the low-level
 * directory operations ({@link PalDirectory}). It provides operations to apply, remove, diff, and
 * query the status of intercept bundles.
 *
 * <p>All operations resolve peer names to UUIDs via {@link PalDirectory#getPeerByName(String)}.
 * Intercept UUIDs are generated deterministically from bundle content, enabling idempotent
 * apply/remove operations.
 *
 * <p><strong>Non-atomic operations:</strong> Apply and remove operations process intercepts
 * individually. If a failure occurs mid-operation, the result object reports partial
 * success/failure. Callers can re-run {@code apply()} safely because it is idempotent:
 * already-existing intercepts are skipped.
 *
 * @see InterceptBundleSpec
 * @see ApplyResult
 * @see RemoveResult
 * @see BundleStatus
 * @see InterceptDiff
 */
public class InterceptManager {

  /** Logger instance for InterceptManager operations. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptManager.class);

  /** The directory service used for all etcd operations. */
  private final PalDirectory directory;

  /**
   * Constructs a new {@code InterceptManager} backed by the given directory.
   *
   * @param directory the PAL directory for etcd operations; must not be {@code null}
   * @throws NullPointerException if {@code directory} is {@code null}
   */
  public InterceptManager(PalDirectory directory) {
    this.directory = Objects.requireNonNull(directory, "directory must not be null");
  }

  /**
   * Applies a bundle of intercepts: creates those that don't exist, skips those that do.
   *
   * <p>This operation is idempotent. Applying the same bundle twice produces the same deterministic
   * UUIDs; the second application will skip all already-existing intercepts.
   *
   * <p>After creating intercepts, stores {@link BundleMetadata} in the directory to support
   * bundle-level operations without the original YAML file.
   *
   * <p><strong>Non-atomic:</strong> Intercepts are created individually. If a failure occurs
   * mid-operation, the result reports partial success. Re-running {@code apply()} is safe.
   *
   * @param bundle the intercept bundle specification to apply
   * @return an {@link ApplyResult} with per-intercept details
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted
   * @throws NoPeerInfoNodeException if the target peer does not exist in the directory
   * @throws IllegalArgumentException if a referenced peer name cannot be resolved
   * @throws NullPointerException if {@code bundle} is {@code null}
   */
  public ApplyResult apply(InterceptBundleSpec bundle)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    Objects.requireNonNull(bundle, "bundle must not be null");

    List<ApplyResult.Entry> entries = new ArrayList<>();
    List<UUID> interceptUuids = new ArrayList<>();
    InterceptBundleDefaults defaults = bundle.getDefaults();
    UUID metadataPeerUuid = null;

    for (InterceptSpec spec : bundle.getIntercepts()) {
      UUID interceptUuid = generateInterceptUuid(bundle.getBundleName(), spec);
      interceptUuids.add(interceptUuid);

      String peerName = resolvePeerName(spec, defaults);
      PeerInfo peerInfo = resolvePeer(peerName);
      UUID peerUuid = peerInfo.getUuid();

      if (metadataPeerUuid == null) {
        metadataPeerUuid = peerUuid;
      }

      InterceptRequest<? extends Interceptable> request =
          spec.toInterceptRequest(interceptUuid, peerUuid, defaults);

      long ttlSeconds = resolveTtlSeconds(spec, defaults);

      try {
        directory.createIntercept(request, ttlSeconds);
        entries.add(new ApplyResult.Entry(spec, interceptUuid, ApplyResult.Status.CREATED, null));
        logger.info(
            "Created intercept {} for {}.{} (bundle \"{}\")",
            interceptUuid,
            spec.getTargetClass(),
            spec.getTargetName(),
            bundle.getBundleName());
      } catch (IllegalArgumentException e) {
        entries.add(new ApplyResult.Entry(spec, interceptUuid, ApplyResult.Status.SKIPPED, null));
        logger.info(
            "Skipped intercept {} for {}.{} (already exists)",
            interceptUuid,
            spec.getTargetClass(),
            spec.getTargetName());
      } catch (Exception e) {
        entries.add(
            new ApplyResult.Entry(spec, interceptUuid, ApplyResult.Status.FAILED, e.getMessage()));
        logger.error(
            "Failed to create intercept {} for {}.{}: {}",
            interceptUuid,
            spec.getTargetClass(),
            spec.getTargetName(),
            e.getMessage());
      }
    }

    // Store bundle metadata
    BundleMetadata metadata =
        new BundleMetadata(
            bundle.getBundleName(), metadataPeerUuid, interceptUuids, Instant.now(), 1);
    directory.createBundleMetadata(bundle.getBundleName(), metadata);

    return new ApplyResult(entries);
  }

  /**
   * Removes all intercepts defined in a bundle spec using deterministic UUIDs.
   *
   * <p>This operation is idempotent: removing non-existent intercepts is reported as NOT_FOUND
   * without throwing exceptions. Also deletes the bundle metadata from the directory.
   *
   * <p><strong>Non-atomic:</strong> Intercepts are deleted individually. Partial failures are
   * reported in the result.
   *
   * @param bundle the intercept bundle specification to remove
   * @return a {@link RemoveResult} with per-intercept details
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted
   * @throws IllegalArgumentException if a referenced peer name cannot be resolved
   * @throws NullPointerException if {@code bundle} is {@code null}
   */
  public RemoveResult remove(InterceptBundleSpec bundle)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(bundle, "bundle must not be null");

    InterceptBundleDefaults defaults = bundle.getDefaults();
    List<RemoveResult.Entry> entries = new ArrayList<>();

    for (InterceptSpec spec : bundle.getIntercepts()) {
      UUID interceptUuid = generateInterceptUuid(bundle.getBundleName(), spec);
      String peerName = resolvePeerName(spec, defaults);
      PeerInfo peerInfo = resolvePeer(peerName);
      UUID peerUuid = peerInfo.getUuid();

      Set<InterceptRequest<?>> existing = directory.listInterceptsForPeer(peerUuid);
      boolean found = existing.stream().anyMatch(r -> r.getUuid().equals(interceptUuid));

      if (found) {
        directory.deleteIntercept(peerUuid, interceptUuid);
        entries.add(new RemoveResult.Entry(interceptUuid, RemoveResult.Status.REMOVED));
        logger.info(
            "Removed intercept {} for {}.{} (bundle \"{}\")",
            interceptUuid,
            spec.getTargetClass(),
            spec.getTargetName(),
            bundle.getBundleName());
      } else {
        entries.add(new RemoveResult.Entry(interceptUuid, RemoveResult.Status.NOT_FOUND));
        logger.info("Intercept {} not found, skipping removal", interceptUuid);
      }
    }

    directory.deleteBundleMetadata(bundle.getBundleName());
    return new RemoveResult(entries);
  }

  /**
   * Removes all intercepts in a named bundle by reading metadata from the directory.
   *
   * <p>This operation does not require the original YAML file; it reads the stored {@link
   * BundleMetadata} to determine which intercepts to delete.
   *
   * @param bundleName the name of the bundle to remove
   * @return a {@link RemoveResult} with per-intercept details
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted
   * @throws IllegalArgumentException if no bundle metadata exists for the given name
   * @throws NullPointerException if {@code bundleName} is {@code null}
   */
  public RemoveResult removeByBundle(String bundleName)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(bundleName, "bundleName must not be null");

    BundleMetadata metadata = directory.getBundleMetadata(bundleName);
    if (metadata == null) {
      throw new IllegalArgumentException("No bundle metadata found for \"" + bundleName + "\"");
    }

    UUID peerUuid = metadata.getPeerUuid();
    Set<InterceptRequest<?>> existing = directory.listInterceptsForPeer(peerUuid);
    Set<UUID> existingUuids =
        existing.stream().map(InterceptRequest::getUuid).collect(Collectors.toSet());

    List<RemoveResult.Entry> entries = new ArrayList<>();
    for (UUID interceptUuid : metadata.getInterceptUuids()) {
      if (existingUuids.contains(interceptUuid)) {
        directory.deleteIntercept(peerUuid, interceptUuid);
        entries.add(new RemoveResult.Entry(interceptUuid, RemoveResult.Status.REMOVED));
        logger.info("Removed intercept {} from bundle \"{}\"", interceptUuid, bundleName);
      } else {
        entries.add(new RemoveResult.Entry(interceptUuid, RemoveResult.Status.NOT_FOUND));
        logger.info(
            "Intercept {} from bundle \"{}\" not found, skipping removal",
            interceptUuid,
            bundleName);
      }
    }

    directory.deleteBundleMetadata(bundleName);
    return new RemoveResult(entries);
  }

  /**
   * Removes a single intercept by peer UUID and intercept UUID.
   *
   * @param peerUuid the UUID of the peer
   * @param interceptUuid the UUID of the intercept to remove
   * @return a {@link RemoveResult} with one entry
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted
   * @throws NullPointerException if any parameter is {@code null}
   */
  public RemoveResult removeByUuid(UUID peerUuid, UUID interceptUuid)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(peerUuid, "peerUuid must not be null");
    Objects.requireNonNull(interceptUuid, "interceptUuid must not be null");

    directory.deleteIntercept(peerUuid, interceptUuid);
    logger.info("Removed intercept {} for peer {}", interceptUuid, peerUuid);

    List<RemoveResult.Entry> entries = new ArrayList<>();
    entries.add(new RemoveResult.Entry(interceptUuid, RemoveResult.Status.REMOVED));
    return new RemoveResult(entries);
  }

  /**
   * Computes a diff showing what would change if the bundle were applied.
   *
   * <p>For each intercept spec in the bundle, checks whether the corresponding intercept (by
   * deterministic UUID) exists in the directory and compares fields to detect modifications.
   *
   * @param bundle the intercept bundle specification to compare
   * @return a list of {@link InterceptDiff} entries
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted
   * @throws IllegalArgumentException if a referenced peer name cannot be resolved
   * @throws NullPointerException if {@code bundle} is {@code null}
   */
  public List<InterceptDiff> diff(InterceptBundleSpec bundle)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(bundle, "bundle must not be null");

    InterceptBundleDefaults defaults = bundle.getDefaults();
    String firstPeerName = resolvePeerName(bundle.getIntercepts().get(0), defaults);
    PeerInfo firstPeer = resolvePeer(firstPeerName);
    Set<InterceptRequest<?>> existing = directory.listInterceptsForPeer(firstPeer.getUuid());

    List<InterceptDiff> diffs = new ArrayList<>();
    for (InterceptSpec spec : bundle.getIntercepts()) {
      UUID interceptUuid = generateInterceptUuid(bundle.getBundleName(), spec);
      String peerName = resolvePeerName(spec, defaults);
      PeerInfo peerInfo = resolvePeer(peerName);

      InterceptRequest<? extends Interceptable> desired =
          spec.toInterceptRequest(interceptUuid, peerInfo.getUuid(), defaults);

      // Find matching existing intercept by UUID
      InterceptRequest<?> existingReq =
          existing.stream().filter(r -> r.getUuid().equals(interceptUuid)).findFirst().orElse(null);

      if (existingReq == null) {
        diffs.add(new InterceptDiff(spec, InterceptDiff.DiffType.CREATE, null));
      } else {
        String differences = computeDifferences(desired, existingReq);
        if (differences == null) {
          diffs.add(new InterceptDiff(spec, InterceptDiff.DiffType.UNCHANGED, null));
        } else {
          diffs.add(new InterceptDiff(spec, InterceptDiff.DiffType.MODIFIED, differences));
        }
      }
    }
    return diffs;
  }

  /**
   * Gets the status of a bundle's intercepts by checking which are active in the directory.
   *
   * @param bundle the intercept bundle specification to check
   * @return a {@link BundleStatus} with per-intercept active/inactive status
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted
   * @throws IllegalArgumentException if a referenced peer name cannot be resolved
   * @throws NullPointerException if {@code bundle} is {@code null}
   */
  public BundleStatus status(InterceptBundleSpec bundle)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(bundle, "bundle must not be null");

    InterceptBundleDefaults defaults = bundle.getDefaults();
    String peerName = resolvePeerName(bundle.getIntercepts().get(0), defaults);
    PeerInfo peerInfo = resolvePeer(peerName);
    Set<InterceptRequest<?>> existing = directory.listInterceptsForPeer(peerInfo.getUuid());
    Set<UUID> existingUuids =
        existing.stream().map(InterceptRequest::getUuid).collect(Collectors.toSet());

    List<BundleStatus.InterceptStatusEntry> entries = new ArrayList<>();
    for (InterceptSpec spec : bundle.getIntercepts()) {
      UUID interceptUuid = generateInterceptUuid(bundle.getBundleName(), spec);
      boolean active = existingUuids.contains(interceptUuid);
      entries.add(new BundleStatus.InterceptStatusEntry(spec, interceptUuid, active, null));
    }

    return new BundleStatus(bundle.getBundleName(), peerName, peerInfo.getUuid(), entries);
  }

  /**
   * Gets the status of a named bundle by reading metadata from the directory.
   *
   * <p>This operation does not require the original YAML file.
   *
   * @param bundleName the name of the bundle to check
   * @return a {@link BundleStatus} with per-UUID active/inactive status
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted
   * @throws IllegalArgumentException if no bundle metadata exists for the given name
   * @throws NullPointerException if {@code bundleName} is {@code null}
   */
  public BundleStatus statusByBundle(String bundleName)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(bundleName, "bundleName must not be null");

    BundleMetadata metadata = directory.getBundleMetadata(bundleName);
    if (metadata == null) {
      throw new IllegalArgumentException("No bundle metadata found for \"" + bundleName + "\"");
    }

    UUID peerUuid = metadata.getPeerUuid();
    Set<InterceptRequest<?>> existing = directory.listInterceptsForPeer(peerUuid);
    Set<UUID> existingUuids =
        existing.stream().map(InterceptRequest::getUuid).collect(Collectors.toSet());

    List<BundleStatus.InterceptStatusEntry> statusEntries = new ArrayList<>();
    for (UUID interceptUuid : metadata.getInterceptUuids()) {
      boolean active = existingUuids.contains(interceptUuid);
      // Without the original spec, we create a minimal placeholder spec
      InterceptSpec placeholder =
          InterceptSpec.builder()
              .targetClass("unknown")
              .targetName("unknown")
              .type(InterceptType.BEFORE)
              .callbackClass("unknown")
              .callbackMethod("unknown")
              .build();
      statusEntries.add(
          new BundleStatus.InterceptStatusEntry(placeholder, interceptUuid, active, null));
    }

    return new BundleStatus(bundleName, null, peerUuid, statusEntries);
  }

  /**
   * Generates a deterministic UUID for an intercept based on bundle name and spec content.
   *
   * <p>The UUID is derived from a seed string composed of the bundle name, target class, target
   * name, intercept type, callback class, callback method, and parameter types. This ensures that
   * the same bundle definition always produces the same UUIDs, enabling idempotent operations.
   *
   * <p>This method is package-private for testability.
   *
   * @param bundleName the name of the bundle
   * @param spec the intercept specification
   * @return a deterministic UUID
   */
  static UUID generateInterceptUuid(String bundleName, InterceptSpec spec) {
    String seed =
        bundleName
            + "|"
            + spec.getTargetClass()
            + "|"
            + spec.getTargetName()
            + "|"
            + spec.getType()
            + "|"
            + spec.getCallbackClass()
            + "|"
            + spec.getCallbackMethod()
            + "|"
            + String.join(",", spec.getParameterTypes());
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Resolves the effective peer name for an intercept spec, falling back to bundle defaults.
   *
   * @param spec the intercept spec (may have a peer override)
   * @param defaults the bundle-level defaults
   * @return the effective peer name
   * @throws IllegalArgumentException if no peer is specified in either the spec or defaults
   */
  private static String resolvePeerName(InterceptSpec spec, InterceptBundleDefaults defaults) {
    String peer = spec.getPeerOverride();
    if (peer == null) {
      peer = defaults.getPeer();
    }
    if (peer == null) {
      throw new IllegalArgumentException(
          "No peer specified for intercept "
              + spec.getTargetClass()
              + "."
              + spec.getTargetName()
              + " and no default peer in bundle");
    }
    return peer;
  }

  /**
   * Resolves a peer name to a {@link PeerInfo} via the directory.
   *
   * @param peerName the peer name or UUID string to resolve
   * @return the resolved peer info
   * @throws ExecutionException if an error occurs during etcd operation
   * @throws InterruptedException if the current thread is interrupted
   * @throws IllegalArgumentException if the peer cannot be found
   */
  private PeerInfo resolvePeer(String peerName) throws ExecutionException, InterruptedException {
    PeerInfo peerInfo = directory.getPeerByName(peerName);
    if (peerInfo == null) {
      throw new IllegalArgumentException("Peer not found: \"" + peerName + "\"");
    }
    return peerInfo;
  }

  /**
   * Resolves the effective TTL in seconds for an intercept spec, falling back to bundle defaults.
   *
   * @param spec the intercept spec (may have a TTL override)
   * @param defaults the bundle-level defaults
   * @return the TTL in seconds; 0 if no TTL is specified
   */
  private static long resolveTtlSeconds(InterceptSpec spec, InterceptBundleDefaults defaults) {
    if (spec.getTtlOverride() != null) {
      return spec.getTtlOverride().toSeconds();
    }
    if (defaults.getTtl() != null) {
      return defaults.getTtl().toSeconds();
    }
    return 0;
  }

  /**
   * Compares two intercept requests and returns a description of differences.
   *
   * @param desired the desired state from the bundle spec
   * @param existing the existing state in the directory
   * @return a description of differences, or {@code null} if they match
   */
  private static String computeDifferences(
      InterceptRequest<?> desired, InterceptRequest<?> existing) {
    List<String> diffs = new ArrayList<>();

    if (desired.getPriority() != existing.getPriority()) {
      diffs.add("priority: " + existing.getPriority() + " → " + desired.getPriority());
    }
    if (desired.isForceImmediate() != existing.isForceImmediate()) {
      diffs.add(
          "forceImmediate: " + existing.isForceImmediate() + " → " + desired.isForceImmediate());
    }
    if (!Objects.equals(
        desired.getExceptionPropagationPolicy(), existing.getExceptionPropagationPolicy())) {
      diffs.add(
          "exceptionPolicy: "
              + existing.getExceptionPropagationPolicy()
              + " → "
              + desired.getExceptionPropagationPolicy());
    }
    if (!Objects.equals(
        desired.getCheckedExceptionPolicy(), existing.getCheckedExceptionPolicy())) {
      diffs.add(
          "checkedExceptionPolicy: "
              + existing.getCheckedExceptionPolicy()
              + " → "
              + desired.getCheckedExceptionPolicy());
    }
    if (desired.getTtlSeconds() != existing.getTtlSeconds()) {
      diffs.add("ttlSeconds: " + existing.getTtlSeconds() + " → " + desired.getTtlSeconds());
    }

    if (diffs.isEmpty()) {
      return null;
    }
    return String.join("; ", diffs);
  }
}
