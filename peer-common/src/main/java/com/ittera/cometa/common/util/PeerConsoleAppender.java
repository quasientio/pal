package com.ittera.cometa.common.util;

/**
 * The purpose of this subclass is to not have to change package name in logback.xml after
 * mvn-shading (see issue #168 more details). Having our own ConsoleAppender class means we can
 * leave the full package and class unchanged since we only relocate dependencies, not our own
 * classes
 */
public final class PeerConsoleAppender extends ch.qos.logback.core.ConsoleAppender {}
