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
 * Pool Manager now runs as a thread so that it will clean its own dead threads.
 */
public class pmaThreadPoolManager extends Thread implements ErrorDetect
{
	private int m_iMinThreads=10;
	private int m_iMaxThreads=100;
	private int m_iCurrentThreadCount=0;
	private SystemContext m_pSystem;
	private Vector<pmaThread> m_vThreads = new Vector<pmaThread>();
	private int m_iThreadWaitTimeMS=2000; //how long to wait before bail. set to -1 to wait forever.
	private boolean m_bShutdown=false;
	private long m_lThreadNum=1;
	private String m_sThreadPrefix="";


	public pmaThreadPoolManager(SystemContext paramSystem, int paramMinThreads, int paramMaxThreads, int paramTimeoutMS, String sPrefix)
	{
		m_sThreadPrefix = sPrefix;
		m_pSystem = paramSystem;
		m_iMinThreads = paramMinThreads;
		m_iMaxThreads = paramMaxThreads;
		m_iThreadWaitTimeMS = paramTimeoutMS;

		if(m_iMinThreads<=0) m_iMinThreads=1;
		if(m_iMaxThreads<=0) m_iMaxThreads=1;

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
		for(i=0; i<m_vThreads.size(); i++)
		{
			t = (pmaThread)m_vThreads.get(i);
			if(!t.isRunning() && t.isAlive()) return t;
		}
		//try to create a new thread
		//System.out.println("*** trying to create a new thread");
		if(m_iCurrentThreadCount<m_iMaxThreads)
		{
			return createThread();
		}

		//the pool must be full. try waiting for a thread to become free..
		//System.out.println("*** Pool is full - waiting");
		long ltime = System.currentTimeMillis();
		while(true)
		{
			for(i=0; i<m_vThreads.size(); i++)
			{
				t = (pmaThread)m_vThreads.get(i);
				if(!t.isRunning() && t.isAlive()) return t;
				//Thread.yield();
				//apparently .yield() can have unpredictable results across platforms
				try{Thread.sleep(1);}catch(Exception w){}
			}
			//try{Thread.sleep(100);}catch(Exception w){}

			// if we waited the whole time bail.
			if( ((System.currentTimeMillis() - ltime) > m_iThreadWaitTimeMS) && m_iThreadWaitTimeMS>=0) break;
		} //while

		m_pSystem.doError("pmaThreadPoolManager.NoFreeThreads", new String[]{String.valueOf(m_iThreadWaitTimeMS), String.valueOf(m_iCurrentThreadCount)}, this);
		return null;
	}


	public boolean runThread(pmaThreadInterface paramtarget)
	{
		pmaThread pt = getNextThread();
		if(pt==null || paramtarget==null) return false;
		return pt.runThread(paramtarget);
	}


	/**
	 *
	 */
	public void run()
	{
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "run()", this);

		int iSleepInterval = (20000);
		//continue until someone tells the thread to die
		while(!m_bShutdown)
		{      
			try{ Thread.sleep(iSleepInterval); } catch(Exception e){ }      
			if(!m_bShutdown) cleanPool();
		}

	}

	/**
	 * Ask the pool manager to stop
	 *
	 */
	public void requestQuit()
	{
		m_bShutdown = true;

		for(int i=0; i<m_vThreads.size(); i++)
		{
			pmaThread t = (pmaThread)m_vThreads.get(i);
			t.requestQuit();
			t.interrupt();
		}
	}

	/**
	 * Brutally kill a thread
	 */
	public void killThread(String sThreadID)
	{        
		for(int i=0; i<m_vThreads.size(); i++)
		{
			pmaThread t = (pmaThread)m_vThreads.get(i);
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
		StringBuilder sbOut = new StringBuilder(m_vThreads.size()*50);
		for(int i=0; i<m_vThreads.size(); i++)
		{
			pmaThread t = (pmaThread)m_vThreads.get(i);
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

		int i;
		for(i=0; i<m_vThreads.size(); i++)
		{
			pmaThread t = (pmaThread)m_vThreads.get(i);
			//if not alive AND not running
			if(!t.isAlive()) // && t.isRunning()) ) // ? <- look at
			{
				//pSystem.doDebug(0, "Removing dead thread " + t.getName(), this);
				m_vThreads.removeElementAt(i);
				m_iCurrentThreadCount--;
				t=null;
			}
		}

		if(m_bShutdown) return;
		
		//now boost pool back up to min threads
		for(i=m_iCurrentThreadCount; i<m_iMinThreads; i++)
		{   
			if(m_bShutdown) break;
			createThread();
			//pSystem.doDebug(0, "Creating thread count="+iCurrentThreadCount , this);
		}
	}

	/**
	 * Creates a new thread and adds it to the arraylist
	 */
	private pmaThread createThread()
	{
		m_iCurrentThreadCount++;
		pmaThread t = new pmaThread(m_sThreadPrefix+"-" + m_lThreadNum++);
		m_vThreads.add(t);
		t.start();
		//System.out.println("## NEW THREAD: " + t.toString());
		return t;
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
		pmaThread t;

		for(int i=0; i<m_vThreads.size(); i++)
		{
			t = (pmaThread)m_vThreads.get(i);
			if(t.isRunning()) iActive++;
		}
		return iActive;
	}

	/**
	 *
	 * @return
	 */
	public Vector getActiveObjects()
	{
		Vector vReturn= new Vector(m_vThreads.size());

		for(int i=0; i<m_vThreads.size(); i++)
		{
			pmaThread t = (pmaThread)m_vThreads.get(i);
			if(t.isRunning()) vReturn.add(t.getObject());
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

		for(int i=0; i<m_vThreads.size(); i++)
		{
			t = (pmaThread)m_vThreads.get(i);
			if(t.getExecutionCount()>0)
			{
				executionTotal = t.getAverageExecutionTime();
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