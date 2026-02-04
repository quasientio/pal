# pal-runtime Coverage Report

**Date:** 2026-02-04
**Build:** `mvn clean install -DskipITs` with JaCoCo coverage

## Executive Summary

The pal-runtime module has achieved **79.00% instruction coverage** and **63.86% branch coverage**, falling short of the target goals of 85% instruction coverage and 80% branch coverage.

| Metric | Current | Target | Gap |
|--------|---------|--------|-----|
| Instruction Coverage | 79.00% (24,405 / 30,894) | 85% | -6.00% |
| Branch Coverage | 63.86% (2,179 / 3,412) | 80% | -16.14% |

## Coverage by Package

| Package | Instruction Coverage | Branch Coverage | Status |
|---------|---------------------|-----------------|--------|
| `io.quasient.pal.core.transport.zmq` | 100% | 100% | ✅ Exceeds targets |
| `io.quasient.pal.core.internal.concurrent` | 99% | 90% | ✅ Exceeds targets |
| `io.quasient.pal.core.internal.messages` | 94% | 81% | ✅ Exceeds targets |
| `io.quasient.pal.core.transport.gateway` | 92% | 79% | ⚠️ Branch below target |
| `io.quasient.pal.core.runtime.objects` | 92% | 83% | ✅ Exceeds targets |
| `io.quasient.pal.core.transport.chronicle` | 91% | 70% | ⚠️ Branch below target |
| `io.quasient.pal.core.execution.java.reflect` | 89% | 75% | ⚠️ Branch below target |
| `io.quasient.pal.core.dispatcher.thread` | 89% | 70% | ⚠️ Branch below target |
| `io.quasient.pal.core.transport.websocket` | 88% | 58% | ⚠️ Branch below target |
| `io.quasient.pal.core.transport` | 85% | 70% | ⚠️ Branch below target |
| `io.quasient.pal.core.runtime.session` | 82% | 67% | ❌ Both below target |
| `io.quasient.pal.core.intercept` | 82% | 66% | ❌ Both below target |
| `io.quasient.pal.core.transport.zmq.publish` | 79% | 43% | ❌ Both below target |
| `io.quasient.pal.core.dispatcher` | 78% | 64% | ❌ Both below target |
| `io.quasient.pal.core.annotations` | 72% | 77% | ❌ Instruction below target |
| `io.quasient.pal.core.transport.kafka` | 71% | 64% | ❌ Both below target |
| `io.quasient.pal.core.service` | 67% | 50% | ❌ Both below target |
| `io.quasient.pal.core.execution.java` | 61% | 50% | ❌ Both below target |

## Critical Coverage Gaps

### Highest Impact Classes (by missed instructions)

These classes have the most missed instructions and should be prioritized for coverage improvement:

| Class | Instruction Coverage | Missed Instructions |
|-------|---------------------|---------------------|
| `BaseExecMessageDispatcher` | 38.7% | 1,293 |
| `Main` | 55.4% | 1,060 |
| `InterceptCallbackDispatcher` | 61.9% | 448 |
| `SocketRpcInvoker` | 62.0% | 372 |
| `KafkaSourceLogReader` | 57.2% | 374 |
| `LogConfigurator` | 69.4% | 203 |
| `InterceptMatcher` | 69.0% | 159 |
| `MessagePublisher` | 75.8% | 113 |
| `AroundInterceptChain` | 77.8% | 111 |
| `InterceptActivationCoordinator` | 76.1% | 114 |

### Zero Coverage Classes

The following classes have 0% coverage and need attention:

| Class | Missed Instructions |
|-------|---------------------|
| `Main.new ServiceManager.Listener() {...}` (anonymous) | 35 |
| `AnnotationProcessor` | 4 |
| `Main.new TypeLiteral() {...}` (anonymous, 2 instances) | 12 |
| `AspectProxyDispatcher` | 49 (5.8%) |
| `BaseExecMessageDispatcher.IncomingInterceptMetadata` | 12 |

### Packages Requiring Most Attention

