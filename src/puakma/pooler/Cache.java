/** ***************************************************************
Cache.java
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
package puakma.pooler;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is a thread aware cache.
 * We use instanceof so that we can only add objects with the CacheableItem interface
 * total cache accesses = hits + misses.
 * the cache has a max size in bytes. An item will only be added if the size of the item
 * will fit in the cache.
 *
 * This implements CacheableItem so that we can store a cache in the cache ;-)
 */
public class Cache implements CacheableItem
{
	private Hashtable<String, CacheableItem> m_htItems = new Hashtable<String, CacheableItem>();
	//private double m_dSize=0; cache elements may grow in size!!
	private double m_dMaxSize=-1;
	private final AtomicLong m_lCacheHits = new AtomicLong();
	private final AtomicLong m_lCacheMisses = new AtomicLong();
	private long m_lCacheExpiryTime=1800000L; //30minutes
	//getItem()/addItem() used to scan the whole cache on every call. Only sweep this often.
	private static final long EXPIRE_SWEEP_INTERVAL_MS = 5000;
	private final AtomicLong m_lLastExpireSweep = new AtomicLong();
	private String m_sCacheKey=null;


	public Cache()  {}

	public Cache(double dMaxSize)
	{
		m_dMaxSize = dMaxSize;
	}


	/**
	 * determine if the item will fit in the cache
	 */
	private boolean itemCanFit(double dNewSize)
	{
		if(m_dMaxSize<0) return true;
		if(dNewSize+getCacheSize() <= m_dMaxSize) return true;

		return false;
	}

	/**
	 * Sets the maximum size of the cache
	 */
	public synchronized void setCacheMaxSize(double dNewSize)
	{
		m_dMaxSize = dNewSize;
	}

	/**
	 * Sets the expiry time of all elements in the cache
	 * in milliseconds
	 */
	public synchronized void setCacheExpiryTime(long lNewTimeMS)
	{
		m_lCacheExpiryTime = lNewTimeMS;
	}

	/**
	 * Adds an item to the cache if it doesn't already exist
	 * @return true if the item was successfully added
	 */
	public boolean addItem(Object oItem)
	{
		expireIfDue();

		if(!(oItem instanceof CacheableItem)) return false;
		/*try{ ciItem = (CacheableItem)oItem; }
		catch(Exception e){ return false;}*/
		CacheableItem ciItem = (CacheableItem)oItem;
		if(m_htItems.containsKey(ciItem.getItemKey())) return false;

		if(!itemCanFit(ciItem.getSize())) return false;

		//System.out.println("*** DEBUG: ADD TO CACHE: " + ciItem.getItemKey());
		//m_dSize += ciItem.getSize();
		m_htItems.put(ciItem.getItemKey(), ciItem);

		return true;
	}

	/**
	 * Removes an item from the cache
	 * @return true if the item was successfully removed
	 */
	public boolean removeItem(String szKey)
	{
//		System.out.println("1. DEBUG: REMOVE FROM CACHE: " + szKey);
		if(!m_htItems.containsKey(szKey)) return false;
		//CacheableItem ciItem = (CacheableItem)m_htItems.get(szKey);
//		System.out.println("*** DEBUG: REMOVE FROM CACHE: " + szKey);
		m_htItems.remove(szKey);
		//if(ciItem!=null) m_dSize -= ciItem.getSize();
		return true;
	}

	/**
	 * Replaces an item in the cache
	 */
	public void replaceItem(Object oItem)
	{
		if(!(oItem instanceof CacheableItem)) return;
		CacheableItem ciItem = (CacheableItem)oItem;
		removeItem(ciItem.getItemKey());
		addItem(oItem);
	}

	/**
	 * Goes through all items in the cache and removes the ones that have expired.
	 * Do not set the object to null as other threads may still be using the object
	 * Allow the parameter to be passed so that the cache may be flushed
	 */
	public void expireAll(long lTimeInMilliseconds)
	{
		CacheableItem ciItem;
		Enumeration<CacheableItem> en = m_htItems.elements();
//		System.out.println("*** DEBUG: EXPIRING ITEMS OLDER THAN: " + lTimeInMilliseconds);
		while(en.hasMoreElements())
		{
			ciItem = (CacheableItem)en.nextElement();
			if(ciItem!=null && (lTimeInMilliseconds<1 || ciItem.itemHasExpired(lTimeInMilliseconds)))
			{
				removeItem(ciItem.getItemKey());
			}
		}
	}

