package gash.router.server.queue;

import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pipe.work.Work.WorkMessage;

public class WorkMessageQueue {
	protected static Logger logger = LoggerFactory.getLogger("workQueue");
	private static WorkMessageQueue uniqueInstance;

	private LinkedBlockingQueue<WorkMessage> workQueue;

	private WorkMessageQueue () {
		workQueue = new LinkedBlockingQueue<>();
	}

	public static WorkMessageQueue getInstance() {
		if (uniqueInstance == null) {
			uniqueInstance = new WorkMessageQueue();
		}
		return uniqueInstance;
	}

	public LinkedBlockingQueue<WorkMessage> getWorkMessageQueue() {
		return workQueue;
	}

	public void setWorkMessageQueue(LinkedBlockingQueue<WorkMessage> workQueue) {
		this.workQueue = workQueue;
	}
	
	
	public void offer(WorkMessage wm) {
		try {
			if(workQueue.offer(wm)) {
				logger.debug("message offer success");
			} else {
				logger.error("message offer fail");
			}
			
		} catch (Exception e) {
			logger.error("Exception " + e.getMessage());
		}
	}

	
	public WorkMessage take() {
		try {
			return workQueue.take();
		} catch (Exception e) {
			logger.error("Exception " + e.getMessage());
			return null;
		}
	}
}
