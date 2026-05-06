# Distributed Application Guide

This guide picks up from [Getting Started](../getting-started.md) and shows what changes when your peers move from a single local process to a multi-peer setup connected through etcd and Kafka. It covers service discovery, multi-consumer logging, scaling, and the operational commands you'll use day-to-day.

If you haven't run `pal init` yet, do that first. This guide assumes a project scaffolded with Kafka enabled (`pal init --all`, or answer **y** to the *"Will you use Kafka for WAL?"* prompt), which generates an `infra/` directory with both etcd and Kafka.

## Why Distributed Mode

Local Chronicle workflows are fine for a single peer. Distributed mode adds:

- **Service discovery** via etcd — peers find each other by name, regardless of host or port.
- **Networked, remotely hosted logs** via Kafka — the broker can run on a separate cluster from your peers, so logs are reachable from any machine on the network. Chronicle queues are memory-mapped files on a single host; multi-reader access is local-only.
- **Kafka ecosystem integration** — once a log is a Kafka topic, the broader ecosystem opens up: Kafka Streams and ksqlDB for stream processing, Kafka Connect for sinking to Elasticsearch / S3 / databases / data warehouses, and any client that speaks the Kafka protocol.
- **Scale-out** — multiple instances of the same service run side by side, each registered in the directory.

When a peer publishes its address through etcd and writes its WAL to a Kafka topic, every other peer can discover it, talk to it, and observe what it does — all without the running peer being modified or restarted.

```
  ┌────────────┐
  │   Client   │
  └─────┬──────┘
        │ resolves "calculator" via etcd
        ▼
  ┌────────────────────────────────────────────┐
  │               etcd directory               │
  │  peers · logs · intercept registrations    │
  └──┬─────────────────────────────────────────┘
     │ advertises calculator's WebSocket address
     ▼
  ┌────────────┐                      ┌──────────────┐
  │ Calculator │  ──── WAL writes ──▶ │ Kafka topic  │
  │    peer    │                      │  "calc-wal"  │
  └────────────┘                      └──────────────┘
```

## Start Infrastructure

```bash
infra/start.sh
```

This brings up etcd at `localhost:2379` and Kafka at `localhost:29092` via docker-compose. Verify:

```bash
curl http://localhost:2379/health
docker ps | grep -E "etcd|kafka"
```

Run `infra/stop.sh` to tear them down when you're done.

## Run a Service Peer

```bash
pal run -d localhost:2379 -k localhost:29092 \
  --wal calc-wal \
  --json-rpc auto \
  --interceptable \
  -n calculator \
  -cp build/classes/java/main \
  com.example.calculator.CalculatorService
```

What each flag does:

| Flag | Effect |
|------|--------|
| `-d localhost:2379` | Register this peer in the etcd directory; pick up logs and intercepts from it |
| `-k localhost:29092` | Use Kafka as the log backend |
| `--wal calc-wal` | Write the WAL to Kafka topic `calc-wal` |
| `--json-rpc auto` | Listen for JSON-RPC over WebSocket on a free port; advertise it via the directory |
| `--interceptable` | Pick up intercept registrations targeting this peer (also enables in-flight tracking) |
| `-n calculator` | Register under this name in the directory; clients can address by name |

Use `--zmq-rpc auto` instead of (or alongside) `--json-rpc auto` if you want binary RPC; both can run on the same peer.

## Discover Peers

The directory holds peers, logs, and intercept registrations:

```bash
pal peer ls -d localhost:2379 -l
pal log ls -d localhost:2379 -l
pal intercept ls -d localhost:2379
```

`-l` (long) on the first two adds detail: addresses, uptime, log offsets. A peer name like `calculator` is a stable lookup target — clients reference it by name regardless of the actual host/port.

