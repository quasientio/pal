# Changelog

## Unreleased

- [Fix] Make entry-point span-skip conditional on injector presence in dispatchReplay — only skip entire span when the entry point's thread has a ReplayInputInjector; otherwise skip only the OPERATION entry, leaving nested operations available for cursor matching. Reinstates COMPLETION skip loop for operation-only skip path.
- [Doc] Add --no-wal-incoming-cli documentation to deterministic replay user guide: explain purpose, relationship with --wal-incoming-rpc, and when to use it
- [Doc] Add user guide, developer architecture doc, and CLI reference for RPC policy system
- [Feature] Add RpcPolicyAction and MemberCategory enums for the RPC policy system
- [Feature] Add STUB_FROM_WAL dispatch path in BaseExecMessageDispatcher — supports return value reconstruction, phantom cascading, exception replay, span skipping, and STUB_FROM_WAL_VERIFIED mode
- [Feature] Add SideEffectAnalyzer for detecting unsafe stubs during replay — warns when stubbing would silently drop PUT_FIELD/PUT_STATIC mutations visible outside the span
- [Feature] Add configurable replay policy system with ReplayPolicyRule (Ant-style pattern matching), BuiltInStubRules (--shield-io), ReplayPolicyParser (YAML + CLI options), and extended ReplayPolicy with first-match-wins rule evaluation
- [Enhancement] Replace alibaba fastjson with Jackson for InfoNode/LogInfo/PeerInfo JSON serialization
- [Feature] Add `pal ls -I` command to list registered intercepts in the PAL directory
- [Doc] Update user and developer documentation for multi-threaded replay (Phase 2): add multi-threaded replay section to user guide, document ReplayInputInjector/ReplayGate/entry-point markers in developer guide, add --replay-threading to CLI reference, mark Phase 2 complete in addendum spec
- [Feature] Add RpcCalculator test application for multi-threaded RPC replay integration tests
- [Doc] Update WalIndexMinimalReceiptCalculatorIT Javadoc to reflect symmetric BEFORE/AFTER WAL gating
- [Fix] SelfBootstrapInvoker offset wait now gated by WITH_WAL_INCOMING_CLI to prevent blocking when AFTER message is not written to WAL
- [Fix] Reduce AntPathMatcherThreadSafetyTest iterations to prevent intermittent timeouts on loaded CI
- [Feature] Add `WalReader` utility for reading Chronicle WAL entries into `WalEntry`/`WalIndex` structures
- [Feature] Add `pal replay` CLI command for deterministic WAL replay with simplified arguments and divergence reporting
- [Test] Add integration tests for deterministic WAL replay (zero divergence, cross-divergence, HALT policy)
- [Fix] Local dispatch path now correctly produces EXEC_THROWABLE messages when a method throws an exception, instead of incorrectly producing EXEC_RETURN_VALUE with a null value
