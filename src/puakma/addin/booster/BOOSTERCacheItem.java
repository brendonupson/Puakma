/*
 * BOOSTERCacheItem.java
 *
 * Created on February 15, 2005, 6:25 PM
 */

package puakma.addin.booster;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import puakma.pooler.CacheableItem;
import puakma.util.Util;
/**
 *
 * @author  bupson
 */
public class BOOSTERCacheItem implements CacheableItem
{
	//private int MAX_ELEMENT_SIZE = 512000; //only cache 512Kb and smaller
	//private long m_lMaxKeepMS=24*60*60*1000; //1 day
	private String m_sURI;
	private byte[] m_buf;
	private long m_lCacheHits=0;
	//private Date m_dtExpires = new Date();
	private boolean m_bGZippedContent = false;
	private ArrayList<String> m_arrHeaders = new ArrayList<String>();
	private boolean m_bCacheable = true;
	private String m_sLastModified = "";
	private String m_sContentType = "text/plain";
	private long m_lExpiryDate = 0;
	private long m_lKeepInCacheUntilDate = 0;
	public static final String LAST_MOD_DATE = "EEE, dd MMM yyyy HH:mm:ss z";
	private TimeZone m_tzGMT = TimeZone.getTimeZone("GMT");
	private String m_sServer;
	private String m_sHost;
	private boolean m_bSharedCache=false;

	/** Creates a new instance of BOOSTERCacheItem */
	public BOOSTERCacheItem(String sHost, String sURI, byte buf[], ArrayList<String> arrHeaders, boolean bCacheIsCompressed, int iMaxElementSize, long lMaxCacheTimeMS, long lMinCacheTimeMS, boolean bSharedCache) 
	{
		m_bSharedCache = bSharedCache;
		m_sHost = sHost;
		if(m_sHost==null) m_sHost="";
		//iMaxElementSize of -1 means no limit
		if(buf==null || buf.length<1 || (buf.length>iMaxElementSize && iMaxElementSize>0))
		{
			//System.out.println("! "+buf.length + " > " + iMaxElementSize);
			m_bCacheable = false;
			return;
		}


		m_sContentType = Util.getMIMELine(arrHeaders, "Content-Type");
		m_buf = buf;
		m_sURI = sURI;

		String sCookies = Util.getMIMELine(arrHeaders, "Set-Cookie");
		if(sCookies!=null)
		{
			m_bCacheable = false;
			return;
		}
		String sCache = Util.getMIMELine(arrHeaders, "Cache-Control");
		if(sCache!=null && (sCache.equalsIgnoreCase("private") || sCache.equalsIgnoreCase("no-cache")))
		{
			m_bCacheable = false;
			return;
		}        
		sCache = Util.getMIMELine(arrHeaders, "Pragma");
		if(sCache!=null && (sCache.equalsIgnoreCase("private") || sCache.equalsIgnoreCase("no-cache")))
		{
			m_bCacheable = false;
			return;
		}

		m_sServer = Util.getMIMELine(arrHeaders, "Server");
		m_sLastModified = Util.getMIMELine(arrHeaders, "Last-Modified");        
		long lNow = System.currentTimeMillis();
		String sExpires = Util.getMIMELine(arrHeaders, "Expires");
		if(sExpires!=null)
		{
			//System.out.println("cacheitem - before: Expires is: " + sExpires);
			//if we have an expiry date, then use this as the time to live
			m_lExpiryDate = Util.getDateMSFromGMTString(sExpires);
			//if expires>now
			if(m_lExpiryDate<lNow) 
			{
				m_lExpiryDate = lNow;                
			}
		}
		else
		{			
			m_lExpiryDate = lNow;
			if(lMinCacheTimeMS>0) m_lExpiryDate = lNow+lMinCacheTimeMS;
		}

		long lMinKeepTime = lNow+lMinCacheTimeMS;
		//removed 7/1/08 BJU. Items with a far future expiry date set by the host server
		//were not being correctly reported to the client.		
		//if(m_lExpiryDate>lMaxKeepTime) m_lExpiryDate = lMaxKeepTime;
		//lMinCacheTimeMS of -1 means not set
		m_lKeepInCacheUntilDate = lNow+lMaxCacheTimeMS;
		if(m_lKeepInCacheUntilDate>m_lExpiryDate) m_lKeepInCacheUntilDate = m_lExpiryDate;

		if(lMinCacheTimeMS>0 && m_lExpiryDate<lMinKeepTime) m_lExpiryDate = lMinKeepTime;


		java.util.Date dtExpires = new java.util.Date();
		dtExpires.setTime(m_lExpiryDate);
		sExpires = Util.formatDate(dtExpires, LAST_MOD_DATE, Locale.UK, m_tzGMT);
		//System.out.println("cacheitem - Expires set to: " + sExpires);
		puakma.util.Util.replaceHeaderValue(arrHeaders, "Expires", sExpires);

		m_bGZippedContent = bCacheIsCompressed;
		copyHeader(arrHeaders, "Cache-Control");
		copyHeader(arrHeaders, "Pragma");
		copyHeader(arrHeaders, "Expires");
		copyHeader(arrHeaders, "ETag");
	}

	/**
	 * Copy a header value into the internal header list
	 */
	private void copyHeader(ArrayList<String> arrHeaders, String sField)
	{
		String sData = Util.getMIMELine(arrHeaders, sField);
		if(sData!=null) m_arrHeaders.add(sField + ": " + sData);
	}

