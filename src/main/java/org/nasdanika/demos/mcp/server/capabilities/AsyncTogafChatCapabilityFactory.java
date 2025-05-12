package org.nasdanika.demos.mcp.server.capabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.nasdanika.ai.Embeddings;
import org.nasdanika.capability.CapabilityProvider;
import org.nasdanika.capability.ServiceCapabilityFactory;
import org.nasdanika.common.ProgressMonitor;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest.Builder;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.ModelPreferences;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

public class AsyncTogafChatCapabilityFactory extends ServiceCapabilityFactory<Void, AsyncToolSpecification> {

	@Override
	public boolean isFor(Class<?> type, Object requirement) {
		return AsyncToolSpecification.class == type && requirement == null;
	}

	@Override
	protected CompletionStage<Iterable<CapabilityProvider<AsyncToolSpecification>>> createService(
			Class<AsyncToolSpecification> serviceType, 
			Void serviceRequirement, 
			Loader loader,
			ProgressMonitor progressMonitor) {

		
		Requirement<Embeddings.Requirement, Embeddings> embeddingsRequirement = ServiceCapabilityFactory.createRequirement(Embeddings.class);			
		CompletionStage<Embeddings> embeddingsCS = loader.loadOne(embeddingsRequirement, progressMonitor);

		return wrapCompletionStage(embeddingsCS.thenApply(this::createTool));
	}
	
	protected AsyncToolSpecification createTool(Embeddings embeddings) {		
		String schema = """
				{
				    "type": "object",
				    "properties": {
				      "question": { "type": "string" }
				    },
				    "required": ["question"]
				}	            
				""";
		
		Tool tool = new Tool(
				"togaf_chat", 
				"Answers questions about TOGAF 10 standard", 
				schema);
		
		return new McpServerFeatures.AsyncToolSpecification(
				tool, (exchange, arguments) -> {
					ClientCapabilities clientCapabilities = exchange.getClientCapabilities();
					if (clientCapabilities.sampling() == null) {
						List<Content> result = new ArrayList<>();
						result.add(new TextContent("TOGAF 10 is super-amazing!"));
						return Mono.just(new CallToolResult(result, false));						
					}
					
			        // Create a sampling request
			        Builder messageBuilder = McpSchema.CreateMessageRequest.builder();
			        SamplingMessage message = new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Calculate: " + arguments.get("expression")));
					ModelPreferences modelPreferences = McpSchema.ModelPreferences.builder()
//						    .hints(List.of(
//						        McpSchema.ModelHint.of("claude-3-sonnet"),
//						        McpSchema.ModelHint.of("claude")
//						    ))
					    .intelligencePriority(0.8)  // Prioritize intelligence
					    .speedPriority(0.5)         // Moderate speed importance
					    .build();
					McpSchema.CreateMessageRequest request = messageBuilder
			            .messages(List.of(message))
			            .modelPreferences(modelPreferences)
			            .systemPrompt("You are a helpful assistant. Answer the following question about TOGAF 10:" + arguments.get("question"))
			            .maxTokens(100)
			            .build();
			        
			        // Request sampling from the client
			        Mono<CreateMessageResult> messageResult = exchange.createMessage(request);
			        return messageResult.map(result -> {
						List<Content> resultContent = new ArrayList<>();
						resultContent.add(result.content());
						return new CallToolResult(resultContent, false);
			        });			        
				}
			);		
	}
	
}
