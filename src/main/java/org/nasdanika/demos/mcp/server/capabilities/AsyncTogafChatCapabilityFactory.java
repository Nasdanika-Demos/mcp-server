package org.nasdanika.demos.mcp.server.capabilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.nasdanika.ai.Chat;
import org.nasdanika.ai.Chat.ResponseMessage;
import org.nasdanika.ai.EmbeddingGenerator;
import org.nasdanika.ai.SearchResult;
import org.nasdanika.ai.SimilaritySearch;
import org.nasdanika.ai.SimilaritySearch.EmbeddingsItem;
import org.nasdanika.ai.SimilaritySearch.IndexId;
import org.nasdanika.ai.TextFloatVectorEmbeddingModel;
import org.nasdanika.capability.CapabilityProvider;
import org.nasdanika.capability.ServiceCapabilityFactory;
import org.nasdanika.common.NasdanikaException;
import org.nasdanika.common.ProgressMonitor;

import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex;

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
	
	private record TextSearchConfig(SimilaritySearch<String, Float> textSearch, Function<String, String> textProvider) {}
 
	@Override
	protected CompletionStage<Iterable<CapabilityProvider<AsyncToolSpecification>>> createService(
			Class<AsyncToolSpecification> serviceType, 
			Void serviceRequirement, 
			Loader loader,
			ProgressMonitor progressMonitor) {

		Requirement<EmbeddingGenerator.Requirement, TextFloatVectorEmbeddingModel> embeddingsRequirement = ServiceCapabilityFactory.createRequirement(TextFloatVectorEmbeddingModel.class);			
		CompletionStage<TextFloatVectorEmbeddingModel> embeddingsCS = loader.loadOne(embeddingsRequirement, progressMonitor);		
		CompletionStage<TextSearchConfig> textSearchCS = embeddingsCS.thenApply(this::createTextSearch);		
		
		Requirement<Chat.Requirement, Chat> chatRequirement = ServiceCapabilityFactory.createRequirement(Chat.class);			
		CompletionStage<Chat> chatCS = loader.loadOne(chatRequirement, progressMonitor);		
		return wrapCompletionStage(textSearchCS.thenCombine(
				chatCS, 
				(textSearchConfig, chat) -> createTool(
						textSearchConfig.textSearch(), 
						textSearchConfig.textProvider(), 
						chat)));
	}
	
	protected TextSearchConfig createTextSearch(TextFloatVectorEmbeddingModel embeddingModel) {		
		// Hardcoded for a demo
		File index = new File("test-data/togaf/togaf-10-index.bin");
		File textMap = new File("test-data/togaf/togaf-10-map.json");				
		boolean exact = true;
		
		try (InputStream textMapInputStream = new FileInputStream(textMap)) {
			HnswIndex<IndexId, float[], EmbeddingsItem, Float> hnswIndex = HnswIndex.load(index);			
			SimilaritySearch<List<Float>, Float> vectorSearch = SimilaritySearch.from(exact ? hnswIndex.asExactIndex() : hnswIndex);				
			SimilaritySearch<List<List<Float>>, Float> multiVectorSearch = SimilaritySearch.adapt(vectorSearch);	
			JSONObject textMapObj = new JSONObject(new JSONTokener(textMapInputStream));
			Function<String, String> textProvider = uri -> textMapObj.getString(uri);			
			return new TextSearchConfig(SimilaritySearch.textFloatVectorEmbeddingSearch(multiVectorSearch, embeddingModel), textProvider);
		} catch (IOException e) {
			throw new NasdanikaException("Failed to load vector index or text map", e);
		}		
	}
	
	protected AsyncToolSpecification createTool(
			SimilaritySearch<String, Float> textSearch,
			Function<String, String> textProvider,
			Chat chat) {		
		String schema = """
				{
				    "type": "object",
				    "properties": {
				      "question": { 
				      	"type": "string",
				      	"description" : "A question to be answered by the tool" 
				      }
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
					Object question = arguments.get("question");
					if (clientCapabilities.sampling() == null) {
						// Sampling not supported - using own LLM
						
						Mono<List<SearchResult<Float>>> searchResultsMono = textSearch.findAsync((String) question, 10 /* hardcoded */);
						return searchResultsMono
							.flatMap(searchResults -> {
								List<Chat.Message> messages = new ArrayList<>();
					    		messages.add(Chat.Role.system.createMessage(
				    				"""	    				
				    				You are a helpful assistant.
				    				You will answer user question about TOGAF 10 standard leveraging provided documents
				    				and provide references to the used documents.
				    				Output your answer in markdown.	    				
				    				"""));
					    		messages.add(Chat.Role.user.createMessage((String) question));
					    		
					    		List<SearchResult<Float>> references = new ArrayList<>();
					    		
					    		Map<String, List<SearchResult<Float>>> groupedResults = org.nasdanika.common.Util.groupBy(searchResults, SearchResult::getUri);
								for (Entry<String, List<SearchResult<Float>>> sre: groupedResults.entrySet()) {
									boolean closeEnough = false;
									for (SearchResult<Float> sr: sre.getValue()) {
										if (sr.getDistance() <= 0.3 /* Hardcoded */) {
											closeEnough = true;
											references.add(sr);
										}
									}
									if (closeEnough) {
										StringBuilder messageBuilder = new StringBuilder("Use this document with URL " + sre.getKey() + ":" + System.lineSeparator());
										String contents = textProvider.apply(sre.getKey()); // No chunking/selection in this case - entire page.
										messageBuilder.append(System.lineSeparator() + System.lineSeparator() + contents);					
										messages.add(Chat.Role.system.createMessage(messageBuilder.toString()));
									}
								}
								
								if (references.isEmpty()) {
									List<Content> result = new ArrayList<>();
									result.add(new TextContent("TOGAF 10 is super-amazing!"));
									return Mono.just(new CallToolResult(result, false));														
								}				
								
								return chat
										.chatAsync(messages)
										.flatMap(chatResponses -> {
											List<Content> result = new ArrayList<>();
											for (ResponseMessage chatResponse: chatResponses) {
												result.add(new TextContent(chatResponse.getContent()));												
											}
											return Mono.just(new CallToolResult(result, false));																									
										});
							});
					}
					
			        // Create a sampling request
			        Builder messageBuilder = McpSchema.CreateMessageRequest.builder();
			        SamplingMessage message = new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent("Calculate: " + arguments.get("expression")));
					ModelPreferences modelPreferences = McpSchema.ModelPreferences.builder()
					    .intelligencePriority(0.8)  // Prioritize intelligence
					    .speedPriority(0.5)         // Moderate speed importance
					    .build();
					McpSchema.CreateMessageRequest request = messageBuilder
			            .messages(List.of(message))
			            .modelPreferences(modelPreferences)
			            .systemPrompt("You are a helpful assistant. Answer the following question about TOGAF 10:" + question)
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
