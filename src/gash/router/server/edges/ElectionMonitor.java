package gash.router.server.edges;

import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.server.ServerState;
import gash.router.server.location.Location;
import gash.router.server.queue.CommandMessageQueue;
import gash.router.server.queue.WorkMessageQueue;
import pipe.common.Common.Header;
import pipe.election.Election.LeaderStatus;
import pipe.election.Election.RunForLeader;
import pipe.election.Election.LeaderStatus.LeaderQuery;
import pipe.election.Election.LeaderStatus.LeaderState;
import pipe.work.Work.WorkMessage;

public class ElectionMonitor implements Runnable {
	public enum State {
	    LEADER, FOLLOWER, CANDIDATE
	}
	
	private EdgeMonitor emon;
	private Logger logger = LoggerFactory.getLogger("ElectionMonitor");

	private ServerState state;

	public static LinkedBlockingQueue<WorkMessage> electionQueue = new LinkedBlockingQueue<>();

	public static State myState = State.CANDIDATE;
	public static int nodeCount = 0;
	public static int voteCount = 0;
	public static int term = 0;

	public ElectionMonitor(EdgeMonitor emon) {
		super();
		this.emon = emon;
		state = emon.getState();
		emon.getOutboundEdges();
		emon.getInboundEdges();
		emon.isForever();
	}

	private void callElection() {
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(state.getConf().getNodeId());
		hb.setDestination(-1);
		hb.setTime(System.currentTimeMillis());
		hb.setMaxHops(1);
		hb.setSrcHost(state.getIP());
		hb.setSrcPort(state.getConf().getWorkPort());

		LeaderStatus.Builder lsb = LeaderStatus.newBuilder();
		lsb.setAction(LeaderQuery.WHOISTHELEADER);
		lsb.setState(LeaderState.LEADERUNKNOWN);

		RunForLeader.Builder run = RunForLeader.newBuilder();
		run.setTerm(++term);
		run.setVoteForMeID(state.getConf().getNodeId());
		state.setVoted(true);
		++voteCount;

	}

	private boolean isWinner() {
		return voteCount > currentActiveNode() / 2;
	}

	private void declareLeader() {
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(state.getConf().getNodeId());
		hb.setDestination(-1);
		hb.setTime(System.currentTimeMillis());
		hb.setMaxHops(1);
		hb.setSrcHost(state.getIP());
		hb.setSrcPort(state.getConf().getWorkPort());

		LeaderStatus.Builder lsb = LeaderStatus.newBuilder();
		lsb.setAction(LeaderQuery.THELEADERIS);
		lsb.setState(LeaderState.LEADERALIVE);
		lsb.setLeaderHost(state.getIP());
		lsb.setLeaderPort(state.getConf().getWorkPort());

		myState = State.LEADER;
	}

	private void electionCycle() {
		callElection();
		while(!isWinner()) {
			
		}
	}

	public void timeOut() {
		// Random time out for election
		Random rand = new Random(System.currentTimeMillis());
		try {
			Thread.sleep(state.getConf().getHeartbeatDt() + rand.nextInt(1000));
		} catch (InterruptedException e) {
			logger.error("can't sleep");
		}
	}

	@Override
	public void run() {
		while (emon.isForever()) {
			int count = 0;
			WorkMessage wm = electionQueue.poll();
			while( (wm = electionQueue.poll()) == null) {
				timeOut();
				++count;
				if(count == 3) {
					leaderState = LeaderState.LEADERDEAD;
					break;
				}
			}
			
			switch (leaderState) {
			case LEADERUNKNOWN: // cold start
				electionCycle();
				break;
			case LEADERALIVE: // leader is alive
				
				break;
			case LEADERDEAD: // leader is dead
				electionCycle();
				break;
			}

		}

	}

	private int currentActiveNode() {
		int count = 0;
		for (EdgeInfo ei : emon.getOutboundEdges().map.values()) {
			if (ei.isActive() && ei.getChannel() != null && ei.getChannel().isActive()) {
				++count;
			}
		}
		return count;
	}
	
}
