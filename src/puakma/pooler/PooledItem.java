/** ***************************************************************
PooledItem.java
Copyright (C) 2001  Mike Skillicorn 
http://www.seatechnology.com.au mike@mikeskillicorn.com

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
/**
 * Title:        PooledItem<p>
 * Description:  Item for pooling in a Cache or a Pooler <p>
 * Copyright:    Copyright (c) Mike Skillicorn<p>
 * Company:      <p>
 * @author       Mike Skillicorn
 * @version 1.0
 *
 * NOTES
 *
 * The Pooled Item is a final class. It is intended to provide all the
 * necessary functionality and stats of a pooled item in a pool or a cache.
 * It is NOT intended to be overridden as this could lead to incorrect
 * behaviour of the "in use" mechanism and the stats.
 *
 * The PooledItem provides a pointer to a user defined object that is used
 * as the payload for the pooled item. This must be specified when you create
 * the pooled item.
 *
 * eg
 * SessionItem si = new SessionItem( sSessionId, m_iLockOutSeconds, m_session_log, sIP );
 * session_cache.addItem( sSessionId, new PooledItem( si ) );
 *
 * When you lock an item through use, the PooledItem takes care of all the
 * locking and usage details.
 */
package puakma.pooler;

import java.util.Date;
import java.text.*;


public final class PooledItem
{  
	protected Object  m_item = null;
	private   boolean m_bIsInUse  = false;
	private   Date    m_dtCreated;
	private   long    m_lLastUsed = 0;
	private   long     m_lHits = 0;
	private   long    m_lTotalUsage = 0;

	public PooledItem( Object obj )
	{  
		m_item = obj;
		m_dtCreated = new Date();
		m_lLastUsed = System.currentTimeMillis();
	}

	public boolean isInUse()  {  return( m_bIsInUse  ); }


	public void setInUse( boolean bInUse )
	{  
		m_bIsInUse = bInUse;
		if ( bInUse )
		{  
			m_lHits++;
			m_lLastUsed = System.currentTimeMillis();
		}
		else
		{  
			m_lTotalUsage += System.currentTimeMillis() - m_lLastUsed;
		}
	}

	public boolean hasExpired( long lExpiryTimeMS )
	{  return( System.currentTimeMillis() - m_lLastUsed > lExpiryTimeMS );
	}

	public Object getItem()     { return( m_item );      }
//	public void   putItem( Object obj ) { m_item = obj;  } // be careful! not meant for poolers just caches

	public long    getHits()     { return( m_lHits );     }
	public Date   getCreated()  { return( m_dtCreated ); }
	public long   getLastUsed() { return( m_lLastUsed ); }
	public double getAverageUsage()
	{ 
		if ( m_lHits == 0 || m_lTotalUsage == 0 ) 
			return( 0.00 );
		else 
			return( ((double)m_lTotalUsage) / m_lHits );
	}

	public String toString()
	{  
		NumberFormat nf0DP = NumberFormat.getInstance();
		nf0DP.setMaximumFractionDigits(0);
		NumberFormat nf2DP = NumberFormat.getInstance();
		nf2DP.setMaximumFractionDigits(2);
		
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM HH:mm:ss");	
		
		double dAverageSecondsInUse = getAverageUsage() / 1000;
		return( (( m_bIsInUse ) ? "INUSE" : "     ") + " " +
				"Created:" + sdf.format( m_dtCreated ) + " " +
				"Last:" + sdf.format( new Date( m_lLastUsed )) + " " +
				"Hits:" + nf0DP.format( m_lHits ) + " " +				
				"Avg.Use:" + nf2DP.format(dAverageSecondsInUse) + "sec" );
	}
}

