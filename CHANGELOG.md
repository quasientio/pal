# Changelog

## Unreleased

- [Feature] Add `WalReader` utility for reading Chronicle WAL entries into `WalEntry`/`WalIndex` structures
- [Feature] Add `pal replay` CLI command for deterministic WAL replay with simplified arguments and divergence reporting
- [Test] Add integration tests for deterministic WAL replay (zero divergence, cross-divergence, HALT policy)
- [Fix] Local dispatch path now correctly produces EXEC_THROWABLE messages when a method throws an exception, instead of incorrectly producing EXEC_RETURN_VALUE with a null value
