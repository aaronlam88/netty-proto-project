package gash.router.server.edges;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.server.ServerState;
import gash.router.server.queue.CommandMessageQueue;
import gash.router.server.queue.WorkMessageQueue;
import pipe.common.Common.Header;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.RountingTable;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import pipe.work.Work.RountingTable.Edge;

public class SendHeartBeat implements Runnable {
	private EdgeMonitor emon;
	private Logger logger = LoggerFactory.getLogger("SendHB");
	
	private ServerState state;
	public final WorkMessageQueue outgoing_work;
	public final CommandMessageQueue outgoing_command;

	public SendHeartBeat(EdgeMonitor emon) {
		super();
		this.emon = emon;
		outgoing_work = emon.outgoing_work;
		outgoing_command = emon.outgoing_command;
		
		state = emon.getState();
		emon.getOutboundEdges();
		emon.getInboundEdges();
		emon.isForever();
	}

	@Override
	public void run() {
		while (emon.isForever()) {
			for (EdgeInfo ei : emon.getOutboundEdges().map.values()) {
				if (ei.isActive() && ei.getChannel() != null && ei.getChannel().isActive()) {
					WorkMessage wm = createHB(ei);
					ei.getChannel().writeAndFlush(wm);
					logger.debug("send heartbeat to node " + ei.getRef());
				} else {
					emon.onAdd(ei);
					logger.error("cannot send heartbeat to node " + ei.getRef());
				}
			}
			try {
				Thread.sleep(state.getConf().getHeartbeatDt());
			} catch (InterruptedException e) {
				logger.error("can't sleep");
			}
		}
	}

	private WorkMessage createHB(EdgeInfo ei) {

		WorkState.Builder sb = WorkState.newBuilder();
		sb.setEnqueued(-1);
		sb.setProcessed(-1);

		Heartbeat.Builder bb = Heartbeat.newBuilder();
		bb.setState(sb);

		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(state.getConf().getNodeId());
		hb.setDestination(ei.getRef());
		hb.setTime(System.currentTimeMillis());
		hb.setMaxHops(1);
		hb.setSrcHost(state.getIP());
		hb.setSrcPort(state.getConf().getWorkPort());

		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.setHeader(hb);
		wb.setBeat(bb);
		wb.setSecret(ServerState.getSecret());

		RountingTable.Builder rb = RountingTable.newBuilder();
		for (EdgeInfo ei1 : emon.getOutboundEdges().map.values()) {
			Edge.Builder eb = Edge.newBuilder();
			eb.setHost(ei1.getHost());
			eb.setPort(ei1.getPort());
			rb.putRountingTable(ei1.getRef(), eb.build());
		}
		wb.setRoutingTable(rb.build());

		return wb.build();
	}
}

