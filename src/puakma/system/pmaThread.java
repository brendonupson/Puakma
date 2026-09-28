/** ***************************************************************
pmaThread.java
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
package puakma.system;

/**
 * For use with the thread pool manager. The thread is ALWAYS running, but we
 * occasionally assign a new target. Assigning a target wakes the thread (it waits on
 * a private monitor) and it then executes the target's run() method.
 *
 * interrupt() is deliberately NOT used to hand over work: a worker could pick up the
 * new target before the interrupt arrived, and the interrupt would then land inside the
 * request (breaking sleep()/wait() or interrupt aware drivers). interrupt() is only used
 * by killThread()/shutdown to break a running target.
 */
public final class pmaThread extends Thread
{
	//volatile: read by the pool manager's thread to find a free worker
	private volatile boolean m_bIsRunning=false;
	private volatile boolean m_bThreadActive=true;
	private volatile long m_lastRunTimeMS=0;
	private volatile long m_executionCount=0; //the number of times the thread has 'worked'
	private volatile double m_totalExecutionTime=0;
	private volatile long m_lIdleSinceMS=System.currentTimeMillis();
	private volatile pmaThreadInterface m_target=null;
	private pmaThreadPoolManager m_manager=null; //told when this thread becomes free
	//guards the hand over of m_target. Not the Thread's own monitor, join() uses that
	private final Object m_lock = new Object();

	public pmaThread()
	{
		super();
	}

	public pmaThread(String sThreadName)
	{
		super(sThreadName);
	}

	public pmaThread(String sThreadName, pmaThreadPoolManager manager)
	{
		super(sThreadName);
		m_manager = manager;
	}

	public Object getObject()
	{
		return m_target;
	}

	/**
	 * Determines if the current thread is executing
	 */
	public boolean isRunning()
	{
		return m_bIsRunning;
	}


	/**
	 * Determines if the current thread is capable of continuing
	 */
	public boolean isActive()
	{
		return m_bThreadActive;
	}

	/**
	 * Ask the thread to die. A target that is already assigned is still run.
	 */
	public void requestQuit()
	{
		synchronized(m_lock)
		{
			m_bThreadActive = false;
			m_lock.notifyAll();
		}
	}

	/**
	 * Ask an idle thread to die, if it has been idle for at least lIdleMS.
	 * The check and the quit are done under the same lock as runThread(), so a thread
	 * cannot be retired after it has been given work.
	 * @return true if the thread will now exit
	 */
	public boolean tryRetire(long lIdleMS)
	{
		synchronized(m_lock)
		{
			if(!m_bThreadActive || m_bIsRunning || m_target!=null) return false;
			if(System.currentTimeMillis() - m_lIdleSinceMS < lIdleMS) return false;
			m_bThreadActive = false;
			m_lock.notifyAll();
			return true;
		}
	}


	/**
	 * Loads and Runs the thread...
	 * @return true if the target was assigned and will be run
	 * @return false if the target could not be executed
	 */
	public final boolean runThread(pmaThreadInterface paramtarget)
	{
		if(paramtarget==null) return false; //no work to do!

		synchronized(m_lock)
		{
			//already doing work for someone else, or on its way out
			if(m_bIsRunning || !m_bThreadActive) return false;

			m_bIsRunning = true;
			m_target = paramtarget;
			m_lock.notifyAll();
		}
		return true;
	}


	/**
	 *
	 */
	public final void run()
	{
		long lStart;
		while(true)
		{
			pmaThreadInterface target;
			synchronized(m_lock)
			{
				while(m_target==null && m_bThreadActive)
				{
					try{ m_lock.wait(); } catch(InterruptedException e){ }
				}
				if(m_target==null) break; //asked to quit and no work pending
				target = m_target;
			}

			Thread.interrupted(); //don't let a stale interrupt leak into this request
			m_executionCount++;
			lStart = System.currentTimeMillis();
			try
			{
				target.run();
			}
			catch(Throwable t)
			{
				//keep the worker alive: a dead worker would leak its slot until the pool is cleaned
				try
				{
					if(m_manager!=null) 
						m_manager.threadError(this, t);
					else
						t.printStackTrace();
				}
				catch(Throwable e){ } //logging failed (eg OOME), still keep the worker
			}
			finally
			{
				long lEnd = System.currentTimeMillis();
				m_lastRunTimeMS = lEnd - lStart;
				m_totalExecutionTime += m_lastRunTimeMS;
				synchronized(m_lock)
				{
					m_target = null;
					m_lIdleSinceMS = lEnd;
					m_bIsRunning = false;
				}
				if(m_manager!=null) m_manager.threadFinished();
			}
		}
	}


	/**
	 * This will set a thread to die, and interrupt it so it can die.
	 */
	/*public final void destroy()
  {
    bThreadActive = false;
    this.interrupt();
    //wait a second to see if it dies naturally
    try{ sleep(1000); }catch(Exception e){}
    if(target!=null && target instanceof pmaThreadInterface) target.destroy();

  }*/

	/**
	 * Return the length of time the last thread ran for
	 */
	public long getLastRunTime()
	{
		return m_lastRunTimeMS;
	}

	/**
	 * Return the number of times this thread has had work to do
	 */
	public long getExecutionCount()
	{
		return m_executionCount;
	}

	/**
	 * Return the length of time the last thread ran for
	 */
	public double getAverageExecutionTime()
	{
		double result;
		if(m_totalExecutionTime==0 || m_executionCount==0) return 0;

		result = m_totalExecutionTime/m_executionCount;
		return result;
	}


	public String getThreadDetail()
	{
		pmaThreadInterface target = m_target;
		if(target==null) return "";
		return target.getThreadDetail();
	}

	public final void killThread()
	{
		//throw new RuntimeException(getThreadDetail() + " killed");
	}
}
