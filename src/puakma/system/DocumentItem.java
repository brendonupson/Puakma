/** ***************************************************************
DocumentItem.java
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

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import puakma.addin.http.document.TableManager;
import puakma.util.Util;


/**
 * <p>Title: DocumentItem</p>
 * <p>Description: Defines an item on a document</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

public class DocumentItem implements Cloneable
{
	public static final int ITEM_TYPE_STRING = 1;
	public static final int ITEM_TYPE_MULTI = 2;
	public static final int ITEM_TYPE_FILE = 3;
	public static final int ITEM_TYPE_BUFFER = 4;
	public static final int ITEM_TYPE_RICH = 5; //for textarea, multi-lined stuff
	public static final int ITEM_TYPE_DATE = 6;
	public static final int ITEM_TYPE_NUMERIC = 7;
	public static final int ITEM_TYPE_INTEGER = 8;
	public static final int ITEM_TYPE_OBJECT = 9;
	public static final int ITEM_TYPE_JSON = 10;

	protected Document m_docParent;
	protected String m_sName;
	protected String m_sDataFormat; //eg "dd/MM/yy for dates"
	protected byte[] m_Value;
	protected int m_iType=ITEM_TYPE_STRING;
	protected String[] m_sChoices=null;

	protected NumberFormat m_nf = NumberFormat.getInstance();

	public DocumentItem(Document docParent, String szName, String szValue)
	{
		initItem(docParent, szName);
		setStringValue(szValue);
	}

	public DocumentItem(Document docParent, String szName, boolean bValue)
	{
		initItem(docParent, szName);
		setStringValue(String.valueOf(bValue));
	}

	public DocumentItem(Document docParent, String szName, java.util.Date dtValue)
	{
		initItem(docParent, szName);
		setDateValue(dtValue);
	}

	public DocumentItem(Document docParent, String szName, long lValue)
	{
		initItem(docParent, szName);
		setIntegerValue(lValue);
	}

	public DocumentItem(Document docParent, String szName, double dblValue)
	{
		initItem(docParent, szName);
		setNumericValue(dblValue);
	}

	public DocumentItem(Document docParent, String szName, byte[] btValue)
	{
		initItem(docParent, szName);
		setBufferValue(btValue);
	}

	/**
	 * called by each constructor to initialize the item
	 * @param docParent
	 * @param szName
	 */
	public void initItem(Document docParent, String szName)
	{
		m_nf.setMaximumFractionDigits(Integer.MAX_VALUE);
		m_docParent = docParent;
		m_sName = szName;

		m_docParent.replaceItem(this);
	}

	/**
	 * Compares two items for name, type and value
	 */
	public boolean equals(DocumentItem diToCompare)
	{
		try
		{
			if(!this.m_sName.equalsIgnoreCase(diToCompare.getName())) return false;

			if(m_iType!=diToCompare.getType()) return false;
			byte bufThatValue[] = diToCompare.getValue();

			//if both values are null then they match
			if(m_Value==null && bufThatValue!=null
					|| m_Value!=null && bufThatValue==null) return false;
			if(m_Value!=null && bufThatValue!=null)
			{
				if(m_Value.length!=bufThatValue.length) return false;
				for(int i=0; i<m_Value.length; i++)
				{
					if(m_Value[i]!=bufThatValue[i]) return false;
				}
			}
		}
		catch(Exception e)
		{
			return false;
		}
		return true;
	}


	/**
	 * Get the type of this document item
	 * @return the current type of the item
	 */
	public int getType()
	{
		return m_iType;
	}

	/**
	 * Set the item type property
	 * @param iNewType
	 */
	public synchronized void setType(int iNewType)
	{
		m_iType = iNewType;
	}

	public void setParentDocument(Document docNewParent)
	{
		m_docParent.removeItemReference(m_sName);
		m_docParent = docNewParent;
		m_docParent.addItemReference(this);
	}


	/**
	 * Get the name of this document item
	 * @return the current name of the item
	 */
	public String getName()
	{
		return m_sName;
	}

	/**
	 * Get the choices associated with this item
	 */
	public String[] getChoices()
	{
		return m_sChoices;
	}

	/**
	 * Set the item name property
	 * @param szNewName
	 */
	public synchronized void setName(String szNewName)
	{
		m_sName = szNewName;
	}

	/**
	 * Used to set the data format for objects such as dates eg "dd/MM/yy"
	 */
	public synchronized void setDataFormat(String sNewDataFormat)
	{
		m_sDataFormat = sNewDataFormat;
	}

	/**
	 * Mostly for debug...
	 * @return info about the item
	 */
	public String toString()
	{
		return m_sName + ": " + getRawStringValue() + " (" + m_iType + ")";
	}


	/**
	 * 
	 * @return
	 */
	public StringBuilder toXMLFragment()
	{
		boolean bIsNull = m_Value==null;

		StringBuilder sbReturn = new StringBuilder(256);
		sbReturn.append("\t<item name=\"");
		sbReturn.append(m_sName.toLowerCase());
		sbReturn.append("\">\r\n");
		
		switch(m_iType)
		{
		case DocumentItem.ITEM_TYPE_INTEGER:				
			if(bIsNull) sbReturn.append("\t\t<value datatype=\"integer\" isnull=\"true\"></value>\r\n");
			else
			{
				sbReturn.append("\t\t<value datatype=\"integer\" isnull=\"false\">");
				sbReturn.append(getIntegerValue());
				sbReturn.append("</value>\r\n");
			}
			break;
		case DocumentItem.ITEM_TYPE_NUMERIC:			
			if(bIsNull) sbReturn.append("\t\t<value datatype=\"float\" isnull=\"true\"></value>\r\n");
			else 
			{
				NumberFormat nf = NumberFormat.getInstance();
				nf.setGroupingUsed(false);
				
				sbReturn.append("\t\t<value datatype=\"float\" isnull=\"false\">");
				sbReturn.append(nf.format(getNumericValue()));
				sbReturn.append("</value>\r\n");
			}
			break;
		case DocumentItem.ITEM_TYPE_STRING:			
			if(bIsNull) sbReturn.append("\t\t<value datatype=\"string\" isnull=\"true\"></value>\r\n");
			else 
			{
				sbReturn.append("\t\t<value datatype=\"string\" isnull=\"false\"><![CDATA[");
				sbReturn.append(TableManager.cleanXMLString(getStringValue()));
				sbReturn.append("]]></value>\r\n");
			}
			break;
		case DocumentItem.ITEM_TYPE_DATE:			       
			if(bIsNull) sbReturn.append("\t\t<value datatype=\"time\" isnull=\"true\"></value>\r\n");
			else
			{				
				TimeZone tz = null;
				Locale loc = null;
				SessionContext sess = m_docParent.pSession;
				if(sess!=null)
				{
					tz = sess.getTimeZone();
					loc = sess.getLocale();
				}
				java.util.Date dtRecord = getDateValue();
				sbReturn.append("\t\t<value datatype=\"time\" isnull=\"false\">");
				sbReturn.append("<year>");
				sbReturn.append(Util.formatDate(dtRecord, "yyyy", loc, tz));
				sbReturn.append("</year>");
				sbReturn.append("<month>");
				sbReturn.append(Util.formatDate(dtRecord, "MM", loc, tz));
				sbReturn.append("</month>");					
				sbReturn.append("<day>");
				sbReturn.append(Util.formatDate(dtRecord, "dd", loc, tz));
				sbReturn.append("</day>");
				sbReturn.append("<hour>");
				sbReturn.append(Util.formatDate(dtRecord, "HH", loc, tz));
				sbReturn.append("</hour>");					
				sbReturn.append("<minute>");
				sbReturn.append(Util.formatDate(dtRecord, "mm", loc, tz));
				sbReturn.append("</minute>");
				sbReturn.append("<second>");
				sbReturn.append(Util.formatDate(dtRecord, "ss", loc, tz));
				sbReturn.append("</second>");
				//?? is this required? will likely be the same as the server zone anyway cause
				//we construct the Date only from
				sbReturn.append("<zone>");
				sbReturn.append(Util.formatDate(dtRecord, "z", loc, tz));
				sbReturn.append("</zone>");

				sbReturn.append("<monthnameshort>");
				sbReturn.append(Util.formatDate(dtRecord, "MMM", loc, tz));
				sbReturn.append("</monthnameshort>");
				sbReturn.append("<monthnamelong>");
				sbReturn.append(Util.formatDate(dtRecord, "MMMM", loc, tz));
				sbReturn.append("</monthnamelong>");
				sbReturn.append("<daynameshort>");
				sbReturn.append(Util.formatDate(dtRecord, "EEE", loc, tz));
				sbReturn.append("</daynameshort>");
				sbReturn.append("<daynamelong>");
				sbReturn.append(Util.formatDate(dtRecord, "EEEE", loc, tz));
				sbReturn.append("</daynamelong>");

				sbReturn.append('<'+Util.SHORT_DATE+'>');
				sbReturn.append(Util.formatDate(dtRecord, Util.SHORT_DATE, loc, tz));
				sbReturn.append("</"+Util.SHORT_DATE+'>');
				sbReturn.append('<'+Util.LONG_DATE+'>');
				sbReturn.append(Util.formatDate(dtRecord, Util.LONG_DATE, loc, tz));
				sbReturn.append("</"+Util.LONG_DATE+'>');
				sbReturn.append('<'+Util.SHORT_DATE_TIME+'>');
				sbReturn.append(Util.formatDate(dtRecord, Util.SHORT_DATE_TIME, loc, tz));
				sbReturn.append("</"+Util.SHORT_DATE_TIME+'>');
				sbReturn.append('<'+Util.LONG_DATE_TIME+'>');
				sbReturn.append(Util.formatDate(dtRecord, Util.LONG_DATE_TIME, loc, tz));
				sbReturn.append("</"+Util.LONG_DATE_TIME+'>');
				sbReturn.append("</value>\r\n");
			}
			break;		
		}
		
		sbReturn.append("\t</item>\r\n");
		return sbReturn;
	}


	/**
	 * gets the exact string contents of the current item
	 * @return
	 */
	public String getRawStringValue()
	{
		if(m_Value==null) return "";
		try{ if(shouldDoStringConvert()) return new String(m_Value, "UTF-16"); }catch(Exception e){}

		return new String(m_Value);
	}

	/**
	 * Determine if we should do the UTF-16 string conversion
	 */
	private boolean shouldDoStringConvert()
	{
		if(m_iType==ITEM_TYPE_STRING) return true;
		if(m_iType==ITEM_TYPE_JSON) return true;
		if(m_iType==ITEM_TYPE_FILE) return true;

		return false;
	}

	/**
	 * Determines if this item contains a null value
	 */
	public boolean isNull()
	{
		if(m_Value==null) return true;
		return false;
	}

	/**
	 * gets the formatted string contents of the current item
	 * @return
	 */
	public String getStringValue()
	{
		if(m_Value==null) return "";
		switch(m_iType)
		{
		case ITEM_TYPE_DATE:
			return getStringValue(m_sDataFormat);
		case ITEM_TYPE_NUMERIC:           
			return m_nf.format(getNumericValue());     
		};

		try{ if(shouldDoStringConvert()) return new String(m_Value, "UTF-16"); }catch(Exception e){}

		return new String(m_Value);
	}


	/**
	 * gets the string contents of the current item
	 * after applying a format specifier
	 * @return
	 */
	public String getStringValue(String sFormatSpecifier)
	{
		SessionContext sess = m_docParent.getSessionContext();
		switch(m_iType)
		{
		case ITEM_TYPE_DATE:
			//SimpleDateFormat sdf;
			Locale locale = Locale.getDefault();
			if(m_docParent.getSessionContext()!=null && m_docParent.getSessionContext().getLocale()!=null) locale = m_docParent.getSessionContext().getLocale();
			/*if(sFormatSpecifier==null || sFormatSpecifier.length()==0)
				sdf = new SimpleDateFormat();          
			else
			{
				if(sess!=null)
				{
					//if there is a session object of the same name as the format, use it
					String sObj = (String)sess.getSessionObject(sFormatSpecifier);
					if(sObj!=null && sObj.length()>0) sFormatSpecifier = sObj;
				}
				sdf = new SimpleDateFormat(sFormatSpecifier, locale);
			}
			if(sess!=null && sess.getTimeZone()!=null) sdf.setTimeZone(sess.getTimeZone());

			return sdf.format(getDateValue());*/
			if(sFormatSpecifier==null || sFormatSpecifier.length()==0) sFormatSpecifier = Util.SHORT_DATE;
			return Util.formatDate(getDateValue(), sFormatSpecifier, locale, sess.getTimeZone());
		};

		try{ if(shouldDoStringConvert()) return new String(m_Value, "UTF-16"); }catch(Exception e){}

		return new String(m_Value);
	}

	/**
	 *
	 * @return a double describing the current value
	 */
	public double getNumericValue()
	{
		double dReturn;

		// check if we can convert these types to numeric
		if(m_iType==ITEM_TYPE_BUFFER || m_iType==ITEM_TYPE_RICH || m_iType==ITEM_TYPE_JSON)
		{
			return 0;
		}

		String sValue = getRawStringValue();

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
	 *
	 * @return a double describing the current value
	 */
	public long getIntegerValue()
	{
		// check if we can convert these types to numeric
		if(m_iType==ITEM_TYPE_BUFFER || m_iType==ITEM_TYPE_MULTI || m_iType==ITEM_TYPE_RICH || m_iType==ITEM_TYPE_JSON)
		{
			return 0;
		}

		return (long)getNumericValue();
	}

	/**
	 *
	 * @return a date or null if the item is not a Date type or convertible
	 */
	public java.util.Date getDateValue()
	{
		java.util.Date dtValue;
		SimpleDateFormat sdf = new SimpleDateFormat();

		// check if we can convert this type to numeric
		if(m_iType==ITEM_TYPE_BUFFER || m_iType==ITEM_TYPE_MULTI || m_iType==ITEM_TYPE_RICH || m_iType==ITEM_TYPE_JSON)
		{
			return null;
		}

		String sValue = getRawStringValue();
		if(m_iType==ITEM_TYPE_DATE)
		{
			try
			{
				long lDate = Long.parseLong(sValue);
				dtValue = new java.util.Date(lDate);
			}
			catch(Exception le)
			{
				return null;
			}

			return dtValue;
		}

		//OK, it's not a date so just try to convert it as best we can
		try
		{
			dtValue = sdf.parse(sValue);
		}
		catch(ParseException pe)
		{
			dtValue = null;
		}
		return dtValue;
	}

	/**
	 *
	 * @return a date or null if the item is not a Date type or convertible
	 */
	public java.sql.Date getSQLDateValue()
	{
		java.util.Date dtReturn = getDateValue();
		if(dtReturn==null) return null;

		return new java.sql.Date(dtReturn.getTime());
	}

	/**
	 * Sets the current item to a string value
	 * @param szNewValue
	 */
	public synchronized void setStringValue(String szNewValue)
	{
		setType(ITEM_TYPE_STRING);
		if(szNewValue==null)
		{
			setValue(null);
			return;
		}

		try
		{
			setValue(szNewValue.getBytes("UTF-16"));
		}
		catch(Exception e)
		{
			//System.out.println(e.toString());
			setValue(szNewValue.getBytes());
		}

	}

	/**
	 * Sets the current item to a date value
	 * The value is stored as a long converted to a string, then a byte array :-)
	 * @param dtNewValue
	 */
	public synchronized void setDateValue(java.util.Date dtNewValue)
	{
		setType(ITEM_TYPE_DATE);
		if(dtNewValue==null)
		{
			setValue(null);
			return;
		}

		String szValue = Long.toString(dtNewValue.getTime());
		setValue(szValue.getBytes());
	}


	/**
	 * Sets the current item to a numeric (double) value
	 * The value is stored as a double converted to a string, then a byte array :-)
	 * @param dblNewValue
	 */
	public synchronized void setNumericValue(double dblNewValue)
	{
		setType(ITEM_TYPE_NUMERIC);
		String szValue = String.valueOf(dblNewValue);    
		//Double.toString(dblNewValue);
		setValue(szValue.getBytes());
	}


	/**
	 * Sets the current item to a numeric (double) value
	 * The value is stored as a long converted to a string, then a byte array :-)
	 * @param lNewValue
	 */
	public synchronized void setIntegerValue(long lNewValue)
	{
		setType(ITEM_TYPE_INTEGER);

		String szValue = Long.toString(lNewValue);
		setValue(szValue.getBytes());
	}


	/**
	 * sets the byte array value directly
	 * @param Value
	 */
	public synchronized void setBufferValue(byte[] Value)
	{
		setType(ITEM_TYPE_BUFFER);
		setValue(Value);
	}

	/**
	 * sets the byte array value directly
	 * @param Value
	 */
	public synchronized void setValue(byte[] Value)
	{
		m_Value = Value;
	}

	/**
	 * sets the choice array directly
	 * @param sNewChoices
	 */
	public synchronized void setChoices(String[] sNewChoices)
	{
		m_sChoices = sNewChoices;
	}

	/**
	 * Brute force approach to getting data
	 * @return the raw bytes stored in this item
	 */
	public byte[] getValue()
	{
		return m_Value;
	}

	/**
	 * For 'special' items ie File, this will return a handle to the object
	 * @return Object
	 */
	public Object getObject()
	{
		return null;
	}

	/**
	 * clones the object
	 * @return
	 */
	public Object clone()
	{
		DocumentItem diNew;
		try
		{
			diNew = (DocumentItem)super.clone();
			return diNew;
		}
		catch(Exception e)
		{
			return null;
		}
	}
}