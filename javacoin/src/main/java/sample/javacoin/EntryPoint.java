package sample.javacoin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

@Path("/javacoin")
public class EntryPoint {

	// Generate a globally unique address for this node
	String node_identifire = null; // TODO
	
	// Instantiate the Blockchain
	Blockchain blockchain = Blockchain.getInstance();
	
    @GET
    @Path("mine")
    @Produces(MediaType.APPLICATION_JSON)
    public String mine() throws Exception {
    	// We run the proof of work algorithm to get the next proof...
    	JSONObject last_block = blockchain.last_block();
    	
    	int proof=0;
    	if(last_block!=null){
	    	int last_proof = (int) last_block.get("proof");
	    	proof = blockchain.proof_of_work(last_proof);
    	}
    	
    	// We must receive a reward for finding the proof.
    	// The sender is "0" to signify that this node has mined a new coin.
    	blockchain.new_transaction("0", node_identifire, 1);
    	
    	// Forge the new Block by adding it to the chain
    	String previous_hash = Blockchain.hash(last_block);
    	JSONObject block = blockchain.new_block(proof, previous_hash);
    	
    	JSONObject response = new JSONObject();
    	response.put("message","Mined new block");
    	response.put("index", block.get("index"));
    	response.put("transactions", block.get("transactions"));
    	response.put("proof", block.get("proof"));
    	response.put("previous_hash", block.get("previous_hash"));
        return response.toString();
    }
    
    @POST
    @Path("transactions_new")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String new_transaction(
    		@Context HttpServletRequest request) throws Exception {

    	JSONObject values = requestParamsToJSON(request);

    	// Check that the required fields are in the POST'ed data
    	if(
    		values.get("sender") == null ||
    		values.get("recipient") == null ||
    		values.get("amount") == null
    	){
    		return "Missing values";
    	}
    
    	// Create a new Transaction
    	int index = blockchain.new_transaction(
    			(String) values.get("sender"), 
    			(String) values.get("recipient"), 
    			(int) values.get("amount"));
    	
    	JSONObject response = new JSONObject();
    	response.put("message", "Transaction will be added to Block "+index);
    	return response.toString();
    	
    }

    @GET
    @Path("chain")
    @Produces(MediaType.APPLICATION_JSON)
    public String full_chain() throws Exception {
    	JSONObject response = new JSONObject();
    	response.put("chain", blockchain.chain);
    	response.put("length", blockchain.chain.length());
        return response.toString();
    }
    
    @POST
    @Path("nodes_register")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String register_node(
    		@Context HttpServletRequest request) throws Exception {

    	JSONObject values = requestParamsToJSON(request);

    	JSONArray nodesArray = (JSONArray) values.get("nodes");
    	List<Object> nodes = nodesArray.toList();
    	
    	if (nodes == null){
    		return "Error: Please supply a valid list of nodes";
    	}

    	for(Object node : nodes){
    		blockchain.register_node((String)node);
    	}
    	
    	JSONObject response = new JSONObject();
    	response.put("message", "New nodes have been added");
    	response.put("total_nodes", blockchain.nodes.size());
    	return response.toString();
    	
    }
    
    @GET
    @Path("nodes_resolve")
    @Produces(MediaType.APPLICATION_JSON)
    public String consensus() throws Exception {
    	
    	boolean replaced = blockchain.resolve_conflicts();
    	
    	JSONObject response = new JSONObject();
    	if(replaced){
        	response.put("message", "Our chain was replaced");
        	response.put("new_chain", blockchain.chain);
    	}else{
        	response.put("message", "Our chain is authoritative");
        	response.put("chain", blockchain.chain);
    	}
        return response.toString();
    }    
    
	private JSONObject requestParamsToJSON(HttpServletRequest request) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
		return new JSONObject(IOUtils.toString(reader));
		
	}
    
}