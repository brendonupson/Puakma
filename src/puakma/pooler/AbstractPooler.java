/** ***************************************************************
AbstractPooler.java
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
 * Title:        AbstractPooler<p>
 * Description:  Thread Safe Pool/Cache abstract class<p>
 * Copyright:    Copyright (c) Mike Skillicorn<p>
 * Company:
 * @author  Mike Skillicorn
 * @version 1.0
 *
 * NOTES *****
 *
 * The AbstractPooler provides basic functionality for SINGLE-USE pooling or
 * caching of virtually any object type. That is each object in the pool or
 * cache can only be used by one person at a time. See BasePooler and BaseCache
 * for implementation details.
 */

package puakma.pooler;



public abstract class AbstractPooler
{  
	private static final int  MIN_COUNT = 1;

//	throw if an item is not available
	public static final int  LOCK_NO_WAIT        = 0;
//	wait for notify when pool is full an locked
	public static final int  LOCK_WAIT_ON_NOTIFY = -1;
//	no inital pool
	public static final int  NO_INITIAL_COUNT    = 0;
//	no object expiry
	public static final int  NO_EXPIRY           = 0;
//	pool can grow without constraint
	public static final int  UNLIMITED_SIZE      = -1;

	public static final int  DEFAULT_POOL_SIZE = 20;
	public static final int  DEFAULT_LOCK_WAIT = 10000;   // 10 seconds

//	these are expected in the return by checkItem()
	public static final int CHECK_OK   = 1;   // yes this object is OK
	public static final int CHECK_DEAD = -1;  // this one's dead, get rid of it.
	public static final int CHECK_PASS = 0;   // close but no cigar, keep looking

//	maximum size of the pool
	protected int m_iMaxCount;
//	the time to wait in milliseconds before a locked out exception is raised
	protected int m_iLockWaitMS;
//	how long before a pooled item expires
	protected int m_iExpirySeconds;
	private boolean m_bIsClosed;
	protected boolean m_bExpireInUseItems;

	public AbstractPooler( int iMaxCount, int iLockWaitMS, int iExpirySeconds ) throws Exception
	{  
		m_bIsClosed = false;
		m_bExpireInUseItems = false;
		setMaxCount( iMaxCount );
		m_iLockWaitMS = checkLockWait( iLockWaitMS );
		m_iExpirySeconds = ( iExpirySeconds > 0 ) ? iExpirySeconds : NO_EXPIRY;
	}

	protected int checkLockWait( int iLockWaitMS )
	{  if ( iLockWaitMS < 0 ) return( LOCK_WAIT_ON_NOTIFY );
	else if ( iLockWaitMS == 0 ) return( LOCK_NO_WAIT );
	else if ( iLockWaitMS < 500 ) return( DEFAULT_LOCK_WAIT );
	else return( iLockWaitMS );
	}

	/*protected int checkLockWait( int iLockWaitMS )
   {  if ( iLockWaitMS < 500 || iLockWaitMS != LOCK_WAIT_ON_NOTIFY )
      {  if ( iLockWaitMS < 0 ) return( LOCK_NO_WAIT );
         else return( DEFAULT_LOCK_WAIT );
      }
      else return( iLockWaitMS );
   }*/

	public int getMaxCount()      {  return( m_iMaxCount ); }
	public void setMaxCount( int iMaxCount )
	{  if ( iMaxCount > 0 )
	{  m_iMaxCount = ( iMaxCount < MIN_COUNT ) ? MIN_COUNT : iMaxCount;
	}
	else m_iMaxCount = UNLIMITED_SIZE;   // no maximum... unlimited pool
	}

	public int getLockWaitMS()    {  return( m_iLockWaitMS ); }
	public void setLockWaitMS( int iLockWaitMS )
	{  m_iLockWaitMS = checkLockWait( iLockWaitMS );
	}

	public int getExpirySeconds() {  return( m_iExpirySeconds ); }
	public void setExpirySeconds( int iExpirySeconds )
	{  m_iExpirySeconds = ( iExpirySeconds > 0 ) ? iExpirySeconds : NO_EXPIRY;
	}

	public void setExpireInUseItems( boolean bExpire ) {  m_bExpireInUseItems = bExpire; }
	public boolean getExpireInUseItems() {  return( m_bExpireInUseItems ); }

	public boolean isClosed() { return( m_bIsClosed ); }

	public synchronized final void shutdown()
	{  m_bIsClosed = true;
//	notify threads that the pool is being cleared out
	notifyAll();
//	wait for half a second for waiting threads to clear out
	try   {  wait( 500 );  }
	catch ( Exception e ) {}
//	then clear it out
	emptyPool();
	}

	public final void restart()
	{  m_bIsClosed = false;
	}

	protected void doWait( long ltime, int iLockWait ) throws Exception
	{  // couldn't find a free item and couldn't add one....
		if ( iLockWait == LOCK_NO_WAIT )
			throw new Exception( "Could not lock item in pool. Pooler too busy." );
		else if ( iLockWait == LOCK_WAIT_ON_NOTIFY )
		{  // this option continually waits until a notify() is called.
			// if an interrupt is thrown on the thread the exception will be re-raised.
			wait();
		}
		else
		{  // this option will wait for a specified time OR until a notify() is called.
			// if an interrupt is thrown on the thread the exception will be re-raised.
			wait( iLockWait );
			// if we waited the whole time bail. that is, we weren't notify()-ed...
			if (( System.currentTimeMillis() - ltime ) > iLockWait - 50 )
				throw new Exception( "Pooler timed out waiting for a lock." );
		}
	}

	protected boolean doExpire( PooledItem pi, boolean bExpireInUseItems )
	{  
		if ((pi != null))
		{  
			if ( ( bExpireInUseItems || !pi.isInUse() ) &&
					( pi.hasExpired(m_iExpirySeconds * 1000)) )
			{  
				try 
				{  
					if ( pi.getItem() != null ) expireItem( pi.getItem() );  }
				catch (Exception e) {}
			}
			else return( false );
		}
		return( true );  // pi = null or expired
	}

	protected void doFree( PooledItem pi )
	{  if ( pi != null )
	{  try {  if ( pi.getItem() != null ) freeItem( pi.getItem() );  }
	catch (Exception e) {}
	}
	}

	protected String toStringHeader( int iSize )
	{  return( "******* CLASS NAME: " + getClass().getName() + "\r\n" +
			"       Pool Status: " + ((m_bIsClosed) ? "closed" : "open") + "\r\n" +
			" Maximum Pool Size: " + m_iMaxCount + "\r\n" +
			"  Actual Pool Size: " + iSize + "\r\n" +
			"    Lock Wait Time: " + m_iLockWaitMS + " milliseconds\r\n" +
			"       Expiry Time: " + m_iExpirySeconds + "\r\n" );
	}

	protected void checkIsOpen() throws Exception
	{  if ( m_bIsClosed )
		throw new Exception( "Invalid request: Pool is closed." );
	}

	protected abstract void emptyPool();
	protected abstract void freeItem( Object obj );
	protected abstract void expireItem( Object obj );

}

