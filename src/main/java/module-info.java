import org.nasdanika.capability.CapabilityFactory;
import org.nasdanika.demos.mcp.server.capabilities.AsyncTogafChatCapabilityFactory;
import org.nasdanika.demos.mcp.server.capabilities.SyncCalculatorCapabilityFactory;
import org.nasdanika.demos.mcp.server.capabilities.SyncResourceCapabilityFactory;

module org.nasdanika.demos.mcp.server {
	
	exports org.nasdanika.demos.mcp.server;
	
	requires transitive org.nasdanika.ai.mcp.sse;
	requires transitive org.nasdanika.ai;
	
	provides CapabilityFactory with 
		SyncCalculatorCapabilityFactory,
		SyncResourceCapabilityFactory,
		AsyncTogafChatCapabilityFactory;
				
}