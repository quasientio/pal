@REM
@REM Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM     http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@echo off
setlocal enabledelayedexpansion

rem Resolve PAL_HOME from script location (no env var required)
pushd %~dp0..
set PAL_HOME=%CD%
popd

rem Determine the command (first non-option argument)
set COMMAND=
for %%a in (%*) do (
  set ARG=%%a
  if "!ARG:~0,1!" neq "-" (
    if not defined COMMAND set COMMAND=%%a
  )
)

rem ---------------------------------------------------------------------------
rem Chronicle Queue module exports (required for all commands)
rem ---------------------------------------------------------------------------
set CHRONICLE_EXPORTS=--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED ^
  --add-exports=java.base/sun.nio.ch=ALL-UNNAMED ^
  --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED ^
  --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED ^
  --add-opens=jdk.compiler/com.sun.tools.javac=ALL-UNNAMED ^
  --add-opens=java.base/java.lang=ALL-UNNAMED ^
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED ^
  --add-opens=java.base/java.io=ALL-UNNAMED ^
  --add-opens=java.base/java.util=ALL-UNNAMED ^
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED

rem ---------------------------------------------------------------------------
rem Category environment variables for 'run' command (Kafka-style)
rem
rem Each variable has a sensible default.  Setting the variable REPLACES
rem the default entirely, giving full control over that category.
rem
rem   PAL_HEAP_OPTS  - Heap sizing          (default: -Xmx1g)
rem   PAL_GC_OPTS    - GC tuning            (default: G1GC, 200ms pause target)
rem   PAL_JMX_OPTS   - JMX configuration    (default: built from PAL_JMX_HOST/PORT)
rem
rem PAL_JAVA_OPTS is a catch-all appended LAST and always wins (see below).
rem ---------------------------------------------------------------------------
if "%COMMAND%" == "run" (
  if "%PAL_HEAP_OPTS%" == "" set PAL_HEAP_OPTS=-Xmx1g

  if "%PAL_GC_OPTS%" == "" set PAL_GC_OPTS=-server -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled

  if "%PAL_JMX_OPTS%" == "" (
    if not "%PAL_JMX_HOST%" == "" if not "%PAL_JMX_PORT%" == "" (
      set PAL_JMX_OPTS=-Dcom.sun.management.jmxremote ^
        -Djava.rmi.server.hostname=%PAL_JMX_HOST% ^
        -Dcom.sun.management.jmxremote.port=%PAL_JMX_PORT% ^
        -Dcom.sun.management.jmxremote.local.only=true ^
        -Dcom.sun.management.jmxremote.authenticate=false ^
        -Dcom.sun.management.jmxremote.ssl=false
    )
  )
)

rem ---------------------------------------------------------------------------
rem pal.vmoptions file - persistent JVM configuration
rem
rem One JVM option per line; blank lines and lines starting with '#' are
rem ignored.  Loaded from %PAL_HOME%\config\pal.vmoptions if the file exists.
rem Copy config\pal.vmoptions.example to config\pal.vmoptions to get started.
rem ---------------------------------------------------------------------------
set PAL_VMOPTIONS=
if exist "%PAL_HOME%\config\pal.vmoptions" (
  for /f "usebackq eol=# tokens=*" %%i in ("%PAL_HOME%\config\pal.vmoptions") do (
    set PAL_VMOPTIONS=!PAL_VMOPTIONS! %%i
  )
)

rem ---------------------------------------------------------------------------
rem Java agent
rem ---------------------------------------------------------------------------
set AGENT_OPT=
if not "%JAVA_AGENT%" == "" set AGENT_OPT=-javaagent:%JAVA_AGENT%

rem ---------------------------------------------------------------------------
rem Logging configuration
rem ---------------------------------------------------------------------------
set LOGGING_OPTS=
if exist "%PAL_PEER_LOGGING_CONFIG%" set LOGGING_OPTS=!LOGGING_OPTS! -Dpeer.logging=%PAL_PEER_LOGGING_CONFIG%
if exist "%PAL_CLI_LOGGING_CONFIG%" set LOGGING_OPTS=!LOGGING_OPTS! -Dcli.logging=%PAL_CLI_LOGGING_CONFIG%

rem ---------------------------------------------------------------------------
rem Assemble final JVM options
rem
rem Precedence (last wins):
rem   Chronicle exports -> category vars -> vmoptions file -> PAL_JAVA_OPTS
rem ---------------------------------------------------------------------------
set JAVA_OPTS=%CHRONICLE_EXPORTS%
if "%COMMAND%" == "run" (
  set JAVA_OPTS=!JAVA_OPTS! %PAL_HEAP_OPTS% %PAL_GC_OPTS% %PAL_JMX_OPTS%
)
set JAVA_OPTS=%JAVA_OPTS% %AGENT_OPT% %LOGGING_OPTS% %PAL_VMOPTIONS% %PAL_JAVA_OPTS%

rem Find pal's jar under %PAL_HOME%\lib
set PAL_JAR=
for %%i in ("%PAL_HOME%\lib\pal-*.jar") do set PAL_JAR=%%i

if "%PAL_JAR%" == "" (
  echo ERROR: Could not find pal-*.jar in %PAL_HOME%\lib
  exit /b 1
)

rem Execute the Pal application
java %JAVA_OPTS% -jar %PAL_JAR% %*

endlocal
