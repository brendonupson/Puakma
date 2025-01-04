/** ***************************************************************
dbConnectionPoolManager.java
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
package puakma.jdbc;

import java.sql.Connection;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;


public class dbConnectionPoolManager implements ErrorDetect
{
	//private boolean m_bStopped;
	private Hashtable m_map = new Hashtable();
	private SystemContext m_sysCtx;
	private dbConnectionCleaner m_Cleaner;
	private boolean m_bRunning = true;
	private String m_sPoolName = "";


	private class dbConnectionCleaner extends Thread implements ErrorDetect
	{
		private boolean m_bCleanerRunning = true;
		//private dbConnectionPoolManager m_mgr;


		public dbConnectionCleaner()//(dbConnectionPoolManager mgr)
		{
			super("dbConnectionCleaner:"+m_sPoolName);
			//m_mgr = mgr;
			this.setDaemon(true);
		}

		public void destroy() 
		{
			m_bCleanerRunning = false;
			this.interrupt();
		}

		public String getThreadDetail() 
		{			
			return getErrorSource();
		}

		public void run() 
		{
			//System.out.println("STARTUP: dbConnectionCleaner ");
			final int iMinimumTimeMS = 30000;
			while(m_bCleanerRunning)
			{				
				try 
				{
					long lRandom = (long) (iMinimumTimeMS*Math.random());
					long lSleep = lRandom + iMinimumTimeMS;
					//System.out.println("sleeping for " + lSleep + "ms");
					Thread.sleep(lSleep); //so all poolers don't clean together, between 30sec and a minute per clean
					if(!isRunning()) break;
					doExpire();
				} 
				catch (InterruptedException e) 
				{				
					//System.out.println("Interrupted!");
				}
				catch (Throwable t) 
				{				
					//eg out of memory?
					//System.out.println(t.toString());
				}
				//System.out.println("Cleaning... ");				
			}
			//System.out.println("SHUTDOWN: "+this.getName());			
		}

		
		/**
		 * 
		 */
		public String getErrorSource() 
		{
			return this.getClass().getName();
		}

		public String getErrorUser() 
		{			
			return pmaSystem.SYSTEM_ACCOUNT;
		}

	}



	// **************************************************
	// **************************************************
	// **************************************************
	


	/**
	 * 
	 */
	public dbConnectionPoolManager( SystemContext paramSysCtx, String sPoolName )
	{
		if(sPoolName!=null) m_sPoolName = sPoolName;
		m_sysCtx = paramSysCtx;
		m_Cleaner = new dbConnectionCleaner();
		m_Cleaner.start();
	}

	public boolean isRunning() 
	{
		return m_bRunning;
	}

	public boolean createPooler(  String sAlias, int iMaxCount, int iLockWaitMS, int iInitialCount,
			int iExpirySeconds, String sdbDriver, String sdbName,
			String sdbUser, String sdbPassword ) throws Exception
			{
		if (!m_map.containsKey( sAlias.trim().toLowerCase()) )
		{
			m_map.put( sAlias.trim().toLowerCase(), new dbConnectionPooler(
					iMaxCount, iLockWaitMS, iInitialCount, iExpirySeconds,
					sdbDriver, sdbName, sdbUser, sdbPassword, m_sysCtx ));
			return( true );
		}
		return( false );
			}

	/**
	 * 
	 * @param sAlias
	 * @return
	 * @throws Exception
	 */
	public dbConnectionPooler getPooler( String sAlias ) throws Exception
	{  
		dbConnectionPooler pool = (dbConnectionPooler)m_map.get( sAlias.trim().toLowerCase() );
		if ( pool != null ) return( pool );
		else throw new Exception( "Invalid call to getPooler(). Alias [" + sAlias + "] does not exist." );
	}


	public boolean hasPool( String sAlias )
	{
		dbConnectionPooler pool = (dbConnectionPooler)m_map.get( sAlias.trim().toLowerCase() );
		if(pool!=null) return true;
		return false;
	}

	public boolean removePooler( String sAlias )
	{  
		String s = sAlias.trim().toLowerCase();
		if ( m_map.containsKey( s ) )
		{  
			try
			{  
				dbConnectionPooler pool = (dbConnectionPooler)m_map.get( s );
				if ( pool != null ) pool.destroy();
			}
			catch (Exception e )
			{
				m_sysCtx.doError("Trapped exception shutting down pool.", this);
			}
			m_map.remove( s );
			return( true );
		}
		return( false );
	}

	public synchronized java.sql.Connection getConnection( String sAlias ) throws Exception
	{		
		if(sAlias==null) return null;

		String s = sAlias.trim().toLowerCase();
		if ( !m_map.containsKey( s ) )
			throw new Exception( "Error getConnection(). Alias: " + sAlias + " has not been registered." );

		dbConnectionPooler pooler = (dbConnectionPooler)m_map.get( s );
		Connection cx = pooler.getConnection();
		//set autocommit ??
		//the next line ensures a pool created with bad credentials etc will be removed from the manager
		//thus the next time it is called a new pool will be created
		if(cx==null && pooler.getUsedItemCount()==0) removePooler(sAlias);
		return cx;

	}

	public synchronized void releaseConnection( String sAlias, Connection cnx ) throws Exception
	{  
		String s = sAlias.trim().toLowerCase();
		if ( !m_map.containsKey( s ) )
			throw new Exception( "Error releaseConnection(). Alias: " + sAlias + " has not been registered." );
		((dbConnectionPooler)m_map.get( s )).releaseConnection( cnx );
	}


	/**
	 * This method looks in each pool and releases the passed connection
	 */
	public synchronized boolean releaseConnection( Connection cnx )
	{
		if(cnx==null) return false;

		Enumeration en = m_map.elements();
		while ( en.hasMoreElements() )
		{
			dbConnectionPooler pool = (dbConnectionPooler)en.nextElement();
			if(pool!=null && pool.hasItem(cnx))
			{
				m_sysCtx.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Releasing Connection: "+ Thread.currentThread().getName(), this);
				pool.releaseConnection(cnx);
				return true;
			}
		}
		return false;
	}

	/**
	 * This *may* slow performance, as the gc thread has to free the resources, but
	 * this will stop the database server getting cranky when we have thousands of
	 * open connections.
	 */
	protected void finalize()
	{
		//System.out.print("fin.");
		shutdown();
	}


	public synchronized void shutdown()
	{  
		m_bRunning = false;
		m_Cleaner.destroy();

		Collection coll = m_map.values();
		Iterator it = coll.iterator();
		m_sysCtx.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Shutting down connection pool manager.", this );
		//m_bStopped = true;
		while ( it.hasNext() )
		{  
			dbConnectionPooler pool = (dbConnectionPooler)it.next();
			try   
			{ 
				if ( pool != null ) pool.destroy(); 
			}
			catch (Exception e )
			{
				m_sysCtx.doError("Error shutting down pool: " + e.toString(), this);
			}
		}
	}

	/**
	 * @deprecated
	 */
	public synchronized void zz_restart()
	{
		m_sysCtx.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Restarting connection pool manager.", this);
		//m_bStopped = false;

		Enumeration en = m_map.elements();
		while ( en.hasMoreElements() )
		{
			dbConnectionPooler pool = (dbConnectionPooler)en.nextElement();
			try   {  if ( pool != null ) pool.restart(); }
			catch (Exception e )
			{
				m_sysCtx.doError("Trapped exception shutting down pool.", this);
			}
		}
	}
	
	public synchronized void reset()
	{
		m_sysCtx.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "Resetting connection pool manager.", this);
		//m_bStopped = false;

		Enumeration en = m_map.elements();
		while ( en.hasMoreElements() )
		{
			dbConnectionPooler pool = (dbConnectionPooler)en.nextElement();
			try   {  if ( pool != null ) pool.shutdown(); }
			catch (Exception e )
			{
				m_sysCtx.doError("Trapped exception shutting down pool.", this);
			}
		}
		
		m_map.clear(); //remove all pools		
	}


	/**
	 * Remove elements from the pool if they have expired. 
	 */
	public synchronized void doExpire()
	{
		Enumeration en = m_map.elements();
		while ( en.hasMoreElements() )
		{
			dbConnectionPooler pool = (dbConnectionPooler)en.nextElement();
			if ( pool != null ) pool.doExpire();			
		}
	}
	

	/**
	 *
	 * @return
	 */
	public String toString()
	{
		return getStatus();
	}

	/**
	 *
	 * @return
	 */
	public String getStatus()
	{
		StringBuilder sb = new StringBuilder(256);
		sb.append("------ DB POOL STATUS ------ (" + m_map.size() + ") \r\n");
		Enumeration en = m_map.elements();
		while ( en.hasMoreElements() )
		{
			dbConnectionPooler pool = (dbConnectionPooler)en.nextElement();
			if(pool!=null) sb.append(pool.getStatus());
		}

		sb.append("----------------------------\r\n");
		return sb.toString();

	}


	public String getErrorSource()
	{
		return "dbConnectionPoolManager";
	}

	public String getErrorUser()
	{
		return "System";
	}

	public int getPoolCount() 
	{
		return m_map.size();
	}

}



