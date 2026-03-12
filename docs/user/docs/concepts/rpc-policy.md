# RPC Policy

PAL's RPC policy system controls which operations remote callers can invoke on a peer. It acts as an access control layer between incoming RPC messages and the dispatch engine, filtering by class, method, channel, and member type.

## Why RPC Policy Matters

When a peer exposes RPC endpoints (ZeroMQ or JSON-RPC WebSocket), any client that can reach the endpoint can invoke any accessible method. Without a policy:

- `System.exit()` can be called remotely, killing the peer
- `Runtime.exec()` can execute arbitrary OS commands
- `ProcessBuilder` can spawn processes
- `ClassLoader` can load arbitrary classes
- Internal PAL classes can be accessed

An RPC policy defines exactly what callers are allowed to do, following a **deny-by-default** security model.

## Quick Start

### Using Presets

The fastest way to secure a peer is with built-in presets:

```bash
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy-preset deny-unsafe,deny-jdk-internals \
  -cp app.jar com.example.Main
```

This blocks dangerous operations (System.exit, Runtime.exec, etc.) and JDK internal classes while allowing everything else.

### Using a YAML Policy File

For fine-grained control, write a policy file:

```yaml
# rpc-policy.yaml
version: 1
defaultAction: DENY

presets:
  deny-unsafe: true

rules:
  - class: "com.example.api.**"
    action: ALLOW

  - class: "com.example.Calculator"
    method: "add"
    action: ALLOW
```

```bash
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy rpc-policy.yaml \
  -cp app.jar com.example.Main
```

## YAML Policy Schema

### Top-Level Structure

```yaml
version: 1                    # Schema version (required)
defaultAction: DENY           # Action when no rule matches: ALLOW or DENY
presets:                       # Built-in deny-list presets
  deny-unsafe: true
rules:                         # Ordered list of rules (first match wins)
  - class: "com.example.**"
    action: ALLOW
```

### Rule Fields

| Field | Required | Type | Default | Description |
|-------|----------|------|---------|-------------|
| `class` | Yes (or `pattern`) | string | -- | Ant-style class pattern (e.g., `com.example.**`) |
| `method` | No | string | `**` (all members) | Method/field name pattern |
| `pattern` | No | string | -- | Combined `class.method` pattern (alternative to separate `class`/`method`) |
| `action` | Yes | string | -- | `ALLOW`, `DENY`, `LOG_AND_ALLOW`, `LOG_AND_DENY` |
| `channel` | No | string or list | all channels | `ZMQ_SOCKET_RPC`, `WEBSOCKET_RPC`, `LOG_RPC`, `CLI_RPC` |
| `members` | No | list | all types | `METHOD`, `STATIC_METHOD`, `CONSTRUCTOR`, `FIELD_GET`, `FIELD_SET` |
| `visibility` | No | string or list | all visibilities | Java member visibility levels to match (optional, defaults to all) |

### MemberVisibility Values

| Value | Description |
|-------|-------------|
| `PUBLIC` | Public members |
| `PROTECTED` | Protected members |
| `PACKAGE_PRIVATE` | Package-private (default) members |
| `DEFAULT` | Alias for `PACKAGE_PRIVATE` |
| `PRIVATE` | Private members |
| `ALL` | All visibility levels (no restriction) |

### Pattern Syntax

Patterns use Ant-style path matching with `.` as the separator:

| Pattern | Matches |
|---------|---------|
| `com.example.Calculator` | Exact class name |
| `com.example.*` | Classes directly in `com.example` package |
| `com.example.**` | Classes in `com.example` and all sub-packages |
| `com.example.Calculator.add` | Exact class and method (when using `pattern`) |

Matching is **case-insensitive**.

### Rule Format Options

You can specify class and member patterns separately or combined:

```yaml
rules:
  # Separate class/method (recommended)
  - class: "com.example.api.**"
    method: "**"
    action: ALLOW

  # Combined pattern (splits at last dot)
  - pattern: "com.example.Calculator.add"
    action: ALLOW
```

## Rule Evaluation

Rules are evaluated in **first-match-wins** order:

1. Rules are checked in the order they appear in the YAML file
2. The first rule whose pattern, channel, member, and visibility filters all match determines the action
3. If no rule matches, the `defaultAction` applies

```yaml
rules:
  # This rule is checked first
  - class: "com.example.Calculator"
    method: "divide"
    action: DENY

  # This rule is checked second (broader pattern)
  - class: "com.example.Calculator"
    method: "**"
    action: ALLOW
```

