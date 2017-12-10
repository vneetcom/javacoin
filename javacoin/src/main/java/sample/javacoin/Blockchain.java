package sample.javacoin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;

public class Blockchain {
	
	private static Blockchain singleton = new Blockchain();
	public static Blockchain getInstance(){
		return singleton;
	}
	
	JSONArray chain = new JSONArray();
	JSONArray current_transactions = new JSONArray();
	Set<String> nodes = new HashSet<String>();
	
	/**
	 * Add a new node to the list of nodes
	 * @param address <str> Address of node. Eg. 'http://192.168.0.5:5000'
	 * @throws Exception 
	 */
	public void register_node(String address) throws Exception{
		URL urlparse = new URL(address);
		String parsed_url = urlparse.getHost()+":"+urlparse.getPort();
		this.nodes.add(parsed_url);
	}
	
	/**
	 * Determine if a given blockchain is valid
	 * @param chain: <list> A blockchain
	 * @return: True if valid, False if not
	 * @throws Exception 
	 */
	public boolean valid_chain(JSONArray chain) throws Exception{
		
		JSONObject last_block = (JSONObject) chain.get(0);
		int current_index = 1;
		
		while( current_index < chain.length()){
			JSONObject block = (JSONObject) chain.get(current_index);
			System.out.println(last_block);
			System.out.println(block);
			System.out.println("\n--------------\n");
			
			// Check that the hash of the block is correct
			if(!block.get("previous_hash").equals(hash(last_block))){
				return false;
			}
			
			// Check that the Proof of Work is correct
			if(! valid_proof((int)last_block.get("proof"), (int)block.get("proof"))){
				return false;
			}
			
			last_block = block;
			current_index += 1;			
		}
		
		return true;
	}
	
	/**
	 * This is our consensus algorithm, it resolves conflicts
	 * by replacing our chain with the longest one in the network.
	 * @return <bool> True if our chain was replaced, False if not
	 * @throws Exception 
	 */
	public boolean resolve_conflicts() throws Exception{
		
		Set<String> neighbours = this.nodes;
		JSONArray new_chain = null;
		
		// We're only looking for chains longer than ours
		int max_length = this.chain.length();
		
		
		// Grab and verify the chains from all the nodes in our network
		CloseableHttpClient httpclient = HttpClients.createDefault();
		for (String node : neighbours){
			
			HttpGet request = new HttpGet("http://"+node+"/javacoin/chain");
			CloseableHttpResponse response = httpclient.execute(request);
			
			if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK){

				JSONObject obj = new JSONObject(IOUtils.toString(new BufferedReader(new InputStreamReader(response.getEntity().getContent()))));
				int length = (int) obj.get("length");
				JSONArray chain = (JSONArray) obj.get("chain");
				
				// Check if the length is longer and the chain is valid
				if (length > max_length && valid_chain(chain)){
					max_length = length;
					new_chain = chain;
				}
			}
		}
		
		// Replace our chain if we discovered a new, valid chain longer than ours
		if (new_chain != null){
			this.chain = new_chain;
			return true;
		}	
		return false;
	}


	/**
	 * Create a new Block in the Blockchain
	 * @param proof　<int> The proof given by the Proof of Work algorithm
	 * @param previous_hash <String> Hash of previous Block
	 * @return <JSONObject> New Block
	 * @throws Exception 
	 */
	public JSONObject new_block(int proof, String previous_hash) throws Exception{
		
//		http://www.javainterviewpoint.com/read-json-java-jsonobject-jsonarray/
		
		JSONObject block = new JSONObject();
		block.put("index", this.chain.length() + 1);
		block.put("timestamp", new Timestamp(System.currentTimeMillis()));
		block.put("transactions", this.current_transactions);
		block.put("proof", proof);
		block.put("previous_hash",previous_hash != null ? previous_hash : 
			this.chain.length() == 0 ? "0" : hash((JSONObject)this.chain.get(this.chain.length()-1)));
		
		// 現在のトランザクションリストをリセット
		this.current_transactions = new JSONArray();
		
		this.chain.put(block);
		
		return block;
		
	}

	/**
	 * Creates a new transaction to go into the next mined Block
	 * @param sender: <String> Address of the Sender
	 * @param recipient: <String> Address of the Recipient
	 * @param amount: <int> Amount
	 * @return:　<int> The index of the Block that will hold this transaction
	 */
	public int new_transaction(String sender, String recipient, int amount){
		
		JSONObject newTransaction = new JSONObject();
		newTransaction.put("sender",sender);
		newTransaction.put("recipient",recipient);
		newTransaction.put("amount", amount);
		this.current_transactions.put(newTransaction);
		
		if(last_block()==null){
			return 1;
		}
		return (int) last_block().get("index") + 1;
	}
	
	/**
	 * Return the last block of chain
	 * @return
	 */
	public JSONObject last_block(){
		if(this.chain.length()==0){
			return null;
		}
		return (JSONObject) this.chain.get(chain.length()-1);
	}
	
	/**
	 * Creates a SHA-256 hash of a Block
	 * @param block: <JSONObject> Block
	 * @return: <String> SHA-256 hash of a Block
	 * @throws Exception 
	 */
	public static String hash(JSONObject block) throws Exception{
		if(block == null){
			return null;
		}
		// (If original Python code,) We must make sure that the Dictionary is Ordered, or we'll have inconsistent hashes
		String block_string = block.toString();
		return localHashLib(block_string);
		
	}

	private static String localHashLib(String block_string) throws NoSuchAlgorithmException {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				md.update(block_string.getBytes());
				byte[] cipher_byte = md.digest();
				StringBuilder sb = new StringBuilder(2 * cipher_byte.length);
				for(byte b : cipher_byte){
					sb.append(String.format("%02x", b&0xff));
				}
				return sb.toString();
	}
	
	/**
	 * Simple Proof of Work Algorithm:
	 *  - Find a number p' such that hash(pp') contains leading 4 zeroes, where p is the previous p'
	 * 	- p is the previous proof, and p' is the new proof
	 * @param last_proof: <int>
	 * @return: <int>
	 * @throws Exception 
	 */
	public int proof_of_work(int last_proof) throws Exception{
		int proof = 0;
		while (valid_proof(last_proof, proof) == false){
			proof++;
		}
		return proof;
	}
	
	/**
	 * Validates the Proof
	 * @param last_proof: <int> Previous Proof
	 * @param proof: <int> Current Proof
	 * @return: <boolean> True if correct, False if not.
	 * @throws Exception 
	 */
	public static boolean valid_proof(int last_proof, int proof) throws Exception{
		String guess = String.valueOf(last_proof)+String.valueOf(proof);
		String guess_hash = localHashLib(guess);
		return "0000".equals(guess_hash.substring(0,4));
	}
	
}
