package com.quasient.pal.dsl.jsonrpc;

import com.quasient.pal.common.objects.ObjectRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of executing an RPC chain.
 *
 * <p>After invoking the {@code send()} method on an {@link RpcChain}, an instance of this class is
 * returned, encapsulating the outcomes of each RPC call within the chain.
 *
 * <p>Provides methods to access individual values by their variable names and to inspect all
 * responses collectively.
 */
public class RpcChainResult {

  /**
   * Unmodifiable list of response maps from the RPC chain execution. Each map contains details such
   * as "requestId", "varName", "value", "ref", and "error".
   */
  private final List<Map<String, Object>> chainValues;

  /**
   * Mapping from variable names to their corresponding values for quick lookup. If multiple entries
   * have the same varName, the last one is stored.
   */
  private final Map<String, Object> varNameToValueMap = new HashMap<>();

  /**
   * Mapping from variable names to their associated {@link ObjectRef} instances. Only entries with
   * a non-null {@code ObjectRef} are included.
   */
  private final Map<String, ObjectRef> varNameToRefMap = new HashMap<>();

  /**
   * Constructs an {@code RpcChainResult} from a list of response maps.
   *
   * <p>Each map in {@code chainValues} is expected to contain the following keys:
   *
   * <ul>
   *   <li>"requestId" : {@link String} - Identifier for the request.
   *   <li>"varName" : {@link String} or {@code null} - Variable name associated with the value.
   *   <li>"value" : {@link Object} - The value returned by the RPC call, which could be a
   *       primitive, an {@link ObjectRef}, or another object.
   *   <li>"ref" : {@link ObjectRef} - Reference to an object, if applicable.
   * </ul>
   *
   * <p>Only these specific keys are processed to populate the internal maps, ensuring simplicity
   * and avoiding redundancy.
   *
   * @param chainValues a list of response maps containing RPC call results
   */
  public RpcChainResult(List<Map<String, Object>> chainValues) {
    // Store the chainValues as an unmodifiable list for safety
    this.chainValues =
        chainValues == null ? Collections.emptyList() : Collections.unmodifiableList(chainValues);

    // Build varNameToValueMap from chainValues
    // If varName is present, store the value for quick lookup.
    // If multiple entries have the same varName, the last one wins.
    for (Map<String, Object> entry : this.chainValues) {
      String varName = (String) entry.get("varName");
      Object value = entry.get("value");
      if (varName != null && !varName.isEmpty()) {
        varNameToValueMap.put(varName, value);
      }
    }

    // same for object Ref's
    for (Map<String, Object> entry : this.chainValues) {
      String varName = (String) entry.get("varName");
      Object value = entry.get("ref");
      if (varName != null && !varName.isEmpty() && value instanceof ObjectRef) {
        varNameToRefMap.put(varName, (ObjectRef) value);
      }
    }
  }

  /**
   * Retrieves all response maps from the RPC chain execution.
   *
   * <p>Each map in the returned list contains keys such as "requestId", "varName", "value", "ref",
   * and "error".
   *
   * @return an unmodifiable list of response maps
   */
  public List<Map<String, Object>> getAllValues() {
    return chainValues;
  }

  /**
   * Retrieves all variable names that have recorded values in the RPC chain result.
   *
   * @return an unmodifiable list of variable names
   */
  public List<String> getAllVarNames() {
    return new ArrayList<>(varNameToValueMap.keySet());
  }

  /**
   * Retrieves the value associated with the specified variable name.
   *
   * <p>If no value is associated with the given {@code varName}, or if the {@code varName} does not
   * exist, this method returns {@code null}.
   *
   * @param varName the variable name to look up
   * @return the value associated with {@code varName}, or {@code null} if not found
   */
  public Object getValue(String varName) {
    return varNameToValueMap.get(varName);
  }

  /**
   * Retrieves the {@link ObjectRef} associated with the specified variable name.
   *
   * <p>If no {@code ObjectRef} is associated with the given {@code varName}, or if the {@code
   * varName} does not exist, this method returns {@code null}.
   *
   * @param varName the variable name to look up
   * @return the {@code ObjectRef} associated with {@code varName}, or {@code null} if not found
   */
  public ObjectRef getRef(String varName) {
    return varNameToRefMap.get(varName);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "RpcChainResult{"
        + "chainValues="
        + chainValues
        + ", varNameToValueMap="
        + varNameToValueMap
        + '}';
  }
}
