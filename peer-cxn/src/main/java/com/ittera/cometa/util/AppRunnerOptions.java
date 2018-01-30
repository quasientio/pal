package com.ittera.cometa.util;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class AppRunnerOptions {

	// options available
	int requests, clients;
	String inLog, outLog;
	boolean async, sendAndForget, verbose;
	// cmd line args after options
	List<String> argList;

	public static AppRunnerOptions parseFrom(String[] args) {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption("r", "num-requests", true, "number of requests to send");
		options.addOption("c", "num-clients", true, "number of clients to use");
		options.addOption("rl", "read-log", true, "read from given log");
		options.addOption("wl", "write-log", true, "write to given log");
		options.addOption("l", "log", true, "read and write from/to given log");
		options.addOption("f", "forget-reply", false, "do not wait for replies");
		options.addOption("a", "async", false, "send to log in async mode");
		options.addOption("v", "verbose", false, "print useful info");
		options.addOption("h", "help", false, "print usage");

		CommandLine cmdLine = null;
		try {
			cmdLine = parser.parse(options, args);
		} catch (ParseException exp) {
			System.err.println(exp.getMessage());
			System.exit(1);
		}

		if (cmdLine.hasOption("help")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("runner", options);
			System.exit(0);
		}

		// create and fill runner options
		AppRunnerOptions opts = new AppRunnerOptions();
		opts.requests = Integer.parseInt(cmdLine.getOptionValue("r", "1").trim());
		opts.clients = Integer.parseInt(cmdLine.getOptionValue("c", "1").trim());
		opts.verbose = cmdLine.hasOption("v");
		opts.sendAndForget = cmdLine.hasOption("forget-reply");
		opts.async = cmdLine.hasOption("async");
		boolean readLogGiven = cmdLine.hasOption("read-log") || cmdLine.hasOption("log");
		boolean writeLogGiven = cmdLine.hasOption("write-log") || cmdLine.hasOption("log");

		if (readLogGiven) {
			String logName = cmdLine.getOptionValue("l");
			if (logName == null) {
				logName = cmdLine.getOptionValue("rl");
			}
			opts.inLog = logName.trim();
		}

		if (writeLogGiven) {
			String logName = cmdLine.getOptionValue("l");
			if (logName == null) {
				logName = cmdLine.getOptionValue("wl");
			}
			opts.outLog = logName.trim();
		}

		opts.argList = cmdLine.getArgList();

		return opts;
	}
}
