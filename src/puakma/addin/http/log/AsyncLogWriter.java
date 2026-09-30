/** ***************************************************************
HTTPLogger.java
Copyright (C) 2001  Brendon Upson 
http://www.wnc.net.au info@wnc.net.au

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

 *************************************************************** */

package puakma.addin.http.log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Runs logging work on a single background daemon thread so that file and database 
 * I/O never happens on the HTTP request path. Tasks are queued in a bounded queue; if the 
 * queue is full (the log target is slower than the request rate) the task is dropped rather 
 * than blocking the request thread. Tasks run one at a time in the order submitted.
 */
public class AsyncLogWriter implements Runnable
{
	public static final int DEFAULT_CAPACITY = 10000;
	private static final long REPORT_INTERVAL_MS = 60000;

	private final ArrayBlockingQueue<Runnable> m_queue;
	private final Thread m_thread;
	private final AtomicLong m_lDropped = new AtomicLong(0);
	private volatile boolean m_bRunning = true;
	private volatile Runnable m_idleHook = null;
	private volatile Runnable m_closeHook = null;
	private volatile Consumer<String> m_reporter = null;
	private long m_lLastReportedDropped = 0;
	private long m_lLastReportTime = 0;

	public AsyncLogWriter(String sThreadName, int iCapacity)
	{
		m_queue = new ArrayBlockingQueue<Runnable>(iCapacity>0 ? iCapacity : DEFAULT_CAPACITY);
		m_thread = new Thread(this, sThreadName);
		m_thread.setDaemon(true);
		m_thread.start();
	}

	/**
	 * Called on the writer thread whenever the queue has just been emptied, eg to flush buffered output.
	 */
	public void setIdleHook(Runnable r)
	{
		m_idleHook = r;
	}

	/**
	 * Called on the writer thread once, after the queue has been drained at shutdown, eg to close files.
	 */
	public void setCloseHook(Runnable r)
	{
		m_closeHook = r;
	}

	/**
	 * Receives a message (on the writer thread, at most once a minute) when entries have been dropped 
	 * because the queue was full, or a task threw an unexpected exception.
	 */
	public void setReporter(Consumer<String> reporter)
	{
		m_reporter = reporter;
	}

	/**
	 * Queue a task without blocking. 
	 * @return false if the task was dropped (queue full or writer stopped)
	 */
	public boolean submit(Runnable task)
	{
		if(task==null || !m_bRunning) return false;
		if(m_queue.offer(task)) return true;
		m_lDropped.incrementAndGet();
		return false;
	}

	public long getDroppedCount()
	{
		return m_lDropped.get();
	}

	public int getQueueSize()
	{
		return m_queue.size();
	}

	public void run()
	{
		while(m_bRunning)
		{
			try
			{
				Runnable task = m_queue.take();
				runTask(task);
				if(m_queue.isEmpty()) runHook(m_idleHook);
				reportDropped();
			}
			catch(InterruptedException e)
			{
				//shutdown() was called
			}
		}

		Thread.interrupted(); //clear the flag so the drain below isn't cut short
		Runnable task;
		while((task=m_queue.poll())!=null) runTask(task);
		runHook(m_idleHook);
		runHook(m_closeHook);
		reportDropped();
	}

	private void runTask(Runnable task)
	{
		try
		{
			task.run();
		}
		catch(Throwable t)
		{
			report("Log task failed: " + t);
		}
	}

	private void runHook(Runnable hook)
	{
		if(hook!=null) runTask(hook);
	}

	private void reportDropped()
	{
		long lDropped = m_lDropped.get();
		if(lDropped==m_lLastReportedDropped) return;
		long lNow = System.currentTimeMillis();
		if(lNow-m_lLastReportTime<REPORT_INTERVAL_MS && m_bRunning) return;
		report((lDropped-m_lLastReportedDropped) + " log entries dropped, the log queue was full");
		m_lLastReportedDropped = lDropped;
		m_lLastReportTime = lNow;
	}

	private void report(String sMessage)
	{
		Consumer<String> reporter = m_reporter;
		if(reporter==null) return;
		try{ reporter.accept(sMessage); }catch(Throwable t){}
	}

	/**
	 * Stop accepting work, write out whatever is queued and close. Waits up to lWaitMS for this to finish.
	 */
	public void shutdown(long lWaitMS)
	{
		m_bRunning = false;
		m_thread.interrupt();
		try{ m_thread.join(lWaitMS); }catch(InterruptedException e){ Thread.currentThread().interrupt(); }
	}
}
