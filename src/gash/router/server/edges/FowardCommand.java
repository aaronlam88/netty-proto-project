package gash.router.server.edges;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.server.ServerState;
import gash.router.server.queue.CommandMessageQueue;
import gash.router.server.queue.WorkMessageQueue;
import pipe.common.Common.Header;
import pipe.work.Work.WorkMessage;
import routing.Pipe.CommandMessage;

public class FowardCommand implements Runnable {
	private EdgeMonitor emon;
	private Logger logger = LoggerFactory.getLogger("FowardCommand");

	private ServerState state;
	public final WorkMessageQueue outgoing_work;
	public final CommandMessageQueue outgoing_command;

	public FowardCommand(EdgeMonitor emon) {
		super();
		this.emon = emon;

		outgoing_work = emon.outgoing_work;
		outgoing_command = emon.outgoing_command;

		state = emon.getState();
	}

	@Override
	public void run() {
		while (emon.isForever()) {
			try {
				CommandMessage cm = emon.outgoing_command.take();
				Header header = cm.getHeader();
				int sender = header.getNodeId();
				int recver = header.getDestination();

				// I shouldn't send message to myself
				if (recver == state.getConf().getNodeId()) {
					continue;
				}
				// I shouldn't return the message to the sender
				if (sender == recver) {
					continue;
				}

				// rebuild message to pass on to other node
				// convert command message to work message
				Header.Builder hb = Header.newBuilder();
				hb.mergeFrom(header);

				WorkMessage.Builder wb = WorkMessage.newBuilder();
				wb.setErr(cm.getErr());
				wb.setPing(cm.getPing());
				wb.setMessage(cm.getMessage());
				wb.setSecret(ServerState.getSecret());

				EdgeInfo ei = emon.getOutboundEdges().map.get(recver);
				if (ei != null) {
					// I found the recver, send it to the recver and stop
					if (ei.isActive() && ei.getChannel() != null && ei.getChannel().isActive()) {
						hb.setDestination(ei.getRef());
						wb.setHeader(hb.build());
						ei.getChannel().writeAndFlush(wb.build());
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
							wb.setHeader(hb.build());
							ei1.getChannel().writeAndFlush(wb.build());
						} else {
							if (ei != null) {
								emon.onAdd(ei1);
								wb.setHeader(hb.build());
								ei1.getChannel().writeAndFlush(wb.build());
							}
						}
						logger.info("forwarding command to " + wb.getHeader().getDestination());
					}
				}
			} catch (Exception e) {
				logger.debug(e.getMessage());
			}
		}
	}
}