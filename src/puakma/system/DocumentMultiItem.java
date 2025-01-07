/** ***************************************************************
DocumentMultiItem.java
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Vector;

import puakma.util.Util;

/**
 * This is a multivalue item, used for things like the SendTo field in an email
 * With this you can also map a 1 to many relationship in a single document, well, kludge it anyway!
 */
public class DocumentMultiItem extends DocumentItem
{
	Vector<String> m_vList = new Vector<String>();

	/*
	 * Used to create multi items
	 */
	public DocumentMultiItem(Document paramParent, String paramItemName, String paramItemValue)
	{
		super(paramParent, paramItemName, paramItemValue);
		setMultiStringValue(paramItemValue);
		setType(ITEM_TYPE_MULTI);
	}

	public DocumentMultiItem(Document paramParent, String paramItemName, String[] paramItemValues)
	{
		super(paramParent, paramItemName, "");
		if(paramItemValues!=null) 
		{
			for(int i=0; i<paramItemValues.length; i++)
				m_vList.add(paramItemValues[i]);
		}
		setType(ITEM_TYPE_MULTI);
	}

	public void setValues(String[] paramItemValues)
	{
		m_vList.clear();
		setStringValue(null);
		if(paramItemValues!=null) 
		{
			for(int i=0; i<paramItemValues.length; i++)
				m_vList.add(paramItemValues[i]);
		}
	}
	
	/**
	 * get the first value in the list and convert it to a number
	 */
	public double getNumericValue()
	{
		if(m_vList.size()==0) return 0;
		
		double dReturn = 0;
		String sValue = (String)m_vList.get(0);
		
		try
		{     
			//first try to parse with the servers number settings
			Number n = m_nf.parse(sValue);        
			dReturn = n.doubleValue();
		}
		catch(ParseException pe)
		{
			//ok, that failed so now try just a straight convert
			try
			{
				dReturn = Double.parseDouble(sValue);
			}
			catch(NumberFormatException e)
			{
				//that bombed too, so return zero
				dReturn = 0;
			}
		}


		return dReturn;
	}

	/**
	 * Adds a value to the list
	 */
	public void appendValue(String szValue)
	{
		m_vList.add(szValue);
	}

	/**
	 * gets all items as a single string
	 */
	public String getStringValue()
	{
		return getStringValue(",");
	}
	/**
	 * gets all items as a single string
	 */
	public String getStringValue(String Seperator)
	{
		/*String szResult="";
		if(Seperator==null) Seperator="";
		for(int i=0; i<m_vList.size(); i++)
		{
			if(szResult.length()==0)
				szResult = (String)m_vList.elementAt(i);
			else
				szResult += Seperator + m_vList.elementAt(i);
		}

		return szResult;*/
		StringBuilder sb = new StringBuilder(64);
		if(Seperator==null) Seperator="";
		for(int i=0; i<m_vList.size(); i++)
		{
			if(i>0) sb.append(Seperator);
			sb.append((String)m_vList.elementAt(i));			
		}

		return sb.toString();
	}

	/**
	 * Adds a comma separated string as a multivalue
	 * @param sNewValue
	 */
	public void setMultiStringValue(String sNewValues)
	{
		m_vList.removeAllElements();
		if(sNewValues==null) return;

		ArrayList<String> v = Util.splitString(sNewValues, ",");
		if(v!=null) m_vList.addAll(v);
	}


	/**
	 * Use this method to get the buffer
	 */
	public Vector<String> getValues()
	{
		return m_vList;
	}

	/**
	 * This will return a handle to the Vector object
	 * @return Object
	 */
	public Object getObject()
	{
		return (Object)m_vList;
	}

}