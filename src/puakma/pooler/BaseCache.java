/** ***************************************************************
BaseCache.java
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
 * Title:        <p>
 * Description:  <p>
 * Copyright:    Copyright (c) <p>
 * Company:      <p>
 * @author
 * @version 1.0
 *
 * NOTES *****
 *
 * The BaseCache provides a thread safe mechanism to allow caching of
 * virtually any object type (See section entitled SINGLE OBJECT TYPE POOL )
 * by overriding a few simple functions.
 *
 * A CACHE is considered to be a collection of one or many different object
 * types, each identified by a single key.
 *
 * The BaseCache is a SINGLE-USE cache. That is each object in the pool can
 * only be used by one person at a time.
 *
 * While the BaseCache contains most of the functionality required for the
 * caching of objects, it is an abstract class with two methods that must be
 * overridden.
 *
 * protected abstract void freeItem( Object obj );
 *   Override this function to allow freeing of resources if the cache finds
 *   an invalid item or checkItem returns CHECK_DEAD
 *
 * protected abstract void expireItem( Object obj );
 *   Override this function to allow freeing of resources if the cache finds
 *   an expired item in the cache.
 *
 * The last function that you may want to override is checkItem(). This is
 * called before an item is locked to ensure it meets the criteria of the
 * requesting client.
 * protected boolean checkItem( Object obj )
 *
 * To retrieve an item from the cache call getItem()
 * To release the item back into the cache call releaseItem()
 *
 * WARNING!
 * The cache works on the premise that MULTIPLE threads are vying for cached
 * resources. For flexibility, the BaseCache constructor allows the client to
 * specify whether the Cache is to wait for an object to become available
 * via another threads call to notifyAll() OR to immediately throw an
 * exception if all the objects in the cache are locked. The behaviour of the
 * locking mechanism is as follows.
 * 1. If the client has specified an expiry period, remove all expired
 *    objects calling freeItem() where necessary.
 * 2. Locate the object in the pool. If it can't be found, immediately return
 *    null.
 * 3. Check to see if the object is in use. If so wait according to the
 *    requested lock wait period. If the wait expires and the item is still
 *    locked return null.
 * 4. Retrieve the payload of the pooled item. If this is null, the item is
 *    invalid and is removed from the list. Return null.
 * 5. Call checkItem. If this returns CHECK_DEAD, remove item and return null.
 *    Otherwise return the item.
 */

package puakma.pooler;

import java.util.HashMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public abstract class BaseCache extends AbstractPooler
{  
	private HashMap m_map = null;

	public BaseCache( int iMaxCount, int iLockWaitMS, int iExpirySeconds ) throws Exception
	{  
		super( iMaxCount, iLockWaitMS, iExpirySeconds );
		m_map = new HashMap();
	}

//	this will remove all elements from the pool.
	protected final synchronized void emptyPool()
	{  
		Collection coll = m_map.values();
		Iterator it = coll.iterator();
		while ( it.hasNext() ) doFree( (PooledItem)it.next() );
		m_map.clear();
	}

	private void expire()
	{  
		Set kset = m_map.keySet();
		Iterator it = kset.iterator();
		String sKey;
		while ( it.hasNext() )
		{  
			sKey = (String) it.next();
			if (sKey != null)
			{  
				if ( doExpire( (PooledItem) m_map.get( sKey ), m_bExpireInUseItems )) it.remove();
			}
			else it.remove();
		}
	}

//	returns an item.
//	If iExpirySeconds > 0 then the pooler will attempt to expire items
//	by calling FreeItem, giving the user a chance to clean up a dud
//	item. For an item to expire it's last used time must exceed the expiry
//	setting
//	locks and returns an item. Will create an item if necessary...
//	If ExpirySeconds > 0 then the pooler will attempt to expire items
//	by calling FreeItem, giving the user a chance to clean up a dud
//	item. For an item to expire it must be locked and it's in use time
//	must exceed the FExpirySeconds period. Make sure you set this up right
//	as it is possible for a slow item to exceed Expiry time yet still be
//	valid.
	public final Object getItem( String sKey ) throws Exception
	{  return( getItem( sKey, m_iLockWaitMS ) );
	}

	public final synchronized Object getItem( String sKey, int iLockWaitMS ) throws Exception
	{  PooledItem pi;
	int iCheck;
	long ltime;

	checkIsOpen();

	if ( sKey == null )
		throw new Exception( "Invalid cache request: null key." );

	iLockWaitMS = checkLockWait( iLockWaitMS );

	ltime = System.currentTimeMillis();

	expire();

	while (true)   // keep trying till times out
	{  checkIsOpen();
	pi = (PooledItem) m_map.get( sKey );
	if ( pi != null )
	{  if ( !pi.isInUse() )
	{  if ( pi.getItem() == null )
	{  doRemove( sKey );
	return( null );
	}
	else
	{  iCheck = checkItem( pi.getItem() );
	if ( iCheck == CHECK_OK )
	{  pi.setInUse( true );
	return( pi.getItem() );
	}
	else if ( iCheck == CHECK_DEAD )
	{  doRemove( sKey );
	return( null );
	}
	}
	}
	}
	else return( null );  // item does not exist in cache

	doWait( ltime, iLockWaitMS );
	}
	}

	public final synchronized void releaseItem( String sKey ) throws Exception
	{  if ( sKey != null )
	{  PooledItem pi = (PooledItem) m_map.get( sKey );
	if ( pi != null ) pi.setInUse( false );
	}
	}

	public final synchronized void addItem( String sKey, PooledItem pi ) throws Exception
	{  if ( (sKey != null) && (sKey.length() > 0) ) m_map.put( sKey, pi );
	}

	protected int checkItem( Object obj )
	{  return( CHECK_OK );
	}

	public synchronized String toString()
	{  int iCount = 0;
	String sResult = "", sKey;
	Set kset = m_map.keySet();
	Iterator it = kset.iterator();
	PooledItem pi;
	double dAv = 0.00;

	while ( it.hasNext() )
	{  sKey = (String) it.next();
	if (sKey != null)
	{  pi = (PooledItem)m_map.get( sKey );
	if ( pi != null )
	{  sResult += "           Item: " + sKey + " " + pi.toString() + "\r\n";
	dAv = pi.getAverageUsage();
	iCount++;
	}
	else sResult += "            Item: " + sKey + " Unassigned\r\n";
	}
	}
	if ( m_map.size() == 0 ) sResult = "  There are no items in the pool.\r\n";
	if ( iCount > 0 && dAv > 0.00000000001 )
		sResult += "Average Item Usage: " + ( dAv / iCount ) + " seconds";
	else sResult += "Average Item Usage: 0.00 seconds";

	sResult = toStringHeader( iCount ) + sResult;
	return( sResult );
	}

//	removes an item from the list. Free Item will be called to allow the
//	caller the opportunity to free resources. Use this to remove non
//	functioning elements. Do not free your items directly, let the pooler
//	call free item and free your items in this method. This is a last ditch
//	method, the result of check ite is a better indicator for dead items.
	public synchronized void removeItem( String sKey )
	{  
		if (( sKey != null ) && ( m_map.containsKey( sKey ) )) doRemove( sKey );
	}

	private void doRemove( String sKey )
	{  
		doFree( (PooledItem)m_map.get( sKey ) );
		m_map.remove( sKey );
	}

	public Set keySet()
	{  
		return( m_map.keySet() );
	}

}



