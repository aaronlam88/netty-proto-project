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
package gash.router.server.edges;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.container.RoutingConf.RoutingEntry;
import gash.router.server.ServerState;
import gash.router.server.WorkInit;
import gash.router.server.queue.CommandMessageQueue;
import gash.router.server.queue.WorkMessageQueue;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class EdgeMonitor implements EdgeListener, Runnable {
	protected static Logger logger = LoggerFactory.getLogger("edge monitor");

	private EventLoopGroup group;

	private EdgeList outboundEdges;
	private EdgeList inboundEdges;
	private long dt = 2000;
	private ServerState state;
	private boolean forever = true;

	public final WorkMessageQueue outgoing_work;
	public final CommandMessageQueue outgoing_command;

	public EdgeMonitor(ServerState state) {
		group = new NioEventLoopGroup();
		outgoing_work = WorkMessageQueue.getInstance();
		outgoing_command = CommandMessageQueue.getInstance();

		if (state == null)
			throw new RuntimeException("state is null");

		this.setOutboundEdges(new EdgeList());
		this.setInboundEdges(new EdgeList());
		this.setState(state);
		this.getState().setEmon(this);

		if (state.getConf().getRouting() != null) {
			for (RoutingEntry e : state.getConf().getRouting()) {
				getOutboundEdges().addNode(e.getId(), e.getHost(), e.getPort());
			}
		}

		// cannot go below 2 sec
		if (state.getConf().getHeartbeatDt() > this.dt)
			this.dt = state.getConf().getHeartbeatDt();

		try {
			state.setIP(InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			logger.error("cannot get my current host");
		}
	}

	public void createInboundIfNew(int ref, String host, int port) {
		getInboundEdges().createIfNew(ref, host, port);
	}

	public void createOutboundIfNew(int ref, String host, int port) {
		getOutboundEdges().createIfNew(ref, host, port);
	}

	public void shutdown() {
		setForever(false);
	}

	@Override
	public void run() {
		ElectionMonitor elmon = new ElectionMonitor(this);
		Thread elmonthread = new Thread(elmon);
		elmonthread.start();
		
		SendHeartBeat hb = new SendHeartBeat(this);
		Thread hbthread = new Thread(hb);
		hbthread.start();

		FowardWork fw = new FowardWork(this);
		Thread fwthread = new Thread(fw);
		fwthread.start();

		FowardCommand fc = new FowardCommand(this);
		Thread fcthread = new Thread(fc);
		fcthread.start();

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		PrintServer ps = new PrintServer(this);
		Thread psthread = new Thread(ps);
		executor.scheduleAtFixedRate(psthread, dt, 2 * dt, TimeUnit.MILLISECONDS);
		
		try {
			elmonthread.join();
			hbthread.join();
			fwthread.join();
			fcthread.join();
		} catch (Exception e) {
			logger.debug(e.getMessage());
		}
	}

	public void initConnect(EdgeInfo ei) {
		if (ei == null)
			return;
		try {
			WorkInit si = new WorkInit(getState(), false);
			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class).handler(si);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.option(ChannelOption.SO_KEEPALIVE, true);

			// Make the connection attempt.
			ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();

			// want to monitor the connection to the server s.t. if we loose the
			// connection, we can try to re-establish it.
			ei.setChannel(channel.channel());
			ei.setActive(channel.channel().isActive());
		} catch (Exception e) {
			// logger.error("cannot connect to node " + ei.getRef());
		}
	}

	@Override
	public synchronized void onAdd(EdgeInfo ei) {
		createInboundIfNew(ei.getRef(), ei.getHost(), ei.getPort());
		initConnect(ei);
	}

	@Override
	public synchronized void onRemove(EdgeInfo ei) {
		ei.setActive(false);
		ei.getChannel().close();
	}

	public EdgeList getOutboundEdges() {
		return outboundEdges;
	}

	public void setOutboundEdges(EdgeList outboundEdges) {
		this.outboundEdges = outboundEdges;
	}

	public EdgeList getInboundEdges() {
		return inboundEdges;
	}

	public void setInboundEdges(EdgeList inboundEdges) {
		this.inboundEdges = inboundEdges;
	}

	public boolean isForever() {
		return forever;
	}

	public void setForever(boolean forever) {
		this.forever = forever;
	}

	public ServerState getState() {
		return state;
	}

	public void setState(ServerState state) {
		this.state = state;
	}


	class PrintServer implements Runnable {
		private EdgeMonitor emon;
		// private Logger logger = LoggerFactory.getLogger("PrintServer");

		public PrintServer(EdgeMonitor emon) {
			super();
			this.emon = emon;
		}

		@Override
		public void run() {
			System.out.println("======================");
			System.out.println("node |    host    | port | status");
			for (EdgeInfo ei : emon.getOutboundEdges().map.values()) {
				if (ei.isActive() && ei.getChannel() != null && ei.getChannel().isActive()) {
					System.out.format("%5d %12s %5d : active\n", ei.getRef(), ei.getHost(), ei.getPort());
				} else {
					System.out.format("%5d %12s %5d : inactive\n", ei.getRef(), ei.getHost(), ei.getPort());
					// emon.onRemove(ei);
				}
			}
			System.out.println("the leader is: " + getState().getLeaderID());
			System.out.println("======================");

		}

	}
}
