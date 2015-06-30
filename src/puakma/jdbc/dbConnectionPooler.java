/** ***************************************************************
dbConnectionPooler.java
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
 * Title:        dbConnectionPooler<p>
 * Description:  Thread Safe Database Connection Pooler
 * Copyright:    Copyright (c) Mike Skillicorn
 * Company:
 * @author       Mike Skillicorn
 * @version 1.0
 *
 * NOTES *****
 *
 * The dbConnectionPooler pools connections to a database according to
 * a single set of connection parameters. Each connection is defined by a
 * database driver, a database, a user name and a user password combination.
 *
 * The pooler only contains connections of a single type.
 *
 */

package puakma.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.text.NumberFormat;

import puakma.error.ErrorDetect;
import puakma.pooler.BasePooler;
import puakma.pooler.PooledItem;
import puakma.system.SystemContext;

public class dbConnectionPooler extends BasePooler implements ErrorDetect
{  
	private String m_sdbDriver;
	private String m_sdbName;
	private String m_sdbUser;
	private String m_sdbPassword;
	private SystemContext SysCtx;//ServerMonitor m_monitor;
	private int m_iMaxCount     ;
	//private int m_iLockWaitMS   ;
	private int m_iInitialCount ;
	//private int m_iExpirySeconds;

	public dbConnectionPooler( int iMaxCount, int iLockWaitMS, int iInitialCount,
			int iExpirySeconds, String sdbDriver, String sdbName,
			String sdbUser, String sdbPassword, SystemContext paramSysCtx ) throws Exception
			{  super( iMaxCount, iLockWaitMS, iExpirySeconds );
			SysCtx = paramSysCtx;
			// ensure the driver is registered
			m_sdbDriver   = sdbDriver;
			Class.forName( m_sdbDriver );
			// capture connection parameters

			m_sdbName     = sdbName;
			m_sdbUser     = sdbUser;
			m_sdbPassword = sdbPassword;
			m_iMaxCount     = iMaxCount;
			m_iLockWaitMS   = iLockWaitMS;
			m_iInitialCount = iInitialCount;
			m_iExpirySeconds= iExpirySeconds;
			// create initial items in pool
			initialise( iInitialCount );
			//System.out.println("new pooler: "+m_sdbName);
			}

	/**
	 * Return the database version and name
	 */
	public String getDatabaseInfo()
	{
		String sDBInfo="Unknown Database";
		Connection cx = null;
		try
		{
			cx = getConnection();
			DatabaseMetaData dbmd = cx.getMetaData();
			sDBInfo = dbmd.getDatabaseProductName() + " " + dbmd.getDatabaseProductVersion() + " jdbc_ver="+dbmd.getDriverMajorVersion()+"."+dbmd.getDatabaseMinorVersion();
		}
		catch(Exception e){}
		finally
		{
			releaseConnection(cx);
		}

		return sDBInfo;
	}

	/**
	 *
	 */
	public String getStatus()
	{
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(0);
		String sDBConn = m_sdbName + " (" + m_sdbUser + ") ";
		StringBuilder sb = new StringBuilder(64);
		String sLine1 = sDBConn+nf.format(m_iInitialCount) + "/"+nf.format(m_vPool.size()) + "/"+nf.format(m_iMaxCount)+ " inUse=" + nf.format(getUsedItemCount()) + " hits=" + nf.format(getPoolUsedCount()) + "\r\n";
		for(int i=0; i<m_vPool.size(); i++)
		{
			PooledItem pi = (PooledItem)m_vPool.elementAt(i);
			Connection cx = (Connection)pi.getItem();
			if(cx==null)
				m_vPool.remove(i);
			else
			{
				sb.append("  " + (i+1) + ". ");
				/*if(pi.isInUse()) sb.append("INUSE");
				Date dtLast = new Date(pi.getLastUsed());
				sb.append(" Last used: "+ Util.formatDate(dtLast, "d.MMM.yy HH:mm:ss"));
				sb.append(" Hits: "+ pi.getHits());*/
				sb.append(pi.toString());	
				sb.append("\r\n");
			}
		}
		
		return sLine1 + sb.toString() + "  ==>> " + getDatabaseInfo() + "\r\n";
	}

	public java.sql.Connection getConnection() throws Exception
	{  return( (java.sql.Connection)getItem() );
	}

	private void clearConnection( Connection cnx )
	{
		try
		{  // make sure the changes are committed and the warnings are cleared
			// before returning the cnx to the pool
			if ( !cnx.isClosed() )
			{  if ( !cnx.getAutoCommit() ) cnx.commit();
			cnx.clearWarnings();
			}
		}
		catch(Exception E)
		{  // exception somewhere : bail out and remove cnx
			// to be recreated if required
			try { if (!cnx.isClosed() ) cnx.close();  }
			catch (Exception e )
			{  
				SysCtx.doError("Trapped exception clearing connection. %s", new String[]{e.toString()}, this );
			}
		}
	}

