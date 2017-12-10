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

	// このノードのグローバルにユニークなアドレスを作る
	String node_identifire = null;
	
	// ブロックチェーンクラスをインスタンス化する
	Blockchain blockchain = Blockchain.getInstance();
	
	/**
	 * メソッドはGETで/mineエンドポイントを作る
	 * @return
	 * @throws Exception 
	 */
    @GET
    @Path("mine")
    @Produces(MediaType.APPLICATION_JSON)
    public String mine() throws Exception {
    	// 次のプルーフを見つけるためプルーフ・オブ・ワークアルゴリズムを使用する
    	JSONObject last_block = blockchain.last_block();
    	
    	int proof=0;
    	if(last_block!=null){
	    	int last_proof = (int) last_block.get("proof");
	    	proof = blockchain.proof_of_work(last_proof);
    	}
    	
    	// プルーフを見つけたことに対する報酬を得る
    	// 送信者は、採掘者が新しいコインを採掘したことを表すために"0"とする
    	blockchain.new_transaction("0", node_identifire, 1);
    	
    	// チェーンに新しいブロックを加えることで、新しいブロックを採掘する
    	JSONObject block = blockchain.new_block(proof, null);
    	
    	JSONObject response = new JSONObject();
    	response.put("message","Mined new block");
    	response.put("index", block.get("index"));
    	response.put("transactions", block.get("transactions"));
    	response.put("proof", block.get("proof"));
    	response.put("previous_hash", block.get("previous_hash"));
        return response.toString();
    }
    
    /**
     * メソッドはPOSTで/transactions/newエンドポイントを作る。メソッドはPOSTなのでデータを送信する
     * @return
     * @throws Exception
     */
    @POST
    @Path("transactions_new")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String new_transaction(
    		@Context HttpServletRequest request) throws Exception {

    	JSONObject values = requestParamsToJSON(request);

    	// POSTされたデータに必要なデータがあるかを確認
    	if(
    		values.get("sender") == null ||
    		values.get("recipient") == null ||
    		values.get("amount") == null
    	){
    		return "Missing values"; // TODO return code 400
    	}
    
    	// 新しいトランザクションを作る
    	int index = blockchain.new_transaction(
    			(String) values.get("sender"), 
    			(String) values.get("recipient"), 
    			(int) values.get("amount"));
    	
    	JSONObject response = new JSONObject();
    	response.put("message", "The transaction is added into block "+index);
    	return response.toString();
    	
    }

	/**
	 * メソッドはGETで、フルのブロックチェーンをリターンする/chainエンドポイントを作る
	 * @return
	 * @throws Exception 
	 */
    @GET
    @Path("chain")
    @Produces(MediaType.APPLICATION_JSON)
    public String full_chain() throws Exception {
    	JSONObject response = new JSONObject();
    	response.put("chain", blockchain.chain);
    	response.put("length", blockchain.chain.length()); // TODO: ?initial 1 or 0?
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
    		return "Error: Incorrect node list."; // TODO: return code 400
    	}

    	for(Object node : nodes){
    		blockchain.register_node((String)node);
    	}
    	
    	JSONObject response = new JSONObject();
    	response.put("message", "New node is added.");
    	response.put("total_nodes", blockchain.nodes.size()); // TODO: ?initial 1 or 0?
    	return response.toString();
    	
    }
    
    @GET
    @Path("nodes_resolve")
    @Produces(MediaType.APPLICATION_JSON)
    public String consensus() throws Exception {
    	
    	boolean replaced = blockchain.resolve_conflicts();
    	
    	JSONObject response = new JSONObject();
    	if(replaced){
        	response.put("message", "chain is replaced.");
        	response.put("new_chain", blockchain.chain);
    	}else{
        	response.put("message", "chain is confirmed.");
        	response.put("chain", blockchain.chain);
    	}
        return response.toString();
    }    
    
	private JSONObject requestParamsToJSON(HttpServletRequest request) throws Exception {

		//https://stackoverflow.com/questions/7085545/how-to-convert-http-request-body-into-json-object-in-java
//		https://stackoverflow.com/questions/33896136/read-json-message-from-http-post-request-in-java
		// https://matome.naver.jp/odai/2137432732637566701
		// https://stackoverflow.com/questions/7318632/java-lang-illegalstateexception-getreader-has-already-been-called-for-this-re
		BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
		return new JSONObject(IOUtils.toString(reader));
		
	}
    
}