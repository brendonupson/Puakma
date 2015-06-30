/** ***************************************************************
AgendaItem.java
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

package puakma.addin.agenda;

import puakma.system.*;
import puakma.util.*;
import java.util.*;
import java.text.*;
/**
 * <p>Title: AgendaItem</p>
 * <p>Description: An object describing something that will be processed by AGENDA</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */

public class AgendaItem
{
	public final static String SCHED_NONE="N";
	public final static String SCHED_SECOND="S";
	public final static String SCHED_MINUTE="I";
	public final static String SCHED_HOUR="H";
	public final static String SCHED_DAY="D";
	public final static String SCHED_WEEK="W";
	public final static String SCHED_MONTH="M";
	public final static String SCHED_YEAR="Y";

	public final static String DAY_SUNDAY="S";
	public final static String DAY_MONDAY="M";
	public final static String DAY_TUESDAY="T";
	public final static String DAY_WEDNESDAY="W";
	public final static String DAY_THURSDAY="H";
	public final static String DAY_FRIDAY="F";
	public final static String DAY_SATURDAY="A";


	private String m_sAppGroup="";
	private String m_sAppName="";
	private String m_sDesignName="";
	private RequestPath m_rPath;
	private String m_sOptions="";
	private int m_iDesignID=-1;
	private java.util.Date m_dtNextRunTime=null; //the next time this item should be run
	private java.util.Date m_dtLastRunTime=null; //the last time this item was run

	private String m_sScheduleType=SCHED_NONE;
	private int m_iInterval=1;
	private String m_sDays=DAY_SUNDAY+DAY_MONDAY+DAY_TUESDAY+DAY_WEDNESDAY+DAY_THURSDAY+DAY_FRIDAY+DAY_SATURDAY;
	private int m_iStartHour=0;
	private int m_iStartMinute=0;
	private int m_iFinishHour=24;
	private int m_iFinishMinute=0;
	private int m_iDate=1; //for Y only
	private int m_iMonth=0; //for Y only
	private SimpleDateFormat m_sdf = new SimpleDateFormat("EEE dd.MMM.yy HH:mm:ss");


	/**
	 * Create a new agendaitem
	 * @param szAppGroup
	 * @param szAppName
	 * @param szDesignName
	 * @param szOptions
	 * @param iDesignID
	 */
	public AgendaItem(String szAppGroup, String szAppName, String szDesignName, String szOptions, int iDesignID)
	{
		m_sAppGroup = szAppGroup;
		m_sAppName = szAppName;
		m_sDesignName = szDesignName;
		m_sOptions = szOptions;
		m_iDesignID = iDesignID;

		m_rPath = new RequestPath(m_sAppGroup + "/" + m_sAppName + ".pma/" + m_sDesignName);

		String szTemp = getOptionElement("Schedule");
		if(szTemp != null)  m_sScheduleType = szTemp.toUpperCase();

		szTemp = getOptionElement("Days");
		if(szTemp != null)  m_sDays = szTemp.toUpperCase();

		szTemp = getOptionElement("LastRun");
		if(szTemp != null)
		{
			try
			{
				long lTime = Long.parseLong(szTemp);
				Calendar cal = Calendar.getInstance();
				cal.setTimeInMillis(lTime);
				m_dtLastRunTime = cal.getTime();
				java.util.Date dtNow = new java.util.Date();
				//if the last run time is in the future, make it now
				//this caters for servers that have had the wrong time settings
				if(m_dtLastRunTime.after(dtNow)) m_dtLastRunTime = dtNow;
			}
			catch(Exception dte){}
		}

		szTemp = getOptionElement("Interval");
		if(szTemp != null)
		{
			try{ m_iInterval = Integer.parseInt(szTemp); }
			catch(Exception ie){}
		}
		m_iInterval = Math.abs(m_iInterval);

		szTemp = getOptionElement("Date");
		if(szTemp != null)
		{
			try{ m_iDate = Integer.parseInt(szTemp); }
			catch(Exception de){}
		}

		szTemp = getOptionElement("Month");
		if(szTemp != null)
		{
			//month is 0 based. 0=jan
			try{ m_iMonth = Integer.parseInt(szTemp); m_iMonth--; }
			catch(Exception me){}
		}

		parseTimes();
	}

