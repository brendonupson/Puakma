/** ***************************************************************
BasePooler.java
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
 * Title:        BasePooler<p>
 * Description:  Thread Safe Generic Object Pooling Base Class<p>
 * Copyright:    Copyright (c) Mike Skillicorn<p>
 * Company:
 * @author  Mike Skillicorn
 * @version 1.0
 *
 * NOTES *****
 *
 * The BasePooler provides a thread safe mechanism to allow pooling of
 * virtually any object type (See section entitled SINGLE OBJECT TYPE POOL )
 * by overriding a few simple functions.
 *
 * A POOL is considered to be a collection of identical object types.
 * The BasePooler is a SINGLE-USE pool. That is each object in the pool can
 * only be used by one person at a time.
 *
 * While the BasePooler contains most of the functionality required for the
 * pooling of objects, it is an abstract class with two methods that must be
 * overridden.
 *
 * >> createItem()
 * When the capacity of the pool less than the maximum and a request for a
 * pooled item is made and all other items in the pool are locked, the pooler
 * will attempt to add a new item to the pool by calling createItem() as:
 *   protected abstract Object createItem();
 * You must override this function within your decendant class so that it
 * creates an item that you wish to pool and returns the item. The pooler takes
 * care of adding the item to the pool and keeping track of statistical
 * information regarding the usage of the object. For example to pool a thread
 * object your function may look like:
 *   protected Object createItem()
 *   {  return( new Thread() );
 *   }
 *
 * >> freeItem
 * Whenever an object is removed from the pool the pooler calls freeItem()
 * to give the client the opportunity to shutdown or clean up resources
 * associated with the pooled object. The function is defined as:
 *   protected abstract void freeItem( Object obj );
 * Not that this function is not call when an object is "released" back into
 * the pool.
 *
 * The last function that you may want to override is checkItem(). This is
 * called before an item is locked to ensure it meets the criteria of the
 * requesting client. The form is
 *   protected boolean checkItem( Object obj )
 * One example of the use of this method is to check a connection is not
 * closed to ensure you get a usable connection.
 *
 * To retrieve an item from the pool call lockItem()
 * To release the item back into the pool call releaseItem()
 *
 * WARNING!
 * The pooler works on the premise that MULTIPLE threads are vying for pooled
 * resources. For flexibility, the BasePooler constructor allows the client to
 * specify whether the Pooler is to wait for an object to become available
 * via another threads call to notifyAll() OR to immediately throw an
 * exception if all the objects in the pool are locked. The behaviour of the
 * locking mechanism is as follows.
 * 1. If the client has specified an expiry period, remove all expired
 *    objects calling freeItem() where necessary.
 * 2. If an unused object exists, find it, call checkItem() on it to make sure
 *    it's OK and if so, lock it and return it.
 * 3. If all items are locked and there's room, create a new pooled object and
 *    return the new object.
 * 4. If LOCK_NO_WAIT has been specified in the constructor, raise a "No
 *    available items" exception.
 * 5. Otherwise put the thread into a wait state pending another thread
 *    releasing an item. Here's where it gets interesting. When you call
 *    wait() the thread releases it's hold on the monitor and waits to be
 *    notified. This allows another thread to enter the synchronized method.
 *    When notify() or notifyAll() is called, the thread with the highest
 *    priority will start to run. If all the threads have the same priority
 *    they MAY all run OR the first available thread may run. Regardless,
 *    there is no guarantee which thread will continue BUT the lockItem()
 *    function is not re-entrant until a thread has a lock on an item.
 *    Given that local variables survive on the stack for each thread, the
 *    time of the wait can be measured. So it's possible to bail out of a
 *    deadlocked situation by specifying a suitable iLockWaitMS value in
 *    the constructor.
 *    Note that only when a thread is interrupted (interrupt()) will
 *    the thread raise an InterruptedException.
 *
 * In a multi-threaded environment there shouldn't be a problem as eventually
 * a thread will release an item in the pool and the only penalty will be
 * large wait times. In a single threaded environment it make NO sense to
 * be using a wait/notify mechanism (How can a single thread wake itself up??)
 * and LOCK_NO_WAIT should be specified in the iLockWaitMS parameter to the
 * constructor. Otherwise, specify the maximum time you want to wait in
 * milliseconds for the iLockWaitMS parameter and the lockItem() method will
 * raise an exception after that period.
 *
 * Exceptions are not caught in fatal cases.
 *
 * Some methods are marked final to stop the methods being overidden and
 * compromising the functionality of the pooler and also for performance.
 *
 * I had thought of incorporating load balancing but who really cares, it's
 * just time sliced CPU anyway.
 *
 * The pool is simply a vector full of PooledItem objects. PooledItem object
 * contain a reference to the object being pooled.
 *
 * SINGLE OBJECT TYPE POOL
 * I originally wrote the pooler with the concept of having multiple types
 * of objects in the pool, accessable by specifying a particular tag or
 * alias to identify a type of object in the pool, eg multiple connections
 * to a database with different connection parameters. However on
 * consideration, this has the potential for disaster under the following
 * conditions.
 * 1. A heavily used object type fills the pool before a less used object type
 *    has a chance to instantiate itself in the pool, leading to the less used
 *    object type being completely unavailable until a heavily used object
 *    is destroyed.
 * 2. Even if the pool is seeded with a proportionate range of objects types,
 *    there is no guarantee that some of the less used types may expire or be
 *    destroyed, leading to condition 1.
 * 3. Trying to proportion the seed number for different object types is
 *    heading into the abyss of complexity unneccessarily.
 * 4. Mixing types means access to less used types is controlled by the
 *    contention for heavily used types causing unneccessary delays on
 *    requests to less used types.
 * 5. Multiple object type pools just don't scale.
 *
 * SO, The pooler only pools objects of a single type. I've written a
 * database connection pool manager class to co-ordinate the pooling of multiple
 * connections with varying connection parameters.
 *
 *
 * NOTES ON THREADS
 * 1. You can only call wait(), notify() and notifyAll() from within
 *    a synchronized function otherwise you get a monitor ownership exception.
 * 2. Each call to wait() MUST be balanced with a call to notify().
 * 3. sleep() does not have to be in a synchronized method but the
 *    sleeping thread cannot be notified.
 *
 */