	/**
	 * Run the periodic expiry sweep if one hasn't run in the last EXPIRE_SWEEP_INTERVAL_MS.
	 * Only one thread wins the CAS, so concurrent callers don't all scan the cache.
	 */
	private void expireIfDue()
	{
		long lNow = System.currentTimeMillis();
		long lLast = m_lLastExpireSweep.get();
		if(lNow-lLast < EXPIRE_SWEEP_INTERVAL_MS) return;
		if(m_lLastExpireSweep.compareAndSet(lLast, lNow)) expireAll(m_lCacheExpiryTime);
	}

	public void resetCounters()
	{
		m_lCacheMisses.set(0);
		m_lCacheHits.set(0);
	}

	private void logCacheMiss()
	{
		m_lCacheMisses.incrementAndGet();
	}

	private void logCacheHit()
	{
		m_lCacheHits.incrementAndGet();
	}


	/**
	 * Get an item from the cache. If it does not exist, return null.
	 *
	 */
	public Object getItem(String szKey)
	{
		if(szKey==null) return null;

		Object oItem=null;
		expireIfDue(); //make some room
		//System.out.println("["+szKey+"]");
		oItem = m_htItems.get(szKey);
		//the full sweep is throttled, so check this item's own expiry to never return a stale one
		if(oItem!=null && ((CacheableItem)oItem).itemHasExpired(m_lCacheExpiryTime))
		{
			removeItem(szKey);
			oItem = null;
		}
		if(oItem==null)
		{
			logCacheMiss();            
		}
		else
		{
			logCacheHit();
			CacheableItem ciItem = (CacheableItem)oItem;
			ciItem.logCacheAccess();
		}
		return oItem;
	}

	/**
	 * Show the contents of the cache
	 */
	public String toString()
	{
		ArrayList<String> arr = new ArrayList<String>();
		int iCount=0;
		StringBuilder sb = new StringBuilder(256);
		Enumeration<CacheableItem> en = m_htItems.elements();
		while(en.hasMoreElements())
		{
			CacheableItem ciItem = (CacheableItem)en.nextElement();
			String sLine = ciItem.toString();//ciItem.getItemKey() + " size=" + ciItem.getSize() + "\r\n";
			//sb.append(sLine);
			//sb.append("\r\n");
			arr.add(sLine);
			iCount++;
		}

		Collections.sort(arr);

		for(int i=0; i<arr.size(); i++)
		{
			sb.append((String)arr.get(i));
			sb.append("\r\n");
		}

		sb.append(iCount + " elements " +(long)getCacheSize()/1024 + "KB. hits=" + m_lCacheHits.get() + " misses=" + m_lCacheMisses.get() + "\r\n");
		return sb.toString();
	}

	/**
	 * Gets the expiry time of all elements in the cache
	 * in milliseconds
	 */
	public long getCacheExpiryTime()
	{
		return m_lCacheExpiryTime;
	}

	public double getCacheHits()
	{
		return m_lCacheHits.get();
	}

	/**
	 * Loop through the elements in the cache and see how big they are
	 */
	public double getCacheSize()
	{
		double dSize=0;
		Enumeration<CacheableItem> en = m_htItems.elements();
		while(en.hasMoreElements())
		{
			CacheableItem ciItem = (CacheableItem)en.nextElement();
			dSize += ciItem.getSize();          
		}

		return dSize;
	}

	public double getCacheMaxSize()
	{
		return m_dMaxSize;
	}

	public double getCacheMisses()
	{
		return m_lCacheMisses.get();
	}

	public double getCacheAccesses()
	{
		return m_lCacheMisses.get()+m_lCacheHits.get();
	}

	public int getItemCount()
	{
		return m_htItems.size();
	}

	public synchronized void setItemKey(String sNewKey)
	{
		m_sCacheKey = sNewKey;
	}

	public String getItemKey() 
	{
		if(m_sCacheKey==null) return this.getClass().getName(); //?? is this ok - not very unique :-S
		return m_sCacheKey;
	}

	public double getSize() 
	{
		return getCacheSize();
	}

	public boolean itemHasExpired(long lAgeInMilliseconds) 
	{
		return false;
	}

	public void logCacheAccess() 
	{
	}

}