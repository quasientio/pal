package com.ittera.cometa.concentrator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.HelpFormatter;

import com.ittera.cometa.LogInfo;

import java.util.UUID;

public class PeerOptions {

	private final Options options;

	// peer cmd-line options
	LogInfo inLog;
	LogInfo outLog;
	boolean offsetGiven;
	Long offset;
	UUID uuid;
	boolean helpNeeded;
	String classpath;

	private PeerOptions(Options options) {
		this.options = options;
	}

	void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("peer", options);
	}

	public static PeerOptions parse(String[] args) {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption("u", "use-uuid", true, "use given uuid");
		options.addOption("r", "read-log", true, "read from given log");
		options.addOption("w", "write-log", true, "write to given log");
		options.addOption("l", "log", true, "read and write from/to given log");
		options.addOption("s", "offset-start", true, "read from given offset (requires -l or -r)");
		options.addOption("cp", "classpath", true, "load classes from given folders/JARs");
		options.addOption("h", "help", false, "print usage");

		CommandLine cmdLine = null;
		try {
			cmdLine = parser.parse(options, args);
		} catch (ParseException exp) {
			System.err.println(exp.getMessage());
			System.exit(1);
		}

		PeerOptions opts = new PeerOptions(options);
		opts.helpNeeded = cmdLine.hasOption("help");
		boolean readLog = cmdLine.hasOption("read-log") || cmdLine.hasOption("log");
		boolean writeLog = cmdLine.hasOption("write-log") || cmdLine.hasOption("log");
		opts.offsetGiven = cmdLine.hasOption("offset-start");

		// parse offset
		if (opts.offsetGiven) {
			opts.offset = Long.valueOf(cmdLine.getOptionValue("s").trim());
		}

		// parse uuid
		if (cmdLine.hasOption("use-uuid")) {
			opts.uuid = UUID.fromString(cmdLine.getOptionValue("u").trim());
		}

		// parse inLog
		if (readLog) {
			String logName = cmdLine.getOptionValue("l");
			if (logName == null) {
				logName = cmdLine.getOptionValue("r");
			}
			opts.inLog = new LogInfo(logName.trim());
		}

		// parse outLog
		if (writeLog) {
			String logName = cmdLine.getOptionValue("l");
			if (logName == null) {
				logName = cmdLine.getOptionValue("w");
			}
			opts.outLog = new LogInfo(logName.trim());
		}

		// parse jarsPath
		if (cmdLine.hasOption("classpath")) {
			opts.classpath = cmdLine.getOptionValue("cp").trim();
		}

		return opts;
	}
}
