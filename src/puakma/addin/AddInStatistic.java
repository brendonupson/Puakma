package puakma.addin;

import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

import puakma.error.ErrorDetect;
import puakma.system.pmaSystem;
import puakma.util.Util;

/**
 * This class counts statistics in time brackets. This will allow for example "http.hitsperhour"
 * @author bupson
 *
 */
public class AddInStatistic implements ErrorDetect
{
	public static final int STAT_CAPTURE_ONCE = 0;
	public static final int STAT_CAPTURE_PER_SECOND = 1;
	public static final int STAT_CAPTURE_PER_MINUTE = 2;
	public static final int STAT_CAPTURE_PER_HOUR = 3;
	public static final int STAT_CAPTURE_PER_DAY = 4;


	private String m_sStatisticKey;
	private int m_iCaptureType = STAT_CAPTURE_ONCE;
	private int m_iMaxPeriodsHistory;
	private Date m_dtCreated = new Date();
	private Date m_dtLastIncremented = null;
	private pmaAddIn m_pParent = null;
	private Vector m_vStatData = new Vector();

	public AddInStatistic(pmaAddIn parent, String sStatKey, int iCaptureType, int iMaxPeriodsHistory)
	{
		m_pParent = parent;
		if(sStatKey==null) sStatKey = Long.toHexString((long)(Math.random()*99999999));
		m_sStatisticKey = sStatKey.toLowerCase(); 
		m_iCaptureType = iCaptureType; 
		m_iMaxPeriodsHistory = iMaxPeriodsHistory;
	}

	public static void main(String args[])
	{
		AddInStatistic as = new AddInStatistic(null, "HTTP.hitspersecond", STAT_CAPTURE_PER_SECOND, 3);

		for(int i=0; i<20; i++)
		{
			as.increment(1);
			long lSleep = (long)(Math.random()*1000);
			//System.out.println("sleep: " + lSleep);
			try{Thread.sleep(lSleep);} catch(Exception e){}
		}

		AddInStatisticEntry se[] = as.getStatistics();
		for(int i=0; i<se.length; i++)
		{
			System.out.println(se[i].toString());
		}

		as.prune();
		System.out.println("--------------");
		se = as.getStatistics();
		for(int i=0; i<se.length; i++)
		{
			System.out.println(se[i].toString());
		}
	}

	public String getStatisticKey()
	{
		return m_sStatisticKey;
	}

	public Date getCreatedDate()
	{
		return m_dtCreated;	
	}

	public Date getLastIncrementedDate()
	{
		return m_dtLastIncremented;	
	}

	/**
	 * 
	 */
	public void prune()
	{
		//TODO delete old stats from the array to save memory

		//if(m_pParent!=null) m_pParent.m_pSystem.doDebug(0, "PRUNE", this);

		if(m_iCaptureType==STAT_CAPTURE_ONCE) return; //nothing to do these don't get pruned
		Date dtPruneBefore = new Date();
		if(m_iCaptureType==STAT_CAPTURE_PER_DAY) 
		{
			if(m_iMaxPeriodsHistory<1)
				dtPruneBefore = Util.adjustDate(new Date(), 0, 0, -1, 0, 0, 0);
			else
				dtPruneBefore = Util.adjustDate(new Date(), 0, 0, m_iMaxPeriodsHistory*-1, 0, 0, 0);
		}
		if(m_iCaptureType==STAT_CAPTURE_PER_HOUR) 
		{
			if(m_iMaxPeriodsHistory<1)
				dtPruneBefore = Util.adjustDate(new Date(), 0, 0, -1, 0, 0, 0);
			else
				dtPruneBefore = Util.adjustDate(new Date(), 0, 0, 0, m_iMaxPeriodsHistory*-1, 0, 0);
		}
		if(m_iCaptureType==STAT_CAPTURE_PER_MINUTE) 
		{
			if(m_iMaxPeriodsHistory<1)
				dtPruneBefore = Util.adjustDate(new Date(), 0, 0, 0, -1, 0, 0);
			else
				dtPruneBefore = Util.adjustDate(new Date(), 0, 0, 0, 0, m_iMaxPeriodsHistory*-1, 0);
		}
		if(m_iCaptureType==STAT_CAPTURE_PER_SECOND) 
		{
			if(m_iMaxPeriodsHistory<1)
				dtPruneBefore = Util.adjustDate(new Date(), 0, 0, 0, 0, -1, 0);
			else
				dtPruneBefore = Util.adjustDate(new Date(), 0, 0, 0, 0, 0, m_iMaxPeriodsHistory*-1);
		}

		Date dtBounds[] = getDateBounds(dtPruneBefore);
		//if(m_pParent!=null) m_pParent.m_pSystem.doDebug(0, "Pruning older than " + dtBounds[0], this);

		//System.out.println("Pruning older than " + dtBounds[0]);

		for(int i=0; i<m_vStatData.size(); i++)
		{
			AddInStatisticEntry se = (AddInStatisticEntry) m_vStatData.get(i);
			int iDiff = se.timeDifference(dtBounds[0]); 
			if(iDiff<0) 
			{
				//System.out.println(iDiff + " Pruning: " + se.toString());
				m_vStatData.removeElementAt(i);
				i--;
			}
			//else
			//	System.out.println(iDiff + " Keeping: " + se.toString());
		}
	}

