package puakma.addin.stats;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import puakma.addin.AddInStatistic;
import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.server.AddInMessage;

public class STATS extends pmaAddIn  
{
	private static final long ONE_MILLION = 1000000;
	private static final String STATISTIC_KEY_CPUPERMINUTE = "stats.cpuusageperminute";
	private static final String STATISTIC_KEY_MEMORYJVMINUSEPERMINUTE = "stats.memoryjvmmbinuseperminute";
	private static final String STATISTIC_KEY_MEMORYAPPINUSEPERMINUTE = "stats.memoryappmbinuseperminute";
	private static final String STATISTIC_KEY_THREADCOUNTPERMINUTE =  "stats.threadcountperminute";
	private static final String STATISTIC_KEY_ERRORSPERHOUR =  "stats.errorsperhour";
	private static final String STATISTIC_KEY_LOGINSPERHOUR = "stats.loginsperhour";
	private pmaAddInStatusLine m_pStatus;

	private long m_lAddInTimeNanoSeconds = System.currentTimeMillis() * ONE_MILLION;
	private long m_lLastCPUTime = 0;
	//private long m_lLastUserTime = 0;
	private long m_lLastErrorCount = -1;


	public void pmaAddInMain()
	{
		//if(m_pSystem.isAddInLoaded(this.getClass().getName())) return; //don't load it twice!

		setAddInName("STATS");
		m_pStatus = createStatusLine();
		m_pStatus.setStatus("Starting...");
		m_pSystem.doInformation("STATS.Startup", this);

		createStatistic(STATISTIC_KEY_CPUPERMINUTE, AddInStatistic.STAT_CAPTURE_PER_MINUTE, -1, true);
		createStatistic(STATISTIC_KEY_THREADCOUNTPERMINUTE, AddInStatistic.STAT_CAPTURE_PER_MINUTE, -1, true);
		createStatistic(STATISTIC_KEY_MEMORYJVMINUSEPERMINUTE, AddInStatistic.STAT_CAPTURE_PER_MINUTE, -1, true);
		createStatistic(STATISTIC_KEY_MEMORYAPPINUSEPERMINUTE, AddInStatistic.STAT_CAPTURE_PER_MINUTE, -1, true);
		createStatistic(STATISTIC_KEY_ERRORSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		createStatistic(STATISTIC_KEY_LOGINSPERHOUR, AddInStatistic.STAT_CAPTURE_PER_HOUR, -1, true);
		/*
		String szTemp = m_pSystem.getSystemProperty("WIDGIEAutoReload");
		if(szTemp!=null && szTemp.equals("1")) m_bAutoReload = true;
		 */

		// main loop
		updateCPUStats();
		updateMemoryStats();
		updateErrors();
		while (!addInShouldQuit())
		{
			try{Thread.sleep(1000);}catch(Exception e){}
			m_pStatus.setStatus("Updating...");
			if(addInSecondsHaveElapsed(45)) //call more than once per minute otherwise we sometimes miss stats 
			{
				updateCPUStats();
				updateMemoryStats();
				updateErrors();
			}			
			m_pStatus.setStatus("Idle");
		}//end while

		m_pStatus.setStatus("Shutting down");
		m_pSystem.doInformation("STATS.Shutdown", this);
		removeStatusLine(m_pStatus);
	}

	/**
	 * 
	 */
	private void updateErrors() 
	{
		if(m_lLastErrorCount<0) m_lLastErrorCount = m_pSystem.getErrorCount();
		long lCurrentErrorCount = m_pSystem.getErrorCount();
		long lErrorsThisPeriod = lCurrentErrorCount - m_lLastErrorCount;
		if(lErrorsThisPeriod<0) lErrorsThisPeriod = 0; //someone may have reset the error count
		this.incrementStatistic(STATISTIC_KEY_ERRORSPERHOUR, lErrorsThisPeriod);

		m_lLastErrorCount = lCurrentErrorCount;
	}

	/**
	 * 
	 */
	private void updateMemoryStats() 
	{
		//long lMaxHeapSizeBytes = Runtime.getRuntime().maxMemory();
		long lFreeHeapSizeBytes = Runtime.getRuntime().freeMemory();
		//long lUsedHeapSizeBytes = lMaxHeapSizeBytes - lFreeHeapSizeBytes;
		long lJVMUsedSizeBytes = Runtime.getRuntime().totalMemory();
		long lUsedAppSizeBytes = lJVMUsedSizeBytes - lFreeHeapSizeBytes;
		this.setStatistic(STATISTIC_KEY_MEMORYJVMINUSEPERMINUTE, new Double(lJVMUsedSizeBytes/1024/1024));
		this.setStatistic(STATISTIC_KEY_MEMORYAPPINUSEPERMINUTE, new Double(lUsedAppSizeBytes/1024/1024));
	}

	/**
	 * 
	 */
	private void updateCPUStats() 
	{
		long lTotalCPUTime = 0;
		long lTotalUserTime = 0;
		try
		{		
			final ThreadMXBean tmb = ManagementFactory.getThreadMXBean();
			this.setStatistic(STATISTIC_KEY_THREADCOUNTPERMINUTE, new Double(tmb.getThreadCount())); 
			if(!tmb.isThreadCpuTimeEnabled()) tmb.setThreadCpuTimeEnabled(true);
			if(tmb.isCurrentThreadCpuTimeSupported())
			{
				final long[] ids = tmb.getAllThreadIds( );
				for (int i=0; i<ids.length; i++ ) 
				{           
					final long lThreadCPUTime = tmb.getThreadCpuTime( ids[i] );
					final long lThreadUserTime = tmb.getThreadUserTime( ids[i] );
					//System.out.println(ids[i] + ": cpu:" + lThreadCPUTime + " user:" + lThreadUserTime );
					if ( lThreadCPUTime == -1 || lThreadUserTime == -1 )
						continue;   // Thread died             

					lTotalCPUTime += lThreadCPUTime;
					lTotalUserTime += lThreadUserTime;            
				}
			}
			else
			{
				lTotalCPUTime = -1;
				lTotalUserTime = -1;
				//System.out.println("isCurrentThreadCpuTimeSupported()=false");
			}
		}
		catch(Throwable t)
		{
			lTotalCPUTime = -1;
			lTotalUserTime = -1;
			//System.err.println(t.toString());
		} //in case we run on a 1.4 or lower JVM

		long lNowNanoSeconds = System.currentTimeMillis() * ONE_MILLION;
		double dAvailableCPUs = Runtime.getRuntime().availableProcessors();
		double dCPUTimeForPeriod = (double)(lTotalCPUTime-m_lLastCPUTime)/dAvailableCPUs; //average across all cpus
		double dTimeInPeriod = (double)(lNowNanoSeconds - m_lAddInTimeNanoSeconds);
		double dCPUPercent =  (dCPUTimeForPeriod/dTimeInPeriod) * 100;
		if(dCPUPercent>100) dCPUPercent = 100;
		if(dCPUPercent<0) dCPUPercent = 0; // Fk knows how this happens but it does occasionally
		this.setStatistic(STATISTIC_KEY_CPUPERMINUTE, new Double(dCPUPercent)); 

		//System.out.println(dCPUPercent + "% dTimeInPeriod:"+dTimeInPeriod + " dCPUTimeForPeriod:"+dCPUTimeForPeriod);

		m_lAddInTimeNanoSeconds = lNowNanoSeconds;
		m_lLastCPUTime = lTotalCPUTime;
		//m_lLastUserTime = lTotalUserTime;
	}

	/**
	 * Only allow one instance to be loaded
	 */	
	public boolean canLoadMultiple()
	{
		return false;
	}
	
	public AddInMessage sendMessage(AddInMessage oMessage)
	{
		AddInMessage am = super.sendMessage(oMessage);
		if(am!=null) return am;
		
		if(oMessage==null) return null;
		
		String sAction = oMessage.getParameter("Action");
		if(sAction!=null && sAction.equalsIgnoreCase("Login"))
		{
			//increment login count
			//m_pSystem.doDebug(0, "LOGIN:"+oMessage.toString(), this);
			this.incrementStatistic(STATISTIC_KEY_LOGINSPERHOUR, 1); 
		}


		am = new AddInMessage();
		am.Status = AddInMessage.STATUS_SUCCESS;
		return am;
	}

}//class
