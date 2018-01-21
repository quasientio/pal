package com.ittera.cometa.util;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
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
		options.addOption(Option.builder("r").required(false).longOpt("num-requests").hasArg()
			.desc("number of requests to send").build());
		options.addOption(Option.builder("c").required(false).longOpt("num-clients").hasArg()
			.desc("number of clients to use").build());
		options.addOption(Option.builder("rl").required(false).longOpt("read-log").hasArg()
			.desc("read from given log").build());
		options.addOption(Option.builder("wl").required(false).longOpt("write-log").hasArg()
			.desc("write to given log").build());
		options.addOption(Option.builder("l").required(false).longOpt("log").hasArg()
			.desc("read and write from/to given log").build());
		options.addOption(Option.builder("f").required(false).longOpt("forget-reply")
			.desc("do not wait for replies").build());
		options.addOption(Option.builder("a").required(false).longOpt("async")
			.desc("send to log in async mode").build());
		options.addOption(Option.builder("v").required(false).longOpt("verbose")
			.desc("print useful info").build());
		options.addOption(Option.builder("h").required(false).longOpt("help")
			.desc("print usage").build());

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
