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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.container.RoutingConf;
import gash.router.server.queue.CommandMessageQueue;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import routing.Pipe.CommandMessage;

/**
 * The message handler processes json messages that are delimited by a 'newline'
 * 
 * TODO replace println with logging!
 * 
 * @author gash
 * 
 */
public class CommandHandler extends SimpleChannelInboundHandler<CommandMessage> {
	protected static Logger logger = LoggerFactory.getLogger("CommandHandler");
	protected RoutingConf conf;
	protected boolean debug = false;
	protected CommandMessageQueue fowardQueue;

	public CommandHandler(RoutingConf conf) {
		if (conf != null) {
			this.conf = conf;
		}
		fowardQueue = CommandMessageQueue.getInstance();
	}

	/**
	 * override this method to provide processing behavior. This implementation
	 * mimics the routing we see in annotating classes to support a RESTful-like
	 * behavior (e.g., jax-rs).
	 * 
	 * @param msg
	 */
	public void handleMessage(CommandMessage msg, Channel channel) {
		if (msg == null) {
			logger.error("Unexpected content - " + msg);
			return;
		}
		if (debug)
			PrintUtil.printCommand(msg);

		try {
			if (msg.hasPing()) {
				logger.info("ping from " + msg.getHeader().getNodeId());
				
				Header.Builder hd = Header.newBuilder();
				hd.setDestination(msg.getHeader().getNodeId());
				hd.setNodeId(conf.getNodeId());
				hd.setTime(System.currentTimeMillis());

				CommandMessage.Builder rb = CommandMessage.newBuilder();
				rb.setHeader(hd);

				rb.setPing(true);
				rb.setMessage("ping back from: " + conf.getNodeId());
				channel.writeAndFlush(rb.build());
				
			} else if (msg.hasMessage()) {
				logger.info(msg.getMessage());
			} else {
			}

		} catch (Exception e) {
			logger.debug("Exception - " + e.getMessage());
			Failure.Builder eb = Failure.newBuilder();
			eb.setId(conf.getNodeId());
			eb.setRefId(msg.getHeader().getNodeId());
			eb.setMessage(e.getMessage());
			CommandMessage.Builder rb = CommandMessage.newBuilder(msg);
			rb.setErr(eb);
			channel.write(rb.build());
		}

		System.out.flush();
	}

	/**
	 * check to see if we should discard WorkMessage msg
	 * 
	 * @param msg
	 * @return true: we don't need to care about this msg, discard it (return)
	 *         false: we have to read this msg or forward it.
	 */
	protected boolean shouldDiscard(CommandMessage msg) {
		Header header = msg.getHeader();
		int maxHop = header.getMaxHops();
		int src = header.getNodeId();
		long time = header.getTime();

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
		if (src == conf.getNodeId()) {
			return true;
		}

		// the above cases should cover all the problems
		return false;
	}

	/**
	 * rebuild msg so it can be forward to other node, namely --maxHop
	 * 
	 * @param msg
	 * @return WorkMessage with new maxHop = old maxHop - 1
	 */
	protected CommandMessage rebuildMessage(CommandMessage msg) {
		Header header = msg.getHeader();
		int maxHop = header.getMaxHops();
		--maxHop;
		// build new header from old header, only update maxHop
		Header.Builder hb = Header.newBuilder();
		hb.mergeFrom(header);
		hb.setMaxHops(maxHop);

		// build new msg from old msg, only update Header
		CommandMessage.Builder cb = CommandMessage.newBuilder();
		cb.mergeFrom(msg);
		cb.setHeader(hb.build());
		return cb.build();
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
	protected void channelRead0(ChannelHandlerContext ctx, CommandMessage msg) throws Exception {
		// check if I should discard this msg or not
		if (shouldDiscard(msg)) {
			logger.info("discard msg");
			return;
		}
		
		logger.info(msg.toString());

		int dest = msg.getHeader().getDestination();
		if (dest == -1) {
			// this is broadcast message, put it in forward queue and read it
			// too
			logger.debug("broadcast from " + msg.getHeader().getNodeId());
			handleMessage(msg, ctx.channel());
			// rebuild this msg for broadcast
			msg = rebuildMessage(msg);
			fowardQueue.offer(msg);
		} else if (dest == conf.getNodeId()) {
			// this message is for me, I should read it
			handleMessage(msg, ctx.channel());
		} else {
			// this is private message for other, put it in forward queue
			// rebuild this msg for forward
			msg = rebuildMessage(msg);
			fowardQueue.offer(msg);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

}