	/**
	 * The statistic in the time period specified
	 * @param objValue
	 * @param bSet
	 */
	private void updateNumeric(Object objValue, boolean bSet)
	{
		Date dtNow = new Date();
		
		m_dtLastIncremented = dtNow;
		if(m_iCaptureType==STAT_CAPTURE_ONCE) 
		{
			if(m_vStatData.size()==0) 
			{
				m_vStatData.add(new AddInStatisticEntry(objValue));
				return;
			}
			AddInStatisticEntry se = (AddInStatisticEntry) m_vStatData.get(0);
			if(bSet)
				se.set(objValue);
			else
				se.increment(objValue);
			return;
		}

		Date dtBounds[] = getDateBounds(dtNow);
		AddInStatisticEntry se = findStatisticEntry(dtBounds);
		if(bSet)
			se.set(objValue);
		else
			se.increment(objValue);		
	}

	/**
	 * 
	 * @param dIncrementBy
	 */
	public void increment(double dIncrementBy)
	{
		updateNumeric(Double.valueOf(dIncrementBy), false);
	}

	/**
	 * 
	 * @param dSetToValue
	 */
	public void set(Object objSetToValue)
	{
		updateNumeric(objSetToValue, true);
	}
	

	/**
	 * 
	 * @return
	 */
	public int getCaptureType()
	{
		return m_iCaptureType;
	}

	/**
	 * Assume all statistics have the same data type
	 * @return
	 */
	public AddInStatisticEntry[] getStatistics()
	{
		AddInStatisticEntry se[] = new AddInStatisticEntry[m_vStatData.size()];

		for(int i=0; i<m_vStatData.size(); i++)
		{
			se[i] = (AddInStatisticEntry) m_vStatData.get(i);
		}
		return se;
	}

	/**
	 * 
	 * @param dtBounds
	 * @return
	 */
	private AddInStatisticEntry findStatisticEntry(Date[] dtBounds) 
	{
		Date dtStart = dtBounds[0];

		AddInStatisticEntry stat = null;
		// work from the tail back should be faster on a longer list
		for(int i=m_vStatData.size()-1; i>=0; i--)
		{
			stat = (AddInStatisticEntry) m_vStatData.get(i);
			if(stat.timeDifference(dtStart)==0) break;
			if(stat.timeDifference(dtStart)<0) 
			{
				stat = null;
				break;
			}
		}

		if(stat==null) 
		{
			stat = new AddInStatisticEntry(dtBounds[0], dtBounds[1], new Double(0));
			m_vStatData.add(stat);
		}

		return stat;
	}

	/**
	 * 
	 * @param dtDate
	 * @return
	 */
	private Date[] getDateBounds(Date dtDate) 
	{
		return getDateBounds(dtDate, m_iCaptureType);
	}

	/**
	 * Returns the start and end time periods around the dtDate supplied, relative to the iCaptureType
	 * @param dtDate
	 * @return
	 */
	public static Date[] getDateBounds(Date dtDate, int iCaptureType) 
	{
		if(dtDate==null) dtDate = new Date();
		Date dtBounds[] = new Date[]{dtDate, dtDate};

		if(iCaptureType==STAT_CAPTURE_PER_SECOND)
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtDate);						
			cal.set(Calendar.MILLISECOND, 0);
			dtBounds[0] = cal.getTime();
			dtBounds[1] = Util.adjustDate(cal.getTime(), 0, 0, 0, 0, 0, 1);
			return dtBounds;
		}

		if(iCaptureType==STAT_CAPTURE_PER_MINUTE)
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtDate);			
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			dtBounds[0] = cal.getTime();
			dtBounds[1] = Util.adjustDate(cal.getTime(), 0, 0, 0, 0, 1, 0);
			return dtBounds;
		}

		if(iCaptureType==STAT_CAPTURE_PER_HOUR)
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtDate);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			dtBounds[0] = cal.getTime();
			dtBounds[1] = Util.adjustDate(cal.getTime(), 0, 0, 0, 1, 0, 0);
			return dtBounds;
		}

		if(iCaptureType==STAT_CAPTURE_PER_DAY)
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtDate);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			dtBounds[0] = cal.getTime();
			dtBounds[1] = Util.adjustDate(cal.getTime(), 0, 0, 1, 0, 0, 0);
			return dtBounds;
		}


		return dtBounds;
	}

	public Date getStatisticCreated()
	{
		return m_dtCreated;
	}

	public Date getStatisticLastUpdated()
	{
		return m_dtLastIncremented;
	}

	/**
	 * 
	 */
	public String getErrorSource() 
	{		
		return m_pParent.getAddInName() + '_' + m_sStatisticKey;
	}

	/**
	 * 
	 */
	public String getErrorUser()
	{		
		return pmaSystem.SYSTEM_ACCOUNT;
	}

}//class