	/**
	 * breaks the HH:mm components into int values. The clock is in 24hour time.
	 */
	private void parseTimes()
	{
		String szTemp = getOptionElement("StartTime");
		if(szTemp != null)
		{
			int iPos = szTemp.indexOf(":");
			if(iPos>0) //well it shouldnt start with a colon!
			{
				String szStartHour = szTemp.substring(0, iPos);
				String szStartMinute = szTemp.substring(iPos+1, szTemp.length());
				try
				{
					m_iStartHour = Integer.parseInt(szStartHour);
					m_iStartMinute = Integer.parseInt(szStartMinute);
				}
				catch(Exception starte){}
			}
		}

		szTemp = getOptionElement("FinishTime");
		if(szTemp != null)
		{
			int iPos = szTemp.indexOf(":");
			if(iPos>0) //well it shouldnt start with a colon!
			{
				String szFinishHour = szTemp.substring(0, iPos);
				String szFinishMinute = szTemp.substring(iPos+1, szTemp.length());
				try
				{
					m_iFinishHour = Integer.parseInt(szFinishHour);
					m_iFinishMinute = Integer.parseInt(szFinishMinute);
				}
				catch(Exception starte){}
			}
		}
	}


	/**
	 * gets the date this action was last run
	 * @return
	 */
	public java.util.Date getLastRunDate()
	{
		return m_dtLastRunTime;
	}


	/**
	 * Build a string describing when this item should next be run.
	 * @return
	 */
	public String getSchedule()
	{
		String szFormattedTime="UnScheduled";
		if(m_dtNextRunTime!=null) szFormattedTime = m_sdf.format(m_dtNextRunTime);
		return m_rPath.getPathToDesign() + "  " + m_iInterval + m_sScheduleType + "  NextRun=" + szFormattedTime;
	}

	/**
	 * Gets a value from the options string.
	 * @param szWhat - the item to get ie "Schedule"
	 * @return null if the item was not found
	 */
	private String getOptionElement(String szWhat)
	{
		if(m_sOptions==null) return null;
		if(szWhat==null) return null;

		szWhat = szWhat.toLowerCase();
		String szOpt = m_sOptions.toLowerCase();
		int iPos = szOpt.indexOf(szWhat+ "=");
		if(iPos>=0) //item exists
		{
			String szFound = szOpt.substring(iPos+szWhat.length()+1, szOpt.length());
			iPos = szFound.indexOf(',');
			if(iPos>=0) szFound = szFound.substring(0, iPos);
			return szFound;
		}
		return null;
	}


	/**
	 *
	 * @return
	 */
	public String getOptionString()
	{
		return m_sOptions;
	}

	/**
	 * Determines if the current item should run or not.
	 */
	public boolean shouldRun()
	{
		java.util.Date dtNow = new java.util.Date();

		if(m_dtNextRunTime==null) return false;
		if(dtNow.after(m_dtNextRunTime)) return true;

		return false;
	}


	/**
	 * Describe this object
	 * @return
	 */
	public String toString()
	{
		return getSchedule() + " LastRun=" + m_sdf.format(m_dtLastRunTime);
	}


	/**
	 * stamps the item's last run time property
	 */
	public synchronized void setLastRunTime()
	{
		m_dtLastRunTime = new java.util.Date();
	}


	/**
	 * sets the item's next run time property. After applying the relevant calcs.
	 */
	public synchronized void setNextRunTime()
	{
		if(m_sScheduleType.equals(SCHED_NONE))
		{
			m_dtNextRunTime = null;
			return;
		}

		if(m_sScheduleType.equals(SCHED_SECOND))
		{
			m_dtNextRunTime = determineNextRunSecond();
			return;
		}

		if(m_sScheduleType.equals(SCHED_MINUTE))
		{
			m_dtNextRunTime = determineNextRunMinute();
			return;
		}

		if(m_sScheduleType.equals(SCHED_HOUR))
		{
			m_dtNextRunTime = determineNextRunHour();
			return;
		}

		if(m_sScheduleType.equals(SCHED_DAY))
		{
			m_dtNextRunTime = determineNextRunDay();
			return;
		}

		if(m_sScheduleType.equals(SCHED_WEEK))
		{
			m_dtNextRunTime = determineNextRunWeek();
			return;
		}

		if(m_sScheduleType.equals(SCHED_MONTH))
		{
			m_dtNextRunTime = determineNextRunMonth();
			return;
		}

		if(m_sScheduleType.equals(SCHED_YEAR))
		{
			m_dtNextRunTime = determineNextRunYear();
			return;
		}
	}


