package org.nasdanika.demos.mcp.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import org.nasdanika.capability.CapabilityLoader;
import org.nasdanika.capability.CapabilityProvider;
import org.nasdanika.capability.ServiceCapabilityFactory;
import org.nasdanika.capability.ServiceCapabilityFactory.Requirement;
import org.nasdanika.cli.Application;
import org.nasdanika.cli.ShellCommand;
import org.nasdanika.cli.SubCommandRequirement;
import org.nasdanika.common.Closeable;
import org.nasdanika.common.LoggerProgressMonitor;
import org.nasdanika.common.ProgressMonitor;

import picocli.CommandLine;

public class Launcher {
	
	public static void main(String[] args) {
		CapabilityLoader capabilityLoader = new CapabilityLoader(Launcher.class.getModule().getLayer());
		ProgressMonitor progressMonitor =  new LoggerProgressMonitor(Logger.getLogger(Application.class.getName()));

		// Sub-commands, sorting alphabetically
		List<CommandLine> rootCommands = new ArrayList<>();		
		Requirement<SubCommandRequirement, CommandLine> subCommandRequirement = ServiceCapabilityFactory.createRequirement(CommandLine.class, null,  new SubCommandRequirement(Collections.emptyList()));
		for (CapabilityProvider<Object> cp: capabilityLoader.load(subCommandRequirement, progressMonitor)) {
			cp.getPublisher().filter(Objects::nonNull).collectList().block().forEach(cmd -> rootCommands.add((CommandLine) cmd));
		}
		
		// Executing the first one
		for (CommandLine rootCommand: rootCommands) {	
			rootCommand.addSubcommand(new ShellCommand(rootCommand));
			int exitCode;
			try {
				exitCode = rootCommand.execute(args);
			} finally {
				if (rootCommand instanceof Closeable) {
					((Closeable) rootCommand).close(progressMonitor.split("Closing root command", 1));
				}
				capabilityLoader.close(progressMonitor.split("Closing capability loader", 1));
			}
			System.exit(exitCode);
		}
		
		throw new UnsupportedOperationException("There are no root commands");		
	}	

}