	public void releaseConnection( Connection cnx )
	{
		if(cnx==null) return;
		clearConnection( cnx );
		releaseItem( cnx );
	}

	public void closeAllConnections()
	{
		for(int i=0; i<m_vPool.size(); i++)
		{
			PooledItem pi = (PooledItem)m_vPool.elementAt(i);
			Connection cx = (Connection)pi.getItem();
			if(cx!=null)
			{
				//SysCtx.doInformation("--- CLEARING CONNECTION ", this );
				clearConnection(cx);
				try {cx.close(); }
				catch(Exception e){}
			}
		}
	}

	/**
	 * Force all connections to be closed
	 */
	public synchronized void destroy()
	{
		for(int i=0; i<m_vPool.size(); i++)
		{
			PooledItem pi = (PooledItem)m_vPool.elementAt(i);
			Connection cx = (Connection)pi.getItem();
			if(cx!=null)
			{
				//SysCtx.doInformation("--- CLEARING CONNECTION ", this );
				clearConnection(cx);
				try {cx.close(); }
				catch(Exception e){}
			}
		}
		super.shutdown();
	}


	public synchronized void doExpire()
	{
		for(int i=0; i<m_vPool.size(); i++)
		{
			PooledItem pi = (PooledItem)m_vPool.elementAt(i);			
			if(pi!=null)
			{
				if(doExpire(pi, false)) 
				{		
					m_vPool.removeElementAt(i);
					//System.out.println("item expired... " + pi.toString());
				}
			}
		}		
	}

	//...........................................................................
	/**
	 * Under heavy load the DriverManager may not find the db server on the first attempt.
	 * This function has been modified to retry the connect a number of times.
	 */
	protected Object createItem() throws Exception
	{  //return( DriverManager.getConnection( m_sdbName, m_sdbUser, m_sdbPassword ));

		//System.out.println("createItem() " + m_sdbName);
		Connection cx=null;
		int iRetries=1; //2
		int i=0;
		String szMessage="";

		for(i=0; i<iRetries; i++)
		{
			//if(i>0) SysCtx.doInformation("DRIVERMANAGER retrying... "+m_sdbName, this );
			try
			{
				cx =  DriverManager.getConnection( m_sdbName, m_sdbUser, m_sdbPassword );
			}
			catch(Exception e)
			{
				szMessage = e.toString();
			}
			if(cx==null)
			{
				/*
				 * BJU 10.aug.09
				 * This is indeed a difficult choice. When the db does run out of connections, repeated calls to gc()
				 * eventually cause a java.lang.OutOfMemoryError: GC overhead limit exceeded
				 * I have changed it to now just bail out if on initial request a connection cannot be found, rather than a gc() and retry
				 */
				
				//set a random wait time up to 1.5 seconds
				// the wait is so other threads will retry at different intervals
				// note: random may return 0!! - therefore there will be NO wait period
				int iWait = (int)(Math.random()*1500);
				//System.gc(); //this will run the finalizer on the systemcontext objects
				//and hopefully free up some connections
				try{ Thread.sleep(iWait); } catch(Exception x){}
			}
			else
				break;
		}//for

		if(cx==null)
		{
			throw new Exception(szMessage);
		}

		return cx;
	}

	protected void freeItem( Object obj )
	{  
		java.sql.Connection cnx = (java.sql.Connection)obj;
		clearConnection( cnx );
		try { if (!cnx.isClosed() ) cnx.close();  }
		catch (Exception e )
		{
			SysCtx.doError("Trapped exception freeing item. %s", new String[]{e.toString()}, this );
		}
	}

	protected void expireItem( Object obj )
	{  
		java.sql.Connection cnx = (java.sql.Connection)obj;
		clearConnection( cnx );
		try { if (!cnx.isClosed() ) cnx.close();  }
		catch (Exception e )
		{
			SysCtx.doError("Trapped exception expiring item. %s", new String[]{e.toString()}, this );
		}
	}

	protected int checkItem( Object obj )
	{  
		java.sql.Connection cnx = (java.sql.Connection)obj;      
		try
		{  if ( cnx.isClosed() ) return( BasePooler.CHECK_DEAD );
		else return( BasePooler.CHECK_OK );
		}
		catch (Exception e) 
		{  return( BasePooler.CHECK_DEAD );
		}
	}

	public String getErrorSource()
	{
		return "dbConnectionPooler";
	}

	public String getErrorUser()
	{
		return "System";
	}
}

