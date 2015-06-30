/** ***************************************************************
TableColumnItem.java
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
package puakma.port;

import java.io.Serializable;
import java.sql.Types;

/**
 * describes a column in a row in a relational table
 * Dates are stored as a string representation of the long time value
 */
public class TableColumnItem implements Serializable
{

	private static final long serialVersionUID = -5900101947586113115L;
	boolean isPrimaryKey=false;
	String ForeignKeyTo="";
	int Type;
	String Name;
	byte Value[]; //everything is stored in this type! numeric, char, blob, ...

	public static final int ITEM_TYPE_CHAR=1;
	public static final int ITEM_TYPE_INTEGER=2;
	public static final int ITEM_TYPE_NUMERIC=3;
	public static final int ITEM_TYPE_BLOB=4;
	public static final int ITEM_TYPE_TEXT=5;
	public static final int ITEM_TYPE_DATE=6;

	/**
	 * Create a column....
	 */
	public TableColumnItem(String paramName, int paramType, boolean paramPrimaryKey, String paramForeignKey, byte[] paramValue)
	{
		isPrimaryKey = paramPrimaryKey;
		ForeignKeyTo = paramForeignKey;
		if(ForeignKeyTo==null) ForeignKeyTo="";
		Name = paramName;
		Type = paramType;
		Value = paramValue;

	}

	/**
	 * Convert the current type to the SQL equivalent
	 */
	public int getSQLType()
	{
		switch(Type)
		{
		case ITEM_TYPE_INTEGER:
			return Types.INTEGER;
		case ITEM_TYPE_NUMERIC:
			return Types.DOUBLE;
		case ITEM_TYPE_DATE:
			return Types.TIMESTAMP;
		case ITEM_TYPE_CHAR:
			return Types.CHAR;
		case ITEM_TYPE_BLOB:
			return Types.VARBINARY;
		};

		return Types.VARCHAR;
	}

	/**
	 * Compares two columns for name, type and value
	 */
	public boolean equals(TableColumnItem tciToCompare)
	{
		try
		{
			if(!Name.equalsIgnoreCase(tciToCompare.Name)) return false;

			if(Type!=tciToCompare.Type) return false;
			if(!ForeignKeyTo.equalsIgnoreCase(tciToCompare.ForeignKeyTo)) return false;
			if(isPrimaryKey!=tciToCompare.isPrimaryKey) return false;
			//if both values are null then they match
			if(Value==null && tciToCompare.Value!=null
					|| Value!=null && tciToCompare.Value==null) return false;
			if(Value!=null && tciToCompare.Value!=null)
			{
				if(Value.length!=tciToCompare.Value.length) return false;
				for(int i=0; i<Value.length; i++)
				{
					if(Value[i]!=tciToCompare.Value[i]) return false;
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
	 * returns true if the column type is able to be used in a select, ie not blob
	 */
	public boolean isSelectable()
	{
		if(Type==ITEM_TYPE_BLOB) return false;
		return true;
	}

	public String toString()
	{
		String sVal = "***BLOB***";
		sVal = getStringValue();
		return "name=[" + Name + "] value=[" + sVal + "] type=[" + Type + "]";
	}


	/**
	 * Get the size of the name and value items
	 */
	public int getSize()
	{
		int iSize = Name.length();
		if(Value!=null) iSize += Value.length;

		return iSize;
	}

	/**
	 * Determines if the column contains a null value
	 */
	public boolean isNull()
	{
		return Value==null;
	}

	/**
	 * Manually set the value of this tci
	 *
	 */
	public synchronized void setValue(byte[] newBuf)
	{
		Value = newBuf;
	}

	public synchronized void setStringValue(String s)
	{
		if(s==null)
		{
			Value = null;
			return;
		}
		try { Value = s.getBytes("UTF-8"); }catch(Exception e){}
	}

	public String getStringValue()
	{
		if(isNull()) return "-NULL-";
		try 
		{
			if(Type==ITEM_TYPE_BLOB) return "**BLOB**";
			if(Type==ITEM_TYPE_DATE)
			{
				long lDate = Long.parseLong(new String(Value, "UTF-8"));
				java.util.Date dtDate = new java.util.Date(lDate);
				return puakma.util.Util.formatDate(dtDate, "dd.MMM.yy HH:mm:ss:SSS");
			}

			return new String(Value, "UTF-8"); 
		}catch(Exception e){}

		return "???";
	}

	/**
	 *
	 */
	public String getName()
	{
		return Name;
	}
}