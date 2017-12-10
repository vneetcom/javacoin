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
	
	//https://qiita.com/silverskyvicto/items/a32227bdb749d182103a
	private static Blockchain singleton = new Blockchain();
	public static Blockchain getInstance(){
		return singleton;
	}
	
	JSONArray chain = new JSONArray();
	JSONArray current_transactions = new JSONArray();
	Set<String> nodes = new HashSet<String>();
	
	/**
	 * ノードリストに新しいノードを加える
	 * @param address <str> ノードのアドレス 例: 'http://192.168.0.5:5000'
	 * @throws Exception 
	 */
	public void register_node(String address) throws Exception{
		//http://kaworu.jpn.org/java/Java%E3%81%A7URL%E3%82%92%E3%83%91%E3%83%BC%E3%82%B9%E3%81%99%E3%82%8B%E6%96%B9%E6%B3%95
		URL urlparse = new URL(address);
		String parsed_url = urlparse.getHost()+":"+urlparse.getPort();
		this.nodes.add(parsed_url);
	}
	
	/**
	 * ブロックチェーンが正しいかを確認する
	 * @param chain: <list> ブロックチェーン
	 * @return
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
			
			// ブロックのハッシュが正しいかを確認
			if(!block.get("previous_hash").equals(hash(last_block))){
				return false;
			}
			
			// プルーフ・オブ・ワークが正しいかを確認
			if(! valid_proof((int)last_block.get("proof"), (int)block.get("proof"))){
				return false;
			}
			
			last_block = block;
			current_index += 1;			
		}
		
		return true;
	}
	
	/**
	 * これがコンセンサスアルゴリズムだ。ネットワーク上の最も長いチェーンで自らのチェーンを
	 * 置き換えることでコンフリクトを解消する。
	 * @return <bool> 自らのチェーンが置き換えられると True 、そうでなれけば False
	 * @throws Exception 
	 */
	public boolean resolve_conflicts() throws Exception{
		
		Set<String> neighbours = this.nodes;
		JSONArray new_chain = null;
		
		// 自らのチェーンより長いチェーンを探す必要がある
		int max_length = this.chain.length();
		
		
		// 他のすべてのノードのチェーンを確認
		CloseableHttpClient httpclient = HttpClients.createDefault();
		for (String node : neighbours){
			
//			http://d.hatena.ne.jp/Kazuhira/20131026/1382796711
//			http://www.baeldung.com/jersey-jax-rs-client
//			https://itsakura.com/java-httpclient
			HttpGet request = new HttpGet("http://"+node+"/javacoin/chain");
			CloseableHttpResponse response = httpclient.execute(request);
			
			if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK){

				// https://stackoverflow.com/questions/18899232/how-to-parse-this-json-response-in-java
				// https://stackoverflow.com/questions/15801247/parsing-a-json-array-from-http-response-in-java				
				JSONObject obj = new JSONObject(IOUtils.toString(new BufferedReader(new InputStreamReader(response.getEntity().getContent()))));
				int length = (int) obj.get("length");
				JSONArray chain = (JSONArray) obj.get("chain");
				
				// そのチェーンがより長いか、有効かを確認
				if (length > max_length && valid_chain(chain)){
					max_length = length;
					new_chain = chain;
				}
			}
		}
		
		// もし自らのチェーンより長く、かつ有効なチェーンを見つけた場合それで置き換える
		if (new_chain != null){
			this.chain = new_chain;
			return true;
		}	
		return false;
	}


	/**
	 * ブロックチェーンに」新しいブロックを作る
	 * @param proof　<int> プルーフ・オブ・ワークアルゴリズムから得られるプルール
	 * @param previous_hash （オプション） <String> 前のブロックのハッシュ
	 * @return <dict> 新しいブロック
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
	 * 次に採掘されるブロックに加える新しいトランザクションを作る
	 * @param sender: <String> 送信者のアドレス
	 * @param recipient: <String> 受信者のアドレス
	 * @param amount: <int> 量
	 * @return:　<int> このトランザクションを含むブロックのアドレス
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
	 * チェーンの最後のブロックをリターンする
	 * @return
	 */
	public JSONObject last_block(){
		if(this.chain.length()==0){
			return null;
		}
		return (JSONObject) this.chain.get(chain.length()-1);
	}
	
	/**
	 * ブロックのSHA-256ハッシュを作る
	 * @param block: <JSONObject> ブロック
	 * @return: <String>
	 * @throws Exception 
	 */
	public static String hash(JSONObject block) throws Exception{
		
		// 必ず[Python3の場合]ディクショナリ（辞書型のオブジェクト）がソートされている必要がある。そうでないと、一貫性のないハッシュとなってしまう。
		String block_string = block.toString();
		return localHashLib(block_string);
		
	}

	private static String localHashLib(String block_string) throws NoSuchAlgorithmException {
		//		http://kaworu.jpn.org/java/Java%E3%81%A7SHA256%E3%82%92%E8%A8%88%E7%AE%97%E3%81%99%E3%82%8B
		//		https://qiita.com/rsuzuki/items/7e3bd8248c55dab8341d
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
	 * シンプルなプルーフ・オブ・ワークのアルゴリズム：
	 * 	- hash(pp')の最初の４つが0となるようなp'を探す
	 * 	- p は前のプルーフ、p'は新しいプルーフ
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
	 * プルーフが正しいかを確認する: hash(last_proof, proof)の最初の４つが0となっているか？
	 * @param last_proof: <int> 前のプルーフ
	 * @param proof: <int> 現在のプルーフ
	 * @return: <boolean> 正しければtrue、そうでなければfalse
	 * @throws Exception 
	 */
	public static boolean valid_proof(int last_proof, int proof) throws Exception{
		String guess = String.valueOf(last_proof)+String.valueOf(proof);
		String guess_hash = localHashLib(guess);
		return "0000".equals(guess_hash.substring(0,4));
	}
	
}
