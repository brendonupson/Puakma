/** ***************************************************************
pmaThreadPoolManager.java
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

import java.util.Vector;

import puakma.error.ErrorDetect;
import puakma.error.pmaLog;


/**
 * The ThreadPoolManager manages a bunch of pmaThreads. When the pool is created, x
 * threads are spawned, which immediately go to sleep. When a request is made, either:
 * 1. A thread is assigned a Runnable target and interrupted
 * 2. A new thread is created (assigned, then interrupted)
 * 3. We wait, then try 2.
 *
 * Pool Manager now runs as a thread so that it will clean its own dead threads, and
 * retire threads above the minimum that have been idle for a while.
 */
public class pmaThreadPoolManager extends Thread implements ErrorDetect
{
	private int m_iMinThreads=10;
	private int m_iMaxThreads=100;
	private volatile int m_iCurrentThreadCount=0;
	private SystemContext m_pSystem;
	private Vector<pmaThread> m_vThreads = new Vector<pmaThread>();
	private int m_iThreadWaitTimeMS=2000; //how long to wait before bail. set to -1 to wait forever.
	private volatile boolean m_bShutdown=false;
	private long m_lThreadNum=1;
	private String m_sThreadPrefix="";
	private long m_lIdleTimeoutMS=DEFAULT_IDLE_TIMEOUT_MS; //retire threads above min idle this long. <=0 never retires

	public static final long DEFAULT_IDLE_TIMEOUT_MS=60000;
	private static final int CLEAN_INTERVAL_MS=20000;


	public pmaThreadPoolManager(SystemContext paramSystem, int paramMinThreads, int paramMaxThreads, int paramTimeoutMS, String sPrefix)
	{
		this(paramSystem, paramMinThreads, paramMaxThreads, paramTimeoutMS, sPrefix, DEFAULT_IDLE_TIMEOUT_MS);
	}

	public pmaThreadPoolManager(SystemContext paramSystem, int paramMinThreads, int paramMaxThreads, int paramTimeoutMS, String sPrefix, long lIdleTimeoutMS)
	{
		setName(sPrefix + "-mgr");
		m_sThreadPrefix = sPrefix;
		m_lIdleTimeoutMS = lIdleTimeoutMS;
		m_pSystem = paramSystem;
		m_iMinThreads = paramMinThreads;
		m_iMaxThreads = paramMaxThreads;
		m_iThreadWaitTimeMS = paramTimeoutMS;

		if(m_iMinThreads<=0) m_iMinThreads=1;
		if(m_iMaxThreads<=0) m_iMaxThreads=1;
		if(m_iMinThreads>m_iMaxThreads) m_iMinThreads=m_iMaxThreads;

		//create a bunch of threads that are ready to go
		for(int i=0; i<m_iMinThreads; i++)
		{
			createThread();
		}
	}

	/**
	 * Gets a 'free' thread.
	 * @return null if a thread is not available
	 */
	public synchronized pmaThread getNextThread()
	{
		pmaThread t=null;
		int i;

		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_VERBOSE, "getNextThread()", this);
		if(m_bShutdown) return null; //system is shutting down - don't allocate any new threads!

		//try to find an existing thread that has already run or is new
		//System.out.println("*** trying to find an existing thread");
		int iThreadCount = m_vThreads.size(); 
		for(i=0; i<iThreadCount; i++)
		{
			try {
				t = (pmaThread)m_vThreads.get(i);
				if(isFree(t)) return t;
			}catch(ArrayIndexOutOfBoundsException e) { System.err.println("getNextThread(): " + e.toString());}
		}
		//try to create a new thread
		//System.out.println("*** trying to create a new thread");
		if(m_iCurrentThreadCount<m_iMaxThreads)
		{
			t = createThread();
			if(t!=null) return t;
		}

		//the pool must be full. try waiting for a thread to become free..
		//System.out.println("*** Pool is full - waiting");
		long ltime = System.currentTimeMillis();
		while(!m_bShutdown)
		{		
			iThreadCount = m_vThreads.size();
			for(i=0; i<iThreadCount; i++)
			{
				try {
					t = (pmaThread)m_vThreads.get(i);
					if(isFree(t)) return t;
				}catch(ArrayIndexOutOfBoundsException e) { System.err.println("getNextThread() 2: " + e.toString());}
				//Thread.yield();
				//apparently .yield() can have unpredictable results across platforms
				//try{Thread.sleep(1);}catch(Exception w){}
			}
			//wait for a worker to finish (threadFinished() notifies) rather than polling.
			//The timed wait is kept as a safety net in case a notify is missed.
			long lWait = 200;
			if(m_iThreadWaitTimeMS>=0)
			{
				long lRemaining = m_iThreadWaitTimeMS - (System.currentTimeMillis() - ltime);
				if(lRemaining<=0) break;
				if(lRemaining<lWait) lWait = lRemaining;
			}
			try{ wait(lWait); }catch(InterruptedException w){}
		} //while