package puakma.pooler;

import java.text.NumberFormat;
import java.util.Vector;


public abstract class BasePooler extends AbstractPooler
{
	protected Vector m_vPool = null;

	public BasePooler( int iMaxCount, int iLockWaitMS, int iExpirySeconds ) throws Exception
	{  
		super( iMaxCount, iLockWaitMS, iExpirySeconds );
		m_vPool = new Vector();
	}

	// initialise the pool with a number of pre created objects
	public void initialise( int iInitialCount ) throws Exception
	{  
		if ( iInitialCount > 0 )
		{  
			for ( int i=0; i<iInitialCount; i++ ) m_vPool.add( new PooledItem( createItem() ) );
		}
	}

	protected final synchronized void emptyPool()
	{  
		int i = m_vPool.size() - 1;
		while ( i > -1 ) removeItemAt( i-- );
	}

	public final Object getItem() throws Exception
	{  
		return( getItem( m_iLockWaitMS ) );
	}

	/**
	 * gets a count of the items that are currently marked as in use.
	 * @return
	 */
	public int getUsedItemCount()
	{
		int iUseCount=0;
		for(int i=0; i<m_vPool.size(); i++)
		{
			PooledItem pi = (PooledItem)m_vPool.elementAt(i);
			if(pi.isInUse()) iUseCount++;
		}
		return iUseCount;
	}

	/**
	 * gets a count of the items that have been used in this pool.
	 * @return
	 */
	public long getPoolUsedCount()
	{
		long lUseCount=0;
		for(int i=0; i<m_vPool.size(); i++)
		{
			PooledItem pi = (PooledItem)m_vPool.elementAt(i);
			lUseCount+=pi.getHits();
		}
		return lUseCount;
	}