	/**
	 * Determines the next run datetime for something that is scheduled on a per
	 * second basis.
	 * @return the date time that this item should next be run
	 */
	private java.util.Date determineNextRunSecond()
	{
		boolean bSearching=true;
		java.util.Date dtNext=null;
		//java.util.Date dtNow=new java.util.Date();

		if(m_dtLastRunTime!=null)
			dtNext = Util.adjustDate(m_dtLastRunTime, 0, 0, 0, 0, 0, m_iInterval);
		else //set to a past period
		{
			dtNext = Util.adjustDate(new java.util.Date(), 0, 0, 0, 0, 0, -m_iInterval);
		}

		while(bSearching)
		{
			bSearching = false;
			//roll the date to the next day that it may run, and set the start time
			while(!canRunThisDay(dtNext))
			{
				Calendar cal = Calendar.getInstance();
				cal.setTime(dtNext);
				cal.set(Calendar.HOUR_OF_DAY, m_iStartHour);
				cal.set(Calendar.MINUTE, m_iStartMinute);
				cal.set(Calendar.SECOND, 0);
				dtNext = Util.adjustDate(cal.getTime(), 0, 0, 1, 0, 0, 0);
			}

			//now work out the boundaries it should run in: start & finish times
			Calendar calNext = Calendar.getInstance();
			calNext.setTime(dtNext);
			if(calNext.get(Calendar.HOUR_OF_DAY)<m_iStartHour) calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
			if(calNext.get(Calendar.HOUR_OF_DAY)==m_iStartHour && calNext.get(Calendar.MINUTE)<m_iStartMinute)
				calNext.set(Calendar.MINUTE, m_iStartMinute);
			if(calNext.get(Calendar.HOUR_OF_DAY)>m_iFinishHour ||
					(calNext.get(Calendar.HOUR_OF_DAY)==m_iFinishHour && calNext.get(Calendar.MINUTE)>m_iFinishMinute) )
			{
				calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
				calNext.set(Calendar.MINUTE, m_iStartMinute);
				//calNext.set(Calendar.SECOND, 0);
				calNext.setTime(Util.adjustDate(calNext.getTime(), 0, 0, 1, 0, 0, 0));
				bSearching = true;
			}

			dtNext = calNext.getTime();
		}//while bSearching

		return dtNext;
	}

	/**
	 * Determines the next run datetime for something that is scheduled on a per
	 * minute basis.
	 * @return the date time that this item should next be run
	 */
	private java.util.Date determineNextRunMinute()
	{
		boolean bSearching=true;
		java.util.Date dtNext=null;


		if(m_dtLastRunTime!=null)
			dtNext = Util.adjustDate(m_dtLastRunTime, 0, 0, 0, 0, m_iInterval, 0);
		else //set way back in the past
		{
			dtNext = Util.adjustDate(new java.util.Date(), 0, 0, 0, 0, -m_iInterval, 0);
		}


		while(bSearching)
		{
			bSearching = false;
			//roll the date to the next day that it may run, and set the start time
			while(!canRunThisDay(dtNext))
			{
				Calendar cal = Calendar.getInstance();
				cal.setTime(dtNext);
				cal.set(Calendar.HOUR_OF_DAY, m_iStartHour);
				cal.set(Calendar.MINUTE, m_iStartMinute);
				cal.set(Calendar.SECOND, 0);
				dtNext = Util.adjustDate(cal.getTime(), 0, 0, 1, 0, 0, 0);
			}

			//now work out the boundaries it should run in: start & finish times
			Calendar calNext = Calendar.getInstance();
			calNext.setTime(dtNext);
			//calNext.set(Calendar.SECOND, 0);
			if(calNext.get(Calendar.HOUR_OF_DAY)<m_iStartHour) calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
			if(calNext.get(Calendar.HOUR_OF_DAY)==m_iStartHour && calNext.get(Calendar.MINUTE)<m_iStartMinute)
				calNext.set(Calendar.MINUTE, m_iStartMinute);
			if(calNext.get(Calendar.HOUR_OF_DAY)>m_iFinishHour ||
					(calNext.get(Calendar.HOUR_OF_DAY)==m_iFinishHour && calNext.get(Calendar.MINUTE)>m_iFinishMinute) )
			{
				calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
				calNext.set(Calendar.MINUTE, m_iStartMinute);
				calNext.setTime(Util.adjustDate(calNext.getTime(), 0, 0, 1, 0, 0, 0));
				bSearching = true;
			}

			dtNext = calNext.getTime();
		}//while bSearching

		return dtNext;
	}

