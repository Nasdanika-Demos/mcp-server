package org.nasdanika.demos.mcp.server.capabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.nasdanika.capability.CapabilityProvider;
import org.nasdanika.capability.ServiceCapabilityFactory;
import org.nasdanika.common.ProgressMonitor;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public class SyncCalculatorCapabilityFactory extends ServiceCapabilityFactory<Void, SyncToolSpecification> {

	@Override
	public boolean isFor(Class<?> type, Object requirement) {
		return SyncToolSpecification.class == type && requirement == null;
	}

	@Override
	protected CompletionStage<Iterable<CapabilityProvider<SyncToolSpecification>>> createService(
			Class<SyncToolSpecification> serviceType, 
			Void serviceRequirement, 
			Loader loader,
			ProgressMonitor progressMonitor) {

		
		String schema = """
				{
				    "type": "object",
				    "properties": {
				      "leftOperand": { "type": "number" },
				      "rightOperand": { "type": "number" }
				    },
				    "required": ["a", "b"]
				}	            
				""";
			
			SyncToolSpecification syncToolSpecification = new McpServerFeatures.SyncToolSpecification(
				new Tool("calculate_nasdanikus", "Computes nasdanikus function of two numbers", schema), 
				(exchange, arguments) -> {
					List<Content> result = new ArrayList<>();
					result.add(new TextContent("Result: " + arguments));
					return new CallToolResult(result, false);
				}
			);

		return wrap(syncToolSpecification);			
	}
	
}
