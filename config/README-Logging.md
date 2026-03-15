# Logging configuration

This folder contains two example Logback configuration files for different Pal contexts:

- **`peer-logging.xml`** — Used by a Pal peer, i.e., when running `pal run`.  
  The path to this file must be specified in the environment variable **`PAL_PEER_LOGGING_CONFIG`**.  
  If this variable is not set, a [fallback configuration](../modules/pal-runtime/src/main/resources/peer-logging-fallback.xml) is used, which logs to **STDOUT**.

- **`cli-logging.xml`** — Used by `pal peer call`, `pal log print`, and other `pal` CLI subcommands.  
  The path to this file must be specified in the environment variable **`PAL_CLI_LOGGING_CONFIG`**.  
  If not set, a [fallback configuration](../modules/pal-cli/src/main/resources/cli-logging-fallback.xml) is used, which logs to the file **`$PWD/logs/cli.log`**.