	/**
	 * Determines the next run datetime for something that is scheduled on a per
	 * minute basis.
	 * @return the date time that this item should next be run
	 */
	private java.util.Date determineNextRunHour()
	{
		boolean bSearching=true;
		java.util.Date dtNext=null;


		if(m_dtLastRunTime!=null)
			dtNext = Util.adjustDate(m_dtLastRunTime, 0, 0, 0, m_iInterval, 0, 0);
		else //set back in the past
		{
			dtNext = Util.adjustDate(new java.util.Date(), 0, 0, -m_iInterval, 0, 0, 0);
		}

		while(bSearching)
		{
			bSearching = false;
			//roll the date to the next day that it may run, and set the start time
			while(!canRunThisDay(dtNext))
			{
				Calendar cal = Calendar.getInstance();
				cal.setTime(dtNext);
				cal.set(Calendar.HOUR_OF_DAY, m_iStartHour);
				cal.set(Calendar.MINUTE, m_iStartMinute);
				cal.set(Calendar.SECOND, 0);
				dtNext = Util.adjustDate(cal.getTime(), 0, 0, 1, 0, 0, 0);
			}

			//now work out the boundaries it should run in: start & finish times
			Calendar calNext = Calendar.getInstance();
			calNext.setTime(dtNext);
			calNext.set(Calendar.SECOND, 0);
			if(calNext.get(Calendar.HOUR_OF_DAY)<m_iStartHour) calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
			if(calNext.get(Calendar.HOUR_OF_DAY)==m_iStartHour && calNext.get(Calendar.MINUTE)<m_iStartMinute)
				calNext.set(Calendar.MINUTE, m_iStartMinute);
			if(calNext.get(Calendar.HOUR_OF_DAY)>m_iFinishHour ||
					(calNext.get(Calendar.HOUR_OF_DAY)==m_iFinishHour && calNext.get(Calendar.MINUTE)>m_iFinishMinute) )
			{
				calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
				calNext.set(Calendar.MINUTE, m_iStartMinute);
				calNext.setTime(Util.adjustDate(calNext.getTime(), 0, 0, 1, 0, 0, 0));
				bSearching = true;
			}

			dtNext = calNext.getTime();
		}//while bSearching

		return dtNext;
	}


