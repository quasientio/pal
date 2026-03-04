# Changelog

## Unreleased

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