In this example, `Calculator.divide` is denied while all other `Calculator` methods are allowed.

## Built-In Presets

Presets are predefined deny rules for common threat categories. They are evaluated **after** user-defined rules, so your explicit ALLOW rules take precedence.

| Preset | What It Blocks |
|--------|----------------|
| `deny-unsafe` | `System.exit`, `System.setSecurityManager`, `Runtime.exec`, `Runtime.halt`, `Runtime.load`, `Runtime.loadLibrary`, `Runtime.addShutdownHook`, `ProcessBuilder.*`, `Process.*`, `Thread.stop`, `Thread.suspend`, `Thread.resume`, `ThreadGroup.destroy` |
| `deny-jdk-internals` | `com.sun.**`, `sun.**`, `jdk.**` |
| `deny-classloading` | `ClassLoader.*`, `URLClassLoader.*`, `Class.forName`, `Class.newInstance` |
| `deny-reflection` | `java.lang.reflect.**`, `java.lang.invoke.**` |
| `deny-serialization` | `java.io.ObjectInputStream.*` |
| `deny-scripting` | `javax.script.**` |
| `deny-nonpublic` | Denies RPC access to non-public members (protected, package-private, private) |
| `deny-pal-internals` | `io.quasient.pal.**` |

Enable presets via YAML or CLI:

```yaml
presets:
  deny-unsafe: true
  deny-jdk-internals: true
  deny-reflection: true
```

```bash
--rpc-policy-preset deny-unsafe,deny-jdk-internals,deny-reflection
```

## Channel-Scoped Rules

Rules can target specific RPC channels. This lets you apply different policies to different transport protocols:

```yaml
rules:
  # Allow admin operations only over binary RPC (internal network)
  - class: "com.example.admin.**"
    channel: ZMQ_SOCKET_RPC
    action: ALLOW

  # Deny admin operations over WebSocket (external clients)
  - class: "com.example.admin.**"
    channel: WEBSOCKET_RPC
    action: DENY

  # Allow public API on all channels
  - class: "com.example.api.**"
    action: ALLOW
```

Available channels:

| Channel | Description |
|---------|-------------|
| `ZMQ_SOCKET_RPC` | Binary RPC over ZeroMQ |
| `WEBSOCKET_RPC` | JSON-RPC over WebSocket |
| `LOG_RPC` | Messages arriving from a source log (Kafka/Chronicle) |
| `CLI_RPC` | Bootstrap calls from the CLI (main class invocation) |

**Note**: `REPLAY_INJECTION` is always exempt from policy checks. Replayed messages represent operations that already executed and are not external requests.

## Member Category Filtering

Rules can filter by the type of member being accessed:

```yaml
rules:
  # Allow only method calls on DTOs (no field writes)
  - class: "com.example.dto.**"
    members: [METHOD, CONSTRUCTOR]
    action: ALLOW

  # Allow reading config fields, but not writing them
  - class: "com.example.Config"
    members: [FIELD_GET]
    action: ALLOW
```

Available categories:

| Category | Description |
|----------|-------------|
| `METHOD` | Instance method invocation |
| `STATIC_METHOD` | Static method invocation |
| `CONSTRUCTOR` | Object construction |
| `FIELD_GET` | Field or static field read |
| `FIELD_SET` | Field or static field write |

## Visibility Filtering

Rules can filter by Java member visibility level, restricting which access modifiers are allowed for RPC calls:

```yaml
rules:
  # Allow only public members on API classes
  - class: "com.example.api.**"
    action: ALLOW
    visibility: PUBLIC

  # Allow both public and package-private members on a specific internal class
  - class: "com.example.internal.Helper"
    method: "internalMethod"
    action: ALLOW
    visibility: [PUBLIC, PACKAGE_PRIVATE]
```

When `visibility` is omitted, the rule matches all visibility levels (same as specifying `ALL`).

## Actions

| Action | Behavior |
|--------|----------|
| `ALLOW` | Allow the operation to proceed |
| `DENY` | Deny the operation; throws `RpcAccessDeniedException` |
| `LOG_AND_ALLOW` | Log the operation via SLF4J, then allow |
| `LOG_AND_DENY` | Log the operation via SLF4J, then deny |

The `LOG_AND_*` actions are useful for auditing or for testing a policy before enforcing it.

## Default Behavior

