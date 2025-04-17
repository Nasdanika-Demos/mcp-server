import org.nasdanika.capability.CapabilityFactory;
import org.nasdanika.demos.mcp.server.capabilities.SyncCalculatorCapabilityFactory;

module org.nasdanika.demos.mcp.server {
	
	exports org.nasdanika.demos.mcp.server;
	
	requires transitive org.nasdanika.ai.mcp.http;
	
	provides CapabilityFactory with 
		SyncCalculatorCapabilityFactory;
				
}