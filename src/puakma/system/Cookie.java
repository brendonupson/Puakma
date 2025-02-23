/** ***************************************************************
Cookie.java
Copyright (C) 2001  Brendon Upson 
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
package puakma.system;

import java.util.*;
import java.text.*;
import java.net.URLEncoder;
import puakma.util.Util;

/**
 * Simple wrapper class for dealing with http cookies. A document will have a Hashtable
 * containing a bunch of these.
 */
public class Cookie implements Cloneable
{
	private String m_sName;
	private String m_sValue;
	private Date m_dtExpires;
	private String m_sPath;
	private String m_sDomain;
	private boolean m_bSecure=false;
	private boolean m_bHttpOnly=false;

	private boolean m_bSendToClient=true;  

	private SimpleDateFormat m_sdf = new SimpleDateFormat("E dd-MMM-yyyy HH:mm:ss 'GMT'");

	/**
	 * Used to create cookies when passed up from the client
	 * defaults to NOT send to client
	 * Name=Value
	 */
	public Cookie(String szNameValuePair)
	{
		szNameValuePair = Util.trimSpaces(szNameValuePair);
		szNameValuePair = Util.trimChar(szNameValuePair, ';');
		int iPos = szNameValuePair.indexOf('=');
		if(iPos>0)
		{
			setName(szNameValuePair.substring(0, iPos));
			setValue(szNameValuePair.substring(iPos+1, szNameValuePair.length()));
			m_bSendToClient = false;
		}
		else
		{
			setName(szNameValuePair);
			setValue("");
		}
	}

	public Cookie(String szName, String szValue)
	{
		setName(szName);
		setValue(szValue);
	}

	public Cookie(String szName, String szValue, String szPath)
	{
		setName(szName);
		setValue(szValue);
		setPath(szPath);
	}

	public Cookie(String szName, String szValue, Date dtExpires, String szPath)
	{
		setName(szName);
		setValue(szValue);
		setExpires(dtExpires);
		setPath(szPath);
	}

	public Cookie(String szName, String szValue, Date dtExpires, String szPath, String szDomain, boolean bSecure)
	{
		setName(szName);
		setValue(szValue);
		setExpires(dtExpires);
		setPath(szPath);
		setDomain(szDomain);
		setSecure(bSecure);
	}
	
	public Cookie(String szName, String szValue, Date dtExpires, String szPath, String szDomain, boolean bSecure, boolean bHttpOnly)
	{
		setName(szName);
		setValue(szValue);
		setExpires(dtExpires);
		setPath(szPath);
		setDomain(szDomain);
		setSecure(bSecure);
		setHttpOnly(bHttpOnly);
	}



	public synchronized void setName(String szNewName)
	{
		m_sName = szNewName;
	}

	public synchronized void setValue(String szNewValue)
	{
		m_sValue = szNewValue;
	}

	public synchronized void setExpires(Date dtNewExpires)
	{
		if(dtNewExpires==null)
			m_dtExpires = null;
		else
			m_dtExpires = (Date)dtNewExpires.clone();
	}

	public synchronized void setPath(String szNewPath)
	{
		if(szNewPath==null)
			m_sPath = null;
		else
			m_sPath = szNewPath;
	}

	public synchronized void setDomain(String szNewDomain)
	{
		if(szNewDomain==null)
			m_sDomain = null;
		else
			m_sDomain = szNewDomain;
	}


	public synchronized void setSecure(boolean bNewSecure)
	{
		m_bSecure = bNewSecure;
	}
	
	
	public synchronized void setHttpOnly(boolean bHttpOnly)
	{
		m_bHttpOnly = bHttpOnly;
	}

	public synchronized void setSendToClient(boolean bSend)
	{
		m_bSendToClient = bSend;
	}



	public String getName()
	{
		return m_sName;
	}

	public String getValue()
	{
		return m_sValue;
	}

	public String getPath()
	{
		if(m_sPath==null) return "";
		return m_sPath;
	}

	public String getDomain()
	{
		if(m_sDomain==null) return "";
		return m_sDomain;
	}

	public Date getExpires()
	{
		return m_dtExpires;
	}

	public boolean getSecure()
	{
		return m_bSecure;
	}

	/**
	 * Returns the full string used by the http server:
	 * Set-Cookie: Name=Value; expires=DATE; path=Path; domain=DOMAIN; secure
	 * url is encoded in case there are spaces or ; or special chars
	 * @return null if the cookie should NOT be sent to the client
	 */
	public String getCookieString()
	{
		if(!m_bSendToClient) return null;

		return toString();
	}


	/**
	 * Returns the full string used by the http server:
	 * Set-Cookie: Name=Value; expires=DATE; path=Path; domain=DOMAIN; secure
	 * url is encoded in case there are spaces or ; or special chars
	 * @return null if the cookie should NOT be sent to the client
	 */
	public String toString()
	{
		StringBuilder sbCookie = new StringBuilder(256);
		sbCookie.append("Set-Cookie: ");
		sbCookie.append(m_sName);
		sbCookie.append('=');
		try
		{
			sbCookie.append(URLEncoder.encode(m_sValue, "UTF-8"));
		}
		catch(Exception e){}
		if(m_dtExpires!=null)
		{
			sbCookie.append("; expires=");
			sbCookie.append(m_sdf.format(m_dtExpires));
		}
		if(m_sPath!=null && m_sPath.length()!=0)
		{
			sbCookie.append("; path=");
			sbCookie.append(m_sPath);        
		}
		if(m_sDomain!=null && m_sDomain.length()!=0)
		{
			sbCookie.append("; domain=");
			sbCookie.append(m_sDomain);
		}
		if(m_bSecure)
		{
			sbCookie.append("; secure");
		}

		if(m_bHttpOnly)
		{    	
			sbCookie.append("; httpOnly");
		}

		return sbCookie.toString();
	}

	public Object clone()
	{
		Cookie NewCookie;
		try
		{
			NewCookie = (Cookie)super.clone();
			return NewCookie;
		}
		catch(Exception e)
		{
			return null;
		}
	}
}