Peer names are unique within a directory; registering a second peer with the same name fails with `DuplicatePeerNameException`. To run multiple instances of the same service, give each a distinct name (see [Scale Out](#scale-out) below).

## Call the Service

### From the CLI (JSON-RPC stdin)

For typed arguments, pipe a JSON-RPC request on stdin:

```bash
echo '{"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.example.calculator.CalculatorService","method":"add","args":[{"type":"int","value":10},{"type":"int","value":20}]}}' \
  | pal peer call -d localhost:2379 calculator
```

The directory resolves `calculator` to the peer's WebSocket address; the request goes over JSON-RPC; the response prints to stdout.

The positional-argument form (`pal peer call ... ClassName arg1 arg2`) only works for methods with a `static void main(String[])` signature — it always passes `String[]` and routes to `main`. For typed signatures, use the JSON-RPC stdin form. See [JSON-RPC Reference](../concepts/rpc-json.md) for the full request shape and [CLI Reference — Invocation Modes](../cli-reference.md#invocation-modes) for both forms.

### From Java (RpcChain DSL)

For multi-step Java clients, the `pal-client` module's `RpcChain` DSL handles ObjectRef tracking automatically. The pattern below looks the calculator up by name, then constructs a remote instance and calls two methods on it:

```java
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.dsl.jsonrpc.RpcChain;
import io.quasient.pal.dsl.jsonrpc.RpcChainResult;
import io.quasient.pal.messages.types.RpcType;
import java.util.UUID;
import static io.quasient.pal.dsl.jsonrpc.RpcChain.args;

PalDirectory directory = new PalDirectory("localhost:2379");
PeerInfo calcPeer = directory.getPeerByName("calculator");

ThinPeer thinPeer = new ThinPeer()
    .withUuid(UUID.randomUUID())
    .withInitialPeer(calcPeer)
    .withOutboundRpcType(RpcType.JSON_RPC)
    .init();

try {
    RpcChain chain = new RpcChain(thinPeer);
    chain
        .create("com.example.calculator.CalculatorService", "calc")
        .call("add", "sum", args(15, 25))
        .call("multiply", "product", args(6, 7))
        .send();

    RpcChainResult result = chain.getChainResult();
    System.out.println("15 + 25 = " + result.getValue("sum"));
    System.out.println("6 * 7 = " + result.getValue("product"));
} finally {
    thinPeer.close();
    directory.close();
}
```

For the full DSL — lookup variants, nested calls, thread affinity, error handling — see [RpcChain DSL](../concepts/rpc-chain.md).

## Inspect the WAL

Operations on the calculator are appended to its Kafka WAL:

```bash
pal log print -d localhost:2379 calc-wal --tree
```

Multiple consumers can read the same Kafka topic concurrently — other PAL peers replaying via `--source-log`, Kafka Streams jobs, ksqlDB, or anything else that speaks Kafka. This is what distinguishes a Kafka WAL from a single-process Chronicle queue.

Follow the topic live:

```bash
pal log print -d localhost:2379 calc-wal -f --tree
```

## Scale Out

Multiple instances of the same service register under distinct names:

```bash
# Terminal 1
pal run -d localhost:2379 -k localhost:29092 \
  --wal calc-wal-1 --json-rpc auto -n calculator-1 \
  -cp build/classes/java/main com.example.calculator.CalculatorService

# Terminal 2
pal run -d localhost:2379 -k localhost:29092 \
  --wal calc-wal-2 --json-rpc auto -n calculator-2 \
  -cp build/classes/java/main com.example.calculator.CalculatorService
```

Each instance has its own Kafka WAL; clients pick which to address:

```bash
pal peer call -d localhost:2379 calculator-1 ...
pal peer call -d localhost:2379 calculator-2 ...
```

Load balancing across instances is the caller's responsibility — PAL gives you the directory but doesn't dispatch on your behalf.

## Monitoring Across Peers

To observe operations on a running peer without modifying its code, use interception. The end-to-end walkthrough is in [Getting Started → Interception](../getting-started.md#interception-dynamic-behavior-modification): scaffold a callback peer, declare an intercept bundle, apply it with `pal intercept apply`. Once applied, every matching operation on the target peer triggers a callback on the monitoring peer — live, no restart needed.

For the runtime semantics (BEFORE/AFTER/AROUND, in-flight tracking, error propagation), see [Interception](../concepts/interception.md).

## Cleanup

```bash
# Remove peers from the directory (--force also removes still-running ones)
pal peer rm -d localhost:2379 calculator --force

# Remove logs from the directory (does not delete the Kafka topic itself)
pal log rm -d localhost:2379 calc-wal

# Tear down infrastructure
infra/stop.sh
```

To purge a Kafka topic's data, use Kafka's own tooling (`kafka-topics --delete`).

## Further Reading

- [Getting Started](../getting-started.md) — installation, `pal init`, first peer, distributed-mode tutorial
- [Peers and Logs](../concepts/peers-and-logs.md) — peer lifecycle, log roles, directory model
- [Remote Procedure Calls](../concepts/rpc.md) — RPC overview and CLI usage
- [JSON-RPC Reference](../concepts/rpc-json.md) — wire-format details and `JsonRpcMessageFactory`
- [RpcChain DSL](../concepts/rpc-chain.md) — fluent Java API for multi-step JSON-RPC
- [Log Backends](../concepts/logs.md) — Chronicle vs Kafka deep dive
- [Interception](../concepts/interception.md) — runtime semantics
- [CLI Reference](../cli-reference.md) — full command and flag reference