		if(m_bShutdown) return null;
		m_pSystem.doError("pmaThreadPoolManager.NoFreeThreads", new String[]{String.valueOf(m_iThreadWaitTimeMS), String.valueOf(m_iCurrentThreadCount)}, this);
		return null;
	}


	/**
	 * A thread can be handed out if it is idle, alive and not on its way out
	 */
	private static boolean isFree(pmaThread t)
	{
		return !t.isRunning() && t.isActive() && t.isAlive();
	}

	/**
	 * Called by a pmaThread when it finishes its work, to wake a caller waiting in getNextThread()
	 */
	public synchronized void threadFinished()
	{
		notifyAll();
	}

	/**
	 * Called by a pmaThread when its target throws. The worker carries on.
	 */
	public void threadError(pmaThread t, Throwable e)
	{
		m_pSystem.doError(t.getName() + " uncaught " + e.toString(), this);
		e.printStackTrace();
	}

	public boolean runThread(pmaThreadInterface paramtarget)
	{
		if(paramtarget==null) return false;
		//getNextThread() releases the lock before the target is assigned, so the thread may
		//have been taken or retired in between. Try once more before giving up.
		for(int i=0; i<2; i++)
		{
			pmaThread pt = getNextThread();
			if(pt==null) return false;
			if(pt.runThread(paramtarget)) return true;
		}
		return false;
	}


	/**
	 *
	 */
	public void run()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "run()", this);

		//continue until someone tells the thread to die
		while(!m_bShutdown)
		{      
			try{ Thread.sleep(CLEAN_INTERVAL_MS); } catch(InterruptedException e){ }      
			if(m_bShutdown) break;
			//an escaping Throwable (eg OOME creating a native thread) would kill this thread and
			//silently stop all cleaning
			try{ cleanPool(); }
			catch(Throwable t){ m_pSystem.doError("cleanPool() failed: " + t.toString(), this); }
		}

	}

	/**
	 * Ask the pool manager to stop
	 *
	 */
	public void requestQuit()
	{
		m_bShutdown = true;

		pmaThread[] threads = snapshot();
		for(int i=0; i<threads.length; i++)
		{
			pmaThread t = threads[i];
			t.requestQuit();
			t.interrupt();
		}
		this.interrupt(); //stop the housekeeping sleep
		threadFinished(); //wake anyone waiting in getNextThread()
	}

	/**
	 * Brutally kill a thread
	 */
	public void killThread(String sThreadID)
	{     
		pmaThread[] threads = snapshot();
		for(int i=0; i<threads.length; i++)
		{
			pmaThread t = threads[i];
			//System.out.println("Checking: "+t.getName());
			if(t.isAlive() && t.getName().equals(sThreadID)) 
			{
				//t.destroy();
				t.requestQuit();
				t.interrupt();
				m_pSystem.doInformation(sThreadID + " killed!", this);
				// let the cleanup thread remove the dead thread
			}
		}
	}

	/**
	 * get a list of active threads
	 */
	public String getThreadDetail()
	{        		
		pmaThread[] threads = snapshot();
		StringBuilder sbOut = new StringBuilder(threads.length*50);
		for(int i=0; i<threads.length; i++)
		{
			pmaThread t = threads[i];
			//if not alive AND not running
			if(t.isAlive() && t.isRunning()) 
			{
				sbOut.append(t.getName());
				sbOut.append(' ');
				sbOut.append(t.getThreadDetail());
				sbOut.append("\r\n");
			}
		}
		return sbOut.toString();
	}

	/*
	 * Remove all dead threads from the pool, housekeeping  
	 */
	public synchronized void cleanPool()
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_VERBOSE, "cleanPool()", this);
		//pSystem.doDebug(0, "Cleaning Pool.... " + iCurrentThreadCount + " threads "+getActiveThreadCount() + " active", this);

		for(int i = m_vThreads.size() - 1; i >= 0; i--)
		{
			pmaThread t = (pmaThread)m_vThreads.get(i);
			//if not alive AND not running
			if(!t.isAlive()) // && t.isRunning()) ) // ? <- look at
			{
				//pSystem.doDebug(0, "Removing dead thread " + t.getName(), this);
				m_vThreads.removeElementAt(i);
				m_iCurrentThreadCount--;
			}
		}

		if(m_bShutdown) return;

		//retire threads above __min__ that have been idle a while. Work from the tail:
		//getNextThread() scans from the head, so idle threads collect at the end.
		//Retired threads exit on their own. Drop them from the pool now, otherwise they would
		//count against max (and block new threads being created) until the next pass.
		if(m_lIdleTimeoutMS>0)
		{
			int iActive = 0;
			for(int i=0; i<m_vThreads.size(); i++)
			{
				if(((pmaThread)m_vThreads.get(i)).isActive()) iActive++;
			}
			for(int i = m_vThreads.size() - 1; i >= 0 && iActive > m_iMinThreads; i--)
			{
				if(((pmaThread)m_vThreads.get(i)).tryRetire(m_lIdleTimeoutMS))
				{
					m_vThreads.removeElementAt(i);
					m_iCurrentThreadCount--;
					iActive--;
				}
			}
		}

		//now boost pool back up to __min__ threads
		for(int i = m_iCurrentThreadCount; i < m_iMinThreads; i++)
		{
			if(m_bShutdown) break;
			if(createThread()==null) break;
			//pSystem.doDebug(0, "Creating thread count="+iCurrentThreadCount , this);
		}
	}

	/**
	 * Creates a new thread and adds it to the arraylist
	 * @return null if the thread could not be started (eg the OS is out of native threads)
	 */
	private synchronized pmaThread createThread()
	{
		pmaThread t = new pmaThread(m_sThreadPrefix+"-" + m_lThreadNum++, this);
		try
		{
			t.start();
		}
		catch(Throwable e)
		{
			m_pSystem.doError("Unable to start pool thread " + t.getName() + ": " + e.toString(), this);
			return null;
		}
		m_iCurrentThreadCount++;
		m_vThreads.add(t);
		//System.out.println("## NEW THREAD: " + t.toString());
		return t;
	}

	/**
	 * A copy of the pool, safe to iterate without holding the lock
	 */
	private pmaThread[] snapshot()
	{
		return m_vThreads.toArray(new pmaThread[0]);
	}


	/**
	 * Stats, how many threads are in the system
	 */
	public int getThreadCount()
	{
		return m_iCurrentThreadCount;
	}

	/**
	 * Determine if the pool manager has some free threads that can do some work.
	 */
	public boolean hasAvailableThreads()
	{
		int iActive = getActiveThreadCount();
		if(iActive<m_iMaxThreads) return true;

		return false;
	}


	/**
	 * Returns the number of 'running' threads in the system
	 */
	public int getActiveThreadCount()
	{
		int iActive=0;		 

		pmaThread[] threads = snapshot();
		for(int i=0; i<threads.length; i++)
		{
			if(threads[i].isRunning()) iActive++;
		}
		return iActive;
	}

	/**
	 *
	 * @return
	 */
	public Vector getActiveObjects()
	{
		pmaThread[] threads = snapshot();
		Vector vReturn= new Vector(threads.length);

		for(int i=0; i<threads.length; i++)
		{
			pmaThread t = threads[i];
			Object obj = t.getObject();
			if(t.isRunning() && obj!=null) vReturn.add(obj);
		}
		return vReturn;
	}

	/**
	 * Stats, what's the maximum threads that can run
	 */
	public int getThreadMax()
	{
		return this.m_iMaxThreads;
	}

	/**
	 * Stats, what's the minimum threads that can run
	 */
	public int getThreadMin()
	{
		return this.m_iMinThreads;
	}


	/**
	 * Stats, get the average number of milliseconds a thread in this pool
	 * has run for
	 */
	public double getAverageExecutionTime()
	{
		pmaThread t;
		long threadCount=0;
		double executionTotal=0;

		pmaThread[] threads = snapshot();
		for(int i=0; i<threads.length; i++)
		{
			t = threads[i];
			if(t.getExecutionCount()>0)
			{
				executionTotal += t.getAverageExecutionTime();
				threadCount++;
			}
		}

		if(threadCount==0 || executionTotal==0) return 0;
		return executionTotal/threadCount;
	}

	/**
	 * Interrupt all running threads in the pool and stop allocating new threads
	 * note: does not KILL running threads. You should repeatedly call  getActiveThreadCount()
	 * and destroy() to continue interrupting running threads.
	 */
	//was destroy(), renamed due to deprecation.... does not seem to be used.
	/*public synchronized void emptyPool()
  {
    pmaThread t;
    pSystem.doDebug(pmaLog.DEBUGLEVEL_VERBOSE, "emptyPool()", this);

    bShutdown = true;
    for(int i=0; i<vThreads.size(); i++)
    {
      t = (pmaThread)vThreads.get(i);
      //t.destroy(); //flag it to die
      t.interrupt();
      try{ Thread.sleep(100); }catch(Exception r){}
      if(!t.isRunning() || !t.isAlive()) //drop it from the pool
      {
        vThreads.remove(i);
        i=0;
        t = null;
        //try{ Thread.sleep(100); }catch(Exception r){}
      }
    }
  }*/


	/**
	 *
	 */
	public String getErrorSource()
	{
		return "pmaThreadPoolManager";
	}

	/**
	 *
	 */
	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}

}