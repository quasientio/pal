package net.ittera.pal.dsl.jsonrpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.ittera.pal.common.objects.ObjectRef;

/**
 * RpcChainResult represents the final outcome after calling .send() on an RpcChain. It provides a
 * convenient API to access values by varName or inspect all responses.
 */
public class RpcChainResult {

  private final List<Map<String, Object>> chainValues;
  private final Map<String, Object> varNameToValueMap = new HashMap<>();
  private final Map<String, ObjectRef> varNameToRefMap = new HashMap<>();

  /**
   * Constructs an RpcChainResult from a list of response maps. Each map is expected to contain: -
   * "requestId": String - "varName": String or null - "value": Object (could be a primitive, an
   * ObjectRef, or something else) No other maps are taken as constructor parameters to avoid
   * redundancy and complexity.
   *
   * @param chainValues A list of response maps.
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
   * Returns all responses as provided by the chain. Each map contains "requestId", "varName",
   * "value", "ref" and "error"
   *
   * @return An unmodifiable list of response maps.
   */
  public List<Map<String, Object>> getAllValues() {
    return chainValues;
  }

  /**
   * Returns a list of all varNames for which values have been recorded.
   *
   * @return An unmodifiable list of varNames.
   */
  public List<String> getAllVarNames() {
    return new ArrayList<>(varNameToValueMap.keySet());
  }

  /**
   * Retrieves the value associated with a given varName. If no value is associated or varName
   * doesn't exist, returns null.
   *
   * @param varName The varName to look up.
   * @return The value associated with the varName, or null if not found.
   */
  public Object getValue(String varName) {
    return varNameToValueMap.get(varName);
  }

  public ObjectRef getRef(String varName) {
    return varNameToRefMap.get(varName);
  }

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
