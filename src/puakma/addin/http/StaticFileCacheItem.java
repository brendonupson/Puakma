/** ***************************************************************
StaticFileCacheItem.java
Copyright (C) 2026  Brendon Upson
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
package puakma.addin.http;

import puakma.pooler.CacheableItem;

/**
 * A small static file after processing (minified and/or gzipped) with its ETag, so repeat
 * requests skip re-reading, re-compressing and re-hashing the file. The cache key includes the
 * file's last modified time and length, so an edited file is simply a new key.
 * The body is shared between threads and must never be modified.
 */
public class StaticFileCacheItem implements CacheableItem
{
	private final String m_sKey;
	private final byte[] m_bufBody;
	private final boolean m_bGZipped;
	private final String m_sETag;
	private volatile long m_lLastAccess = System.currentTimeMillis();

	public StaticFileCacheItem(String sKey, byte[] bufBody, boolean bGZipped, String sETag)
	{
		m_sKey = sKey;
		m_bufBody = bufBody;
		m_bGZipped = bGZipped;
		m_sETag = sETag;
	}

	public byte[] getBody()
	{
		return m_bufBody;
	}

	public boolean isGZipped()
	{
		return m_bGZipped;
	}

	/**
	 * @return the ETag value (without quotes), null if ETags are not being generated
	 */
	public String getETag()
	{
		return m_sETag;
	}

	public double getSize()
	{
		return m_bufBody==null ? 0 : m_bufBody.length;
	}

	public String getItemKey()
	{
		return m_sKey;
	}

	public void logCacheAccess()
	{
		m_lLastAccess = System.currentTimeMillis();
	}

	public boolean itemHasExpired(long lAgeInMilliseconds)
	{
		return System.currentTimeMillis() - m_lLastAccess > lAgeInMilliseconds;
	}
}