	/**
	 * Determines the next run datetime for something that is scheduled on a per
	 * day basis.
	 * @return the date time that this item should next be run
	 */
	private java.util.Date determineNextRunDay()
	{
		//boolean bSearching=true;
		java.util.Date dtNext=null;


		if(m_dtLastRunTime!=null)
			dtNext = Util.adjustDate(m_dtLastRunTime, 0, 0, m_iInterval, 0, 0, 0);
		else //set back in the past
		{
			dtNext = Util.adjustDate(new java.util.Date(), 0, 0, -m_iInterval, 0, 0, 0);
		}

		//roll the date to the next day that it may run, and set the start time
		while(!canRunThisDay(dtNext))
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtNext);
			cal.set(Calendar.HOUR_OF_DAY, m_iStartHour);
			cal.set(Calendar.MINUTE, m_iStartMinute);
			cal.set(Calendar.SECOND, 0);
			dtNext = Util.adjustDate(cal.getTime(), 0, 0, 1, 0, 0, 0);
		}

		//now work out the boundaries it should run in: start & finish times
		Calendar calNext = Calendar.getInstance();
		calNext.setTime(dtNext);
		calNext.set(Calendar.SECOND, 0);
		calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
		calNext.set(Calendar.MINUTE, m_iStartMinute);

		dtNext = calNext.getTime();

		return dtNext;
	}

	/**
	 * Determines the next run datetime for something that is scheduled on a per
	 * week basis.
	 * @return the date time that this item should next be run
	 */
	private java.util.Date determineNextRunWeek()
	{
		//boolean bSearching=true;
		java.util.Date dtNext=null;
		//java.util.Date dtNow=new java.util.Date();

		if(m_dtLastRunTime!=null)
			dtNext = Util.adjustDate(m_dtLastRunTime, 0, 0, m_iInterval*7, 0, 0, 0);
		else //set back in the past
		{
			dtNext = Util.adjustDate(new java.util.Date(), 0, 0, -m_iInterval*7, 0, 0, 0);
		}

		//roll the date to the next day that it may run, and set the start time
		while(!canRunThisDay(dtNext))
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtNext);
			cal.set(Calendar.HOUR_OF_DAY, m_iStartHour);
			cal.set(Calendar.MINUTE, m_iStartMinute);
			cal.set(Calendar.SECOND, 0);
			dtNext = Util.adjustDate(cal.getTime(), 0, 0, 1, 0, 0, 0);
		}

		//now work out the boundaries it should run in: start times
		Calendar calNext = Calendar.getInstance();
		calNext.setTime(dtNext);
		calNext.set(Calendar.SECOND, 0);
		calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
		calNext.set(Calendar.MINUTE, m_iStartMinute);

		dtNext = calNext.getTime();

		return dtNext;
	}


	/**
	 * Determines the next run datetime for something that is scheduled on a per
	 * day basis.
	 * @return the date time that this item should next be run
	 */
	private java.util.Date determineNextRunMonth()
	{
		//boolean bSearching=true;
		java.util.Date dtNext=null;


		if(m_dtLastRunTime!=null)
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(m_dtLastRunTime);
			cal.set(Calendar.DAY_OF_MONTH, m_iDate);
			dtNext = Util.adjustDate(cal.getTime(), 0, m_iInterval, 0, 0, 0, 0);
		}
		else //set back in the past
		{
			dtNext = Util.adjustDate(new java.util.Date(), 0, -m_iInterval, 0, 0, 0, 0);
		}


		//roll the date to the next day that it may run, and set the start time
		while(!canRunThisDayOfMonth(dtNext))
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtNext);
			cal.set(Calendar.HOUR_OF_DAY, m_iStartHour);
			cal.set(Calendar.MINUTE, m_iStartMinute);
			cal.set(Calendar.SECOND, 0);
			dtNext = Util.adjustDate(cal.getTime(), 0, 0, 1, 0, 0, 0);
		}

		//now work out the boundaries it should run in: start & finish times
		Calendar calNext = Calendar.getInstance();
		calNext.setTime(dtNext);
		calNext.set(Calendar.SECOND, 0);
		calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
		calNext.set(Calendar.MINUTE, m_iStartMinute);

		dtNext = calNext.getTime();

		return dtNext;
	}


	/**
	 * Determines the next run datetime for something that is scheduled on a per
	 * year basis.
	 * @return the date time that this item should next be run
	 */
	private java.util.Date determineNextRunYear()
	{      
		java.util.Date dtNext=null;

		if(m_dtLastRunTime!=null)
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(m_dtLastRunTime);
			cal.set(Calendar.MONTH, m_iMonth);
			cal.set(Calendar.DAY_OF_MONTH, m_iDate);
			dtNext = Util.adjustDate(cal.getTime(), m_iInterval, 0, 0, 0, 0, 0);
		}
		else //set back in the past
		{
			dtNext = Util.adjustDate(new java.util.Date(), -m_iInterval, 0, 0, 0, 0, 0);
		}


		//roll the date to the next day that it may run, and set the start time
		while(!canRunThisMonth(dtNext))
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtNext);
			cal.set(Calendar.DAY_OF_MONTH, m_iDate);
			cal.set(Calendar.HOUR_OF_DAY, m_iStartHour);
			cal.set(Calendar.MINUTE, m_iStartMinute);
			cal.set(Calendar.SECOND, 0);
			dtNext = Util.adjustDate(cal.getTime(), 0, 1, 0, 0, 0, 0);
		}

		while(!canRunThisDayOfMonth(dtNext))
		{
			Calendar cal = Calendar.getInstance();
			cal.setTime(dtNext);
			cal.set(Calendar.HOUR_OF_DAY, m_iStartHour);
			cal.set(Calendar.MINUTE, m_iStartMinute);
			cal.set(Calendar.SECOND, 0);
			dtNext = Util.adjustDate(cal.getTime(), 0, 0, 1, 0, 0, 0);
		}

		//now work out the boundaries it should run in: start & finish times
		Calendar calNext = Calendar.getInstance();
		calNext.setTime(dtNext);
		calNext.set(Calendar.SECOND, 0);
		calNext.set(Calendar.HOUR_OF_DAY, m_iStartHour);
		calNext.set(Calendar.MINUTE, m_iStartMinute);

		dtNext = calNext.getTime();

		return dtNext;
	}


	/**
	 * Determines if the current item can run today
	 * @return
	 */
	private boolean canRunThisDayOfMonth(java.util.Date dtDay)
	{
		Calendar cal = Calendar.getInstance();
		cal.setTime(dtDay);

		int iDay = cal.get(Calendar.DAY_OF_MONTH);
		if(iDay==m_iDate) return true;

		return false;
	}

	/**
	 * Determines if the current item can run today
	 * @return
	 */
	private boolean canRunThisMonth(java.util.Date dtDay)
	{
		Calendar cal = Calendar.getInstance();
		cal.setTime(dtDay);

		int iMonth = cal.get(Calendar.MONTH);
		if(iMonth==m_iMonth) return true;

		return false;
	}


	/**
	 * Determines if the current item can run today
	 * @return
	 */
	private boolean canRunThisDay(java.util.Date dtDay)
	{
		Calendar cal = Calendar.getInstance();
		cal.setTime(dtDay);
		switch(cal.get(Calendar.DAY_OF_WEEK))
		{
		case Calendar.SUNDAY:
			if(m_sDays.indexOf(DAY_SUNDAY)>=0) return true;
			break;
		case Calendar.MONDAY:
			if(m_sDays.indexOf(DAY_MONDAY)>=0) return true;
			break;
		case Calendar.TUESDAY:
			if(m_sDays.indexOf(DAY_TUESDAY)>=0) return true;
			break;
		case Calendar.WEDNESDAY:
			if(m_sDays.indexOf(DAY_WEDNESDAY)>=0) return true;
			break;
		case Calendar.THURSDAY:
			if(m_sDays.indexOf(DAY_THURSDAY)>=0) return true;
			break;
		case Calendar.FRIDAY:
			if(m_sDays.indexOf(DAY_FRIDAY)>=0) return true;
			break;
		case Calendar.SATURDAY:
			if(m_sDays.indexOf(DAY_SATURDAY)>=0) return true;
			break;
		};

		return false;

	}


	/**
	 * Gets the path to the design element /group/app.pma/design
	 * @return
	 */
	public String getPath()
	{
		return m_rPath.getPathToDesign();
	}
	
	/**
	 * Determines if the path passed matches the path for this scheduled action. 
	 * eg if "/grp/app.pma/Action?Param&q=1&q=2" is passed, it will match on "/grp/app.pma/action"
	 * @param sPath
	 * @return
	 */
	public boolean matchesPath(String sPath)
	{
		//System.out.println("TEST: " + sPath + " = " + getPath());
		if(sPath==null) return false;
		
		String sLowPath = getPath().toLowerCase();
		if(sPath.toLowerCase().startsWith(sLowPath)) return true;
		return false;
	}


	/**
	 * Gets the path to the design element /group/app.pma/design
	 * @return
	 */
	public int getDesignID()
	{
		return m_iDesignID;
	}
}