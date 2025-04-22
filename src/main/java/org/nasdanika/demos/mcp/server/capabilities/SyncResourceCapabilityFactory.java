package org.nasdanika.demos.mcp.server.capabilities;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.nasdanika.capability.CapabilityProvider;
import org.nasdanika.capability.ServiceCapabilityFactory;
import org.nasdanika.common.NasdanikaException;
import org.nasdanika.common.ProgressMonitor;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

public class SyncResourceCapabilityFactory extends ServiceCapabilityFactory<Void, SyncResourceSpecification> {

	private static final String SITE_URL = "https://docs.nasdanika.org";

	@Override
	public boolean isFor(Class<?> type, Object requirement) {
		return SyncResourceSpecification.class == type && requirement == null;
	}

	@Override
	protected CompletionStage<Iterable<CapabilityProvider<SyncResourceSpecification>>> createService(
			Class<SyncResourceSpecification> serviceType, 
			Void serviceRequirement, 
			Loader loader,
			ProgressMonitor progressMonitor) {
		
		try {
			List<ResourceContents> contents = new ArrayList<>();
			URL url = new URL(SITE_URL + "/search-documents.json");
			try (InputStream in = url.openStream()) {
				JSONObject jsonObject = new JSONObject(new JSONTokener(in));
				for (String path: jsonObject.keySet()) {		
					if ("core/telemetry/index.html".equals(path)) {
						JSONObject data = jsonObject.getJSONObject(path);
						contents.add(new TextResourceContents(
								SITE_URL + path, 
								"text/plain",
								data.getString("content")));
					}
				}
			}			
				
			SyncResourceSpecification syncToolSpecification = new McpServerFeatures.SyncResourceSpecification(
				new Resource(
						SITE_URL,
						"Nasdanika documentation",
						"Nasdanika documentation site in plain text",
						"text/plain",
						null), 
				(exchange, arguments) -> {
					return new ReadResourceResult(contents);
				}
			);
	
			return wrap(syncToolSpecification);			
		} catch (IOException e) {
			e.printStackTrace();
			throw new NasdanikaException(e);
		}
	}
	
}