#### 1. `io.quasient.pal.core.execution.java` (61% instruction, 50% branch)
- **Key gaps:**
  - `BaseExecMessageDispatcher`: 38.7% (1,293 missed) - complex dispatch logic
  - `AspectProxyDispatcher`: 5.8% - AspectJ proxy handling
  - `InstanceMethodDispatcher`: 72.1% (43 missed)
  - `SetFieldDispatcher`: 78.6% (37 missed)

#### 2. `io.quasient.pal.core.service` (67% instruction, 50% branch)
- **Key gaps:**
  - `Main`: 55.4% (1,060 missed) - peer startup/shutdown paths
  - `SelfBootstrapInvoker`: 78.4% (77 missed)
  - `PeerWiring`: 90.3% (66 missed)
  - Anonymous listener classes: 0%

#### 3. `io.quasient.pal.core.intercept` (82% instruction, 66% branch)
- **Key gaps:**
  - `InterceptCallbackDispatcher`: 61.9% (448 missed) - remote callback handling
  - `InterceptMatcher`: 69.0% (159 missed) - pattern matching
  - `AroundInterceptChain`: 77.8% (111 missed)
  - `InterceptActivationCoordinator`: 76.1% (114 missed)

#### 4. `io.quasient.pal.core.transport.kafka` (71% instruction, 64% branch)
- **Key gaps:**
  - `KafkaSourceLogReader`: 57.2% (374 missed) - consumer logic
  - `LogConfigurator`: 69.4% (203 missed) - Kafka configuration
  - `KafkaWalWriter`: 88.7% (90 missed)

#### 5. `io.quasient.pal.core.dispatcher` (78% instruction, 64% branch)
- **Key gaps:**
  - `SocketRpcInvoker`: 62.0% (372 missed) - RPC handling
  - `MetaMessageDispatcher`: 81.2% (55 missed)
  - `ControlMessageDispatcher`: 75.6% (48 missed)

## Specific Method-Level Gaps

### `BaseExecMessageDispatcher` (1,293 missed instructions)
The largest coverage gap. Key uncovered areas include:
- Interception callback handling paths
- Error handling and exception wrapping
- Complex dispatch routing logic
- Message type-specific processing branches

### `Main` (1,060 missed instructions)
Peer lifecycle management gaps:
- `shutdown()` method paths
- Error handling in `fatalExit()` methods
- `closeZmqContext()` cleanup
- Environment variable parsing branches in `setEmptyParamsFromEnv()`
- Service manager listener callbacks

### `InterceptCallbackDispatcher` (448 missed instructions)
Remote intercept callback handling:
- AROUND callback consolidation
- Exception handling in callback dispatch
- Timeout and retry logic

### `SocketRpcInvoker` (372 missed instructions)
RPC request handling:
- Error response building
- Socket exception handling
- JSON-RPC request dispatching branches

### `KafkaSourceLogReader` (374 missed instructions)
Kafka consumer logic:
- Offset management
- Connection lifecycle
- Error handling on consume failures

## Recommendations

### Short-term (to reach 85% instruction coverage)
1. **Focus on `BaseExecMessageDispatcher`** - This single class accounts for ~20% of all missed instructions
2. **Add integration tests for `Main` lifecycle** - Covers startup/shutdown scenarios
3. **Expand `InterceptCallbackDispatcher` tests** - Remote callback paths

### Medium-term (to reach 80% branch coverage)
1. **Add error path tests** - Many branches are error handling that isn't exercised
2. **Test Kafka failure scenarios** - Consumer disconnects, rebalances
3. **Test interception edge cases** - Pattern matching, chain execution

### Test Patterns to Implement
1. **Service lifecycle tests** - For `Main`, `ConnectedService` subclasses
2. **Mock-based tests** - For external dependencies (Kafka, etcd)
3. **Error injection tests** - Simulate failures to cover exception paths

## Conclusion

The pal-runtime module requires approximately **1,900 additional covered instructions** to reach the 85% target and significant branch coverage improvements to reach 80%. The most impactful improvements would come from:

1. Testing `BaseExecMessageDispatcher` thoroughly (+1,293 potential instructions)
2. Testing `Main` lifecycle scenarios (+1,060 potential instructions)
3. Testing `InterceptCallbackDispatcher` remote paths (+448 potential instructions)

These three classes alone could potentially close the coverage gap if fully tested.
