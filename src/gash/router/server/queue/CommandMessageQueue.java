package gash.router.server.queue;

import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import routing.Pipe.CommandMessage;

public class CommandMessageQueue {
	protected static Logger logger = LoggerFactory.getLogger("workQueue");
	private static CommandMessageQueue uniqueInstance;

	private LinkedBlockingQueue<CommandMessage> commandQueue;

	private CommandMessageQueue () {
		commandQueue = new LinkedBlockingQueue<>();
	}

	public static CommandMessageQueue getInstance() {
		if (uniqueInstance == null) {
			uniqueInstance = new CommandMessageQueue();
		}
		return uniqueInstance;
	}

	public LinkedBlockingQueue<CommandMessage> getCommandMessageQueue() {
		return commandQueue;
	}

	public void setCommandMessageQueue(LinkedBlockingQueue<CommandMessage> commandQueue) {
		this.commandQueue = commandQueue;
	}
	
	
	public void offer(CommandMessage cm) {
		try {
			if(commandQueue.offer(cm)) {
				logger.debug("message offer success");
			} else {
				logger.error("message offer fail");
			}
			
		} catch (NullPointerException e) {
			logger.error("NullPointerException");
		}
	}
	
	public CommandMessage take() {
		try {
			return commandQueue.take();
		} catch (InterruptedException e) {
			logger.error("InterruptedException");
			return null;
		}
	}
}
