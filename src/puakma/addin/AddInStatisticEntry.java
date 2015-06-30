package puakma.addin;

import java.util.Date;

import puakma.util.Util;

public class AddInStatisticEntry
{
	private Date m_dtFromDate;
	private Date m_dtToDate;
	private Object m_data;

	public AddInStatisticEntry(Date dtFrom, Date dtTo, Object objData)
	{
		m_dtFromDate = dtFrom;
		m_dtToDate = dtTo;
		m_data = objData;
	}

	public AddInStatisticEntry(Object objData)
	{
		//m_dtFromDate = dtFrom;
		//m_dtToDate = dtTo;
		m_data = objData;
	}

	
	/**
	 * 
	 * @param objIncrementBy
	 */
	public synchronized void increment(Object objIncrementBy)	
	{
		if(m_data==null) m_data = new Double(0);
		if(!(m_data instanceof Double)) return; //we can't increment a non-double
		
		Double d = (Double)m_data;
		double dIncrementBy = 0;
		if(objIncrementBy instanceof Double) dIncrementBy = ((Double)objIncrementBy).doubleValue();
		if(objIncrementBy instanceof Long) dIncrementBy = ((Long)objIncrementBy).longValue();
		if(objIncrementBy instanceof Integer) dIncrementBy = ((Integer)objIncrementBy).intValue();
		m_data = new Double(d.doubleValue() + dIncrementBy);
	}
	
	/**
	 * 
	 * @param objSetToValue
	 */
	public synchronized void set(Object objSetToValue) 
	{
		m_data = objSetToValue;		
	}

	/**
	 * 
	 * @param dtStart
	 * @return
	 */
	public int timeDifference(Date dtStart) 
	{			
		return m_dtFromDate.compareTo(dtStart);
	}
	
	public Date[] getDateBounds()
	{
		return new Date[]{m_dtFromDate, m_dtToDate};
	}

	public Object getObject()
	{
		return m_data;
	}

	/**
	 * For convenience since we'll mostly be dealing with counters
	 * @return
	 */
	public double getDoubleValue()
	{
		if(m_data instanceof Double) return ((Double)m_data).doubleValue();
		return 0;
	}

	public String toString()
	{
		String sFormat = "yyyy-MM-dd:HH:mm:SS";
		return Util.formatDate(m_dtFromDate, sFormat) + " " + Util.formatDate(m_dtToDate, sFormat) + " [" + m_data + "]";
	}

	
}