	// locks and returns an item. Will create an item if necessary...
	// If ExpirySeconds > 0 then the pooler will attempt to expire items
	// by calling FreeItem, giving the user a chance to clean up a dud
	// item. For an item to expire it must be locked and it's in use time
	// must exceed the FExpirySeconds period. Make sure you set this up right
	// as it is possible for a slow item to exceed Expiry time yet still be
	// valid.
	public final synchronized Object getItem( int iLockWaitMS ) throws Exception
	{  
		PooledItem pi;
		int i, iCheck;
		long ltime;

		//System.out.println("-------------------");
		//long lStart = System.currentTimeMillis();
		checkIsOpen();
		//System.out.println("checkIsOpen() " + (System.currentTimeMillis()-lStart) + "ms");

		iLockWaitMS = checkLockWait( iLockWaitMS );


		ltime = System.currentTimeMillis();




		while (true)   // keep trying till times out
		{  
//			expire locks
			if ( m_iExpirySeconds > NO_EXPIRY )
			{  
				i= m_vPool.size() - 1;
				while ( i > -1 )
				{  
					if ( doExpire( getPooledItem(i), m_bExpireInUseItems )) m_vPool.remove( i );
					i--;
				}
			}

			// try and lock an item
			checkIsOpen();

			i = m_vPool.size() - 1;
			while ( i > -1 )
			{              
				pi = getPooledItem(i);
				if ( pi != null )
				{  
					if ( !pi.isInUse() )
					{  
						if ( pi.getItem() == null ) 
							removeItemAt( i );
						else
						{  
							iCheck = checkItem( pi.getItem() );                     
							if ( iCheck == CHECK_OK )
							{  
								pi.setInUse( true );                       
								return( pi.getItem() );
							}
							else if ( iCheck == CHECK_DEAD ) removeItemAt( i );
						}
					}
				}
				else 
					removeItemAt( i );
				i--;
			}

			// couldn't find one so create a new pooled item here
			//System.out.println("CONN NOT POOLED, CREATING NEW ONE m_iMaxCount="+m_iMaxCount + " poolsize="+m_vPool.size());
			if (( m_iMaxCount <= 0 ) || ( m_vPool.size() < m_iMaxCount ))
			{  
				pi = new PooledItem( createItem() );
				pi.setInUse( true );
				m_vPool.add( pi );
				return( pi.getItem() );
			}

			doWait( ltime, iLockWaitMS );
		}
	}

	// put an item back in the pool once you're done with it...
	public final synchronized void releaseItem( Object obj )
	{  
		//System.out.println("releaseItem()");
		int i = getItemIndex( obj );
		if ( i >= 0 )
		{  
			getPooledItem(i).setInUse( false );
			notifyAll();    // notify any waiting threads there's a slot available
		}
	}

	public synchronized String asString()
	{  
		PooledItem pi;
		int i, iCount = 0;
		String sResult, sItem;
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits( 4 );
		nf.setGroupingUsed( false );
		double dAv = 0.00;

		sResult = toStringHeader( m_vPool.size() );

		for ( i=0; i < m_vPool.size(); i++ )
		{  pi = getPooledItem(i);
		sItem = nf.format( i );
		if ( pi != null )
		{  sResult += "              Item: " + sItem + " " + pi.toString() + "\r\n";
		dAv = pi.getAverageUsage();
		iCount++;
		}
		else sResult = sResult + "               Item: " + sItem + " Unassigned\r\n";
		}
		if ( m_vPool.size() == 0 ) sResult = "  There are no items in the pool.\r\n";
		if ( iCount > 0 && dAv > 0.00000000001 )
			sResult += "Average Item Usage: " + ( dAv / iCount ) + " seconds";
		else sResult += "Average Item Usage: 0.00 seconds";

		return( sResult );
	}

	// removes an item from the list. Free Item will be called to allow the
	// caller the opportunity to free resources. Use this to remove non
	// functioning elements. Do not free your items directly, let the pooler
	// call free item and free your items in this method. This is a last ditch
	// method, the result of check ite is a better indicator for dead items.
	public synchronized void removeItem( Object obj )
	{  
		int i = getItemIndex( obj );
		if ( i >= 0 ) removeItemAt( i );
	}


	/**
	 * Determines if an itme exists in this pool
	 */
	public synchronized boolean hasItem( Object obj )
	{
		int i = getItemIndex( obj );
		if(i>=0) return true;
		return false;
	}

	// PROTECTED ----------------------------------------------------------------
	// override these two methods to add and cleanup items in the pool
	protected abstract Object createItem() throws Exception;

	// override this method if an item needs to be matched as in the
	// case of a db connection string
	protected int checkItem( Object obj )
	{  return( CHECK_OK );
	}

	// PRIVATE ------------------------------------------------------------------
	private PooledItem getPooledItem( int index )
	{  
		return( (PooledItem)( m_vPool.get(index) ) );
	}

	// list must be locked before you call this!!!
	private int getItemIndex( Object obj )
	{  
		PooledItem pi;
		int i = 0;
		while ( i < m_vPool.size() )
		{  
			pi = getPooledItem(i);
			if (( pi != null ) && ( pi.getItem() == obj )) return( i );
			i++;
		}
		return( -1 );
	}

	private void removeItemAt( int index )
	{  
		doFree( getPooledItem(index) );
		m_vPool.remove(index);
	}
}

