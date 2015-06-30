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
 * For use with the thread pool manager. The trick is the thread is ALWAYS running,
 * but we occasionally assign a new Runnable target. After assigning the new target,
 * we interrupt the thread (which is sleeping). It then executes the new target's run()
 * method.
 */
public final class pmaThread extends Thread
{
	private boolean m_bIsRunning=false;
	private boolean m_bThreadActive=true;
	private long m_lastRunTimeMS=0;
	private long m_executionCount=0; //the number of times the thread has 'worked'
	private double m_totalExecutionTime=0;
	private pmaThreadInterface m_target=null;

	public pmaThread()
	{
		super();
	}

	public pmaThread(String sThreadName)
	{
		super(sThreadName);
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
	 * Ask the thread to die
	 */
	public void requestQuit()
	{
		m_bThreadActive = false;
	}


	/**
	 * Loads and Runs the thread...
	 * @return true if the target was assigned and run
	 * @return false if the target could not be executed
	 */
	public final synchronized boolean runThread(pmaThreadInterface paramtarget)
	{
		if(paramtarget==null) //no work to do!
		{
			m_lastRunTimeMS = 0;
			return false;
		}
		else
		{
			if(m_bIsRunning) //already doing work for someone else
			{
				//System.out.println(this.toString() + " -->> WORKING FOR SOMEONE ELSE");
				return false;
			}
			else
			{
				synchronized(this) //lock for extra safety
				{
					m_bIsRunning = true;
					m_target = paramtarget;
					this.interrupt();
				}
			}
		}
		return true;
	}


	/**
	 *
	 */
	public final void run()
	{
		long lStart, lEnd;
		while(m_bThreadActive)
		{
			if(m_target!=null)
			{
				m_executionCount++;
				//System.out.println(this.toString() + " start");
				m_bIsRunning = true;
				lStart = System.currentTimeMillis();
				m_target.run();
				m_target=null;
				lEnd = System.currentTimeMillis();
				m_lastRunTimeMS = lEnd - lStart;
				m_totalExecutionTime += m_lastRunTimeMS;
				m_bIsRunning = false;
				//System.out.println(this.toString() + " end. " + getLastRunTime() + "ms");
			}
			//sleep for a really long time. We will interrupt it if we have more work to do later...
			try{ sleep(99999); } catch(Exception e){ /*System.out.println(this.toString() + ": WAKE UP!"); */ }
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
		if(m_target==null) return "";
		return m_target.getThreadDetail();
	}

	public final void killThread()
	{
		//throw new RuntimeException(getThreadDetail() + " killed");
	}
}