When **no policy is configured** (no `--rpc-policy`, no `--rpc-policy-preset`, no `--rpc-default-action`), all RPC operations are denied by default. To allow RPC calls, configure a policy with explicit ALLOW rules or pass `--rpc-default-action ALLOW`.

When a policy **is** configured, the `defaultAction` applies to any operation that doesn't match a rule. Use `DENY` (the default) for a secure allowlist model, `ALLOW` for a blocklist model.

## Metadata Filtering

The RPC policy also filters **class metadata** responses. When a remote client requests the class metadata for a peer (used for introspection and tooling), only classes and members that the policy allows are included. Clients never discover methods they cannot call.

This means:

- Classes with no accessible members are omitted entirely
- Methods, constructors, and fields blocked by the policy are excluded
- The metadata reflects exactly what is callable under the current policy

## Example Policies

### Development (Permissive)

Block dangerous operations but allow everything else:

```yaml
version: 1
defaultAction: ALLOW

presets:
  deny-unsafe: true
  deny-pal-internals: true
```

### Production (Restrictive Allowlist)

Only allow specific application packages:

```yaml
version: 1
defaultAction: DENY

presets:
  deny-unsafe: true
  deny-jdk-internals: true
  deny-pal-internals: true
  deny-classloading: true
  deny-reflection: true
  deny-serialization: true
  deny-scripting: true

rules:
  - class: "com.example.api.**"
    action: ALLOW

  - class: "com.example.service.**"
    channel: ZMQ_SOCKET_RPC
    action: ALLOW
```

### Testing (Full Access with Logging)

Allow everything but log all access for audit:

```yaml
version: 1
defaultAction: LOG_AND_ALLOW

presets:
  deny-unsafe: true

rules:
  - class: "com.example.**"
    action: ALLOW
```

## CLI Flags

| Flag | Description |
|------|-------------|
| `--rpc-policy <path>` | Path to RPC policy YAML file |
| `--rpc-policy-preset <names>` | Comma-separated preset names (e.g., `deny-unsafe,deny-jdk-internals`) |
| `--rpc-default-action <action>` | Default action when no rule matches: `ALLOW` or `DENY` (default: `DENY`) |

These flags can be combined. When both a YAML file and CLI presets are provided, user-defined rules from the YAML file have highest priority, followed by preset rules, followed by the default action.

## Security Considerations

### Object Reference Bypass

An attacker could try to GET a field that returns a dangerous object (e.g., a `Runtime` instance), then call methods on the returned object reference in subsequent RPC calls. The RPC policy prevents this because:

1. Getting the field requires one RPC call -- checked against the policy
2. Calling a method on the returned object requires another RPC call -- also checked
3. The `deny-unsafe` preset blocks **all** members on dangerous classes (`ProcessBuilder.**`, `Process.**`), including field access

### Visibility-Aware Policies

Non-public members (protected, package-private, private) are implementation details not intended for external access. For production deployments, use the `deny-nonpublic` preset to restrict RPC calls to public members only:

```bash
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy-preset deny-nonpublic,deny-unsafe \
  -cp app.jar com.example.Main
```

If specific non-public members must be accessible, add explicit ALLOW rules in your YAML policy. User-defined rules take precedence over presets:

```yaml
version: 1
defaultAction: DENY

presets:
  deny-nonpublic: true
  deny-unsafe: true

rules:
  # Allow public API
  - class: "com.example.api.**"
    action: ALLOW

  # Explicitly allow a package-private helper method
  - class: "com.example.internal.Helper"
    method: "bootstrap"
    action: ALLOW
    visibility: PACKAGE_PRIVATE
```

### Recommended Production Setup

For production peers exposed to untrusted networks:

```bash
pal run -d localhost:2379 --zmq-rpc auto \
  --rpc-policy rpc-policy-prod.yaml \
  -cp app.jar com.example.Main
```

With a restrictive YAML policy:

- `defaultAction: DENY` -- deny by default
- Enable all relevant presets (including `deny-nonpublic` to restrict access to public members only)
- Explicitly ALLOW only your application's public API packages
- Use channel-scoped rules if different transports have different trust levels
- Use `visibility` filters to expose only the intended access level per rule

## Further Reading

- [Remote Procedure Calls](rpc.md) -- RPC fundamentals
- [CLI Reference](../cli-reference.md) -- Complete `pal run` options
- [Interception](interception.md) -- Dynamic behavior modification (complementary to policy)
