/**
 * Copyright 2016 Gash.
 *
 * This file and intellectual content is protected under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package gash.router.server;

import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.server.edges.ElectionMonitor;
import gash.router.server.location.Location;
import gash.router.server.queue.WorkMessageQueue;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import pipe.election.Election.LeaderStatus;
import pipe.election.Election.LeaderStatus.LeaderQuery;
import pipe.work.Work.RountingTable.Edge;
import pipe.work.Work.Task;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;

/**
 * The message handler processes json messages that are delimited by a 'newline'
 * 
 * 
 * @author gash
 * 
 */
public class WorkHandler extends SimpleChannelInboundHandler<WorkMessage> {
	protected static Logger logger = LoggerFactory.getLogger("WorkHandler");
	protected ServerState state;
	protected boolean debug = false;
	protected WorkMessageQueue forwardQueue;

	public WorkHandler(ServerState state) {
		if (state != null) {
			this.state = state;
		}
		this.forwardQueue = WorkMessageQueue.getInstance();
	}

	/**
	 * override this method to provide processing behavior. T
	 * 
	 * @param msg
	 */
	public void handleMessage(WorkMessage msg, Channel channel) {
		if (msg == null) {
			logger.error("Unexpected content - " + msg);
			return;
		}

		if (debug)
			PrintUtil.printWork(msg);

		// can add enum to proto file to turn this into switch, improve performance?!
		try {
			if (msg.hasBeat()) {
				handleBeat(msg);
			} else if (msg.hasPing()) {
				handlePing(msg);
			} else if (msg.hasErr()) {
				Failure err = msg.getErr();
				logger.error("failure from " + msg.getHeader().getNodeId() + " message: " + err.getMessage());
				if (debug)
					PrintUtil.printFailure(err);
			} else if (msg.hasTask()) {
				Task t = msg.getTask();
			} else if (msg.hasState()) {
				WorkState s = msg.getState();
			} else if (msg.hasLeader()) {
				handleElection(msg);
			}
		} catch (Exception e) {
			Failure.Builder eb = Failure.newBuilder();
			eb.setId(state.getConf().getNodeId());
			eb.setRefId(msg.getHeader().getNodeId());
			eb.setMessage(e.getMessage());
			WorkMessage.Builder rb = WorkMessage.newBuilder(msg);
			rb.setErr(eb);
			channel.write(rb.build());
			logger.error("catch exception " + e.getMessage());
		}

		System.out.flush();

	}

	private void handleElection(WorkMessage msg) {
		logger.info("election from " + msg.getHeader().getNodeId());

		ElectionMonitor.electionQueue.offer(msg);

	}

	private void handlePing(WorkMessage msg) {
		logger.info("ping from " + msg.getHeader().getNodeId());
	}

	private void handleBeat(WorkMessage msg) {
		logger.debug("heartbeat from " + msg.getHeader().getNodeId());

		// update my routing table to include sender
		int sender = msg.getHeader().getNodeId();
		
		// sanity check, avoid loop message
		// if I'm the sender, discard this beat
		if (sender == state.getConf().getNodeId()) {
			return;
		}

		String src_host = msg.getHeader().getSrcHost();
		int src_port = msg.getHeader().getSrcPort();

		// create two way connect with sender
		state.getEmon().createOutboundIfNew(sender, src_host, src_port);

		// copy sender routing table to my
		if (msg.hasRoutingTable()) {
			for (Entry<Integer, Edge> e : msg.getRoutingTable().getRountingTableMap().entrySet()) {
				if (e.getKey() != state.getConf().getNodeId())
					state.getEmon().createOutboundIfNew(e.getKey(), e.getValue().getHost(), e.getValue().getPort());
			}
		}
	}
	
	/**
	 * check to see if we should discard WorkMessage msg
	 * @param msg
	 * @return
	 * 		true: we don't need to care about this msg, discard it (return)
	 * 		false: we have to read this msg or forward it.
	 */
	protected boolean shouldDiscard(WorkMessage msg) {
		Header header = msg.getHeader();
		int maxHop = header.getMaxHops();
		int src = header.getNodeId();
		long time = header.getTime();
		long secret = msg.getSecret();
		
		// if the secret not the same as network secret, discard
		if(secret != ServerState.getSecret()) {
			return true;
		}
		// if max hop == 0, discard
		if (maxHop == 0) {
			// discard this message
			return true;
		}
		// if message is older than 1 minutes (60000ms), discard
		if ((System.currentTimeMillis() - time) > 60000) {
			// discard this message
			return true;
		}
		
		// if I send this msg to myself, discard
		// avoid echo msg
		if (src == state.getConf().getNodeId()) {
			return true;
		}
		
		// the above cases should cover all the problems
		return false;
	}
	
	/**
	 * rebuild msg so it can be forward to other node, namely --maxHop
	 * @param msg
	 * @return
	 * 		WorkMessage with new maxHop = old maxHop - 1
	 */
	protected WorkMessage rebuildMessage (WorkMessage msg) {
		Header header = msg.getHeader();
		int maxHop = header.getMaxHops();
		--maxHop;
		// build new header from old header, only update maxHop
		Header.Builder hb = Header.newBuilder();
		hb.mergeFrom(header);
		hb.setMaxHops(maxHop);
		
		// build new msg from old msg, only update Header
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.mergeFrom(msg);
		wb.setHeader(hb.build());
		return wb.build();
	}

	/**
	 * a message was received from the server. Here we dispatch the message to
	 * the client's thread pool to minimize the time it takes to process other
	 * messages.
	 * 
	 * @param ctx
	 *            The channel the message was received from
	 * @param msg
	 *            The message
	 */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, WorkMessage msg) throws Exception {
		logger.debug("msg maxHop " + msg.getHeader().getMaxHops() + " ping: " + msg.hasPing());
		
		// check if I should discard this msg or not
		if(shouldDiscard(msg)) {
			return;
		}
		
		logger.debug("get msg from " + msg.getHeader().getNodeId());
		
		int dest = msg.getHeader().getDestination();
		if (dest == -1) {
			// this is broadcast message, put it in forward queue and read it
			// too
			logger.debug("broadcast from " + msg.getHeader().getNodeId());
			handleMessage(msg, ctx.channel());
			// rebuild this msg for broadcast
			msg = rebuildMessage(msg);
			forwardQueue.offer(msg);
		} else if (dest == state.getConf().getNodeId()) {
			// this message is for me, I should read it
			handleMessage(msg, ctx.channel());
		} else {
			// this is private message for other, put it in forward queue
			// rebuild this msg for forward
			msg = rebuildMessage(msg);
			forwardQueue.offer(msg);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

}