	public ArrayList<String> getResponseHeaders()
	{
		return m_arrHeaders;
	}

	/**
	 *
	 */
	public String getContentType()
	{
		return m_sContentType;
	}



	/**
	 * determines if the item in the cache has changed since it was added.
	 */
	public boolean hasChanged(ArrayList<String> arrHeaders)
	{
		String sMod = Util.getMIMELine(arrHeaders, "If-Modified-Since");
		if(sMod==null) return true;
		if(m_sLastModified==null || m_sLastModified.length()==0) return true;

		if(m_sLastModified.equalsIgnoreCase(sMod)) return false;

		return true;
	}

	/**
	 * Returns true if this item should be addded to the cache
	 */
	public boolean isCacheable()
	{
		return m_bCacheable;
	}

	/**
	 * get the unique key for this item.
	 */
	public String getItemKey() 
	{
		if(m_bSharedCache) return m_sURI;

		return m_sHost + m_sURI;
	}

	/**
	 * show the size of this object, roughly
	 */
	public double getSize() 
	{
		if(m_buf!=null) return m_buf.length;
		return 0;
	}

	public boolean itemHasExpired(long lAgeInMilliseconds) 
	{
		//if(m_lExpiryDate<lAgeInMilliseconds) return true;
		if(m_lKeepInCacheUntilDate<lAgeInMilliseconds) return true;		
		return false;
	}

	/**
	 * Record each cache hit
	 */
	public synchronized void logCacheAccess() 
	{
		m_lCacheHits++;
	}

	/**
	 * Format the cache item as a HTTP response
	 */
	public byte[] getHTTPReply(boolean bShouldCompress, String sVia, boolean bKeepAlive)
	{
		StringBuilder sbHeaders = new StringBuilder(128);
		boolean bCompressed=false;

		byte bufContent[] = null;

		if(bShouldCompress && m_bGZippedContent) 
		{
			bCompressed = true;
			bufContent = m_buf;
		}
		if(bShouldCompress && !m_bGZippedContent) 
		{
			bufContent = Util.gzipBuffer(m_buf);
			bCompressed = true;
			//very small files compress to be larger then the original
			//eg "<html></html>\r\n"
			if(bufContent.length>m_buf.length)
			{
				bufContent = m_buf;
				bCompressed = false;
			}

		}
		//not compressed
		if(!bShouldCompress && m_bGZippedContent) 
		{
			bCompressed = false;
			bufContent = Util.ungzipBuffer(m_buf);
		}
		if(!bShouldCompress && !m_bGZippedContent) 
		{
			bufContent = m_buf;
			bCompressed = false;
		}


		sbHeaders.append("HTTP/1.1 200 OK\r\n");
		sbHeaders.append("Date: " + Util.formatDate(new java.util.Date(), LAST_MOD_DATE, Locale.UK, m_tzGMT));
		sbHeaders.append("\r\n");        
		for(int i=0; i<m_arrHeaders.size(); i++)
		{
			sbHeaders.append(m_arrHeaders.get(i));
			sbHeaders.append("\r\n");
		}
		//sbHeaders.append("X-stats: shouldcompress="+bShouldCompress+"; isgzipped="+m_bGZippedContent + " compressed=" + bCompressed + "\r\n");
		sbHeaders.append(sVia);
		sbHeaders.append("\r\n");
		if(bCompressed)
		{
			sbHeaders.append("Content-Encoding: gzip\r\n");
		}
		if(m_sServer!=null)
		{
			sbHeaders.append("Server: " + m_sServer);
			sbHeaders.append("\r\n");
		}
		if(bKeepAlive)
			sbHeaders.append("Connection: keep-alive\r\n");
		else
			sbHeaders.append("Connection: close\r\n");

		sbHeaders.append("X-Cache-Hit: BOOSTER-Cache\r\n");
		int iContentLength=0;
		if(bufContent!=null) iContentLength= bufContent.length;
		sbHeaders.append("Content-Length: " + iContentLength);
		sbHeaders.append("\r\n");
		sbHeaders.append("Content-Type: " + m_sContentType);
		sbHeaders.append("\r\n");
		if(m_sLastModified!=null)
		{
			sbHeaders.append("Last-Modified: " + m_sLastModified);
			sbHeaders.append("\r\n");
		}
		sbHeaders.append("\r\n");
		byte bufHeader[] = sbHeaders.toString().getBytes();

		int iLen = bufHeader.length;
		if(bufContent!=null) iLen += bufContent.length;

		byte bufReply[] = new byte[iLen];


		System.arraycopy(bufHeader, 0, bufReply, 0, bufHeader.length);
		if(bufContent!=null) System.arraycopy(bufContent, 0, bufReply, bufHeader.length, bufContent.length);

		/*if(bCompressed)        
            System.out.println("Sending compressed reply");
        else
            System.out.println("Sending normal reply");
		 */
		//System.out.println(sbHeaders.toString());

		return bufReply;
	}

	public String toString()
	{
		java.util.Date dtKeepUntil = new java.util.Date();
		dtKeepUntil.setTime(m_lKeepInCacheUntilDate);
		return getItemKey() + " " + m_sContentType + " "+ (long)getSize() + " CacheUntil:" + puakma.util.Util.formatDate(dtKeepUntil, "yyyy/MM/dd HH:mm:ss");
	}

}//end class
