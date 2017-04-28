package gash.router.server.edges;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.server.ServerState;
import gash.router.server.queue.CommandMessageQueue;
import gash.router.server.queue.WorkMessageQueue;
import pipe.work.Work.WorkMessage;

public class FowardWork implements Runnable {
	private EdgeMonitor emon;
	private Logger logger = LoggerFactory.getLogger("FowardWork");

	private ServerState state;
	public final WorkMessageQueue outgoing_work;
	public final CommandMessageQueue outgoing_command;

	public FowardWork(EdgeMonitor emon) {
		super();
		this.emon = emon;
		outgoing_work = emon.outgoing_work;
		outgoing_command = emon.outgoing_command;
	}

	@Override
	public void run() {
		while (emon.isForever()) {
			try {
				
				WorkMessage wm = emon.outgoing_work.take();
				
				int sender = wm.getHeader().getNodeId();
				int recver = wm.getHeader().getDestination();

				logger.info("sender: " + sender + " recver: " + recver);
				// I shouldn't send message to myself
				if (recver == state.getConf().getNodeId()) {
					continue;
				}
				// I shouldn't return the message to the sender
				if (sender == recver) {
					continue;
				}

				EdgeInfo ei = emon.getOutboundEdges().map.get(recver);
				if (ei != null) {
					// I found the recver, send it to the recver and stop
					if (ei.isActive() && ei.getChannel() != null && ei.getChannel().isActive()) {
						ei.getChannel().writeAndFlush(wm);
					}
					continue;
				} else {
					// I can't find the recver, I will send this message to
					// everyone I know
					for (EdgeInfo ei1 : emon.getOutboundEdges().map.values()) {
						// don't send message from sender back to sender
						if (ei1.getRef() == sender) {
							continue;
						}

						// send to all outgoing edges
						if (ei1.isActive() && ei1.getChannel() != null && ei1.getChannel().isActive()) {
							ei1.getChannel().writeAndFlush(wm);
						} else {
							if (ei != null) {
								emon.onAdd(ei1);
								ei1.getChannel().writeAndFlush(wm);
							}
						}
					}
				}
			} catch (Exception e) {
				logger.debug(e.getMessage());
			}

		}
	}
}
