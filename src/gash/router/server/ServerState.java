package gash.router.server;

import java.util.ArrayList;
import java.util.Hashtable;
import gash.router.container.RoutingConf;
import gash.router.server.edges.EdgeMonitor;
import gash.router.server.location.Location;
import gash.router.server.tasks.TaskList;

public class ServerState {
	private RoutingConf conf;
	private EdgeMonitor emon;
	private TaskList tasks;
	
	private static String myIP;
	private static boolean isLeader = false;
	private static int leaderID = -1;
	private static Location leaderLocation = null;
	private static int term = 0;
	private static boolean voted = false;
	
	private static long secret = 10298576;
		
	public boolean isLeader() {
		return isLeader;
	}

	public void setLeader(boolean isLead) {
		ServerState.isLeader = isLead;
	}

	public int getLeaderID() {
		return leaderID;
	}

	public void setLeaderID(int leaderID) {
		ServerState.leaderID = leaderID;
	}

	public Location getLeaderLocation() {
		return leaderLocation;
	}

	public void setLeaderLocation(Location leaderLocation) {
		ServerState.leaderLocation = leaderLocation;
	}

	//==================Globe Log info==================
	/**
	 * filename_chunkID: 
	 * 		key: filename as String
	 *      value: ArrayList of chunk_ids
	 * use for quick look up all the file name in current network
	 */
	public static Hashtable<String, ArrayList<Integer>> filename_chunkID = new Hashtable<>();
	/**
	 * fileID_sha1: hash table 
 	 *		key:   fileID String === filename.chunkID
 	 *		value: sha1 byte[] the sha1 hash of the chunk_data
	 */
	public static Hashtable<String, byte[]> fileID_sha1 = new Hashtable<>();
	/**
	 * sha1_location: hash table
	 * 		key: sha1 byte[] sha1 hash of the chunk_data
	 * 		value: location of the chunk_data in network. Location (String host, int port)
	 */
	public static Hashtable<byte[], Location> sha1_location = new Hashtable<>();
	//=================================================
	
	//==================Local Log info==================
	/**
	 * myfile_sha1: hash table
	 * 		key: fileID String === the name of all local files in current node
	 * 		value: sha1 byte[] === the sha1 of the chunk_data
	 */
	public static Hashtable<String, byte[]> myfile_sha1 = new Hashtable<>();
	//=================================================
	
	public RoutingConf getConf() {
		return conf;
	}

	public void setConf(RoutingConf conf) {
		this.conf = conf;
	}

	public EdgeMonitor getEmon() {
		return emon;
	}

	public void setEmon(EdgeMonitor emon) {
		this.emon = emon;
	}

	public TaskList getTasks() {
		return tasks;
	}

	public void setTasks(TaskList tasks) {
		this.tasks = tasks;
	}

	public String getIP() {
		return myIP;
	}

	public void setIP(String myIP) {
		ServerState.myIP = myIP;
	}

	public int getTerm() {
		return term;
	}

	public void setTerm(int term) {
		ServerState.term = term;
	}

	public static long getSecret() {
		return secret;
	}

	public void setSecret(long secret) {
		ServerState.secret = secret;
	}

	public boolean isVoted() {
		return voted;
	}

	public void setVoted(boolean voted) {
		ServerState.voted = voted;
	}
}

