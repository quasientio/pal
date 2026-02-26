# Changelog

## Unreleased

- [Feature] Add `pal replay` CLI command for deterministic WAL replay with simplified arguments and divergence reporting
- [Fix] Local dispatch path now correctly produces EXEC_THROWABLE messages when a method throws an exception, instead of incorrectly producing EXEC_RETURN_VALUE with a null value
