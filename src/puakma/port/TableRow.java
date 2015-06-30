/** ***************************************************************
TableRow.java
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Enumeration;
import java.util.Hashtable;

import puakma.system.Document;
import puakma.system.DocumentItem;
import puakma.util.Util;

/**
 * Defines a row in a relational table
 */
public class TableRow implements Serializable
{
	private static final long serialVersionUID = 11406943342625605L;
	private Hashtable m_htColumns=new Hashtable();
	private String m_sTableName;
	private String m_sJDBCDriverName;

	public TableRow(String sTableName)
	{
		m_sTableName = sTableName;
	}


	/**
	 * To see if a column exists
	 */
	public boolean hasColumn(String sColumnName)
	{
		if(sColumnName==null) return false;
		if(m_htColumns.containsKey(sColumnName.toUpperCase())) return true;

		return false;
	}


	/**
	 * To get a column
	 */
	public TableColumnItem getColumn(String sColumnName)
	{
		if(sColumnName==null) return null;
		return (TableColumnItem)m_htColumns.get(sColumnName.toUpperCase());
	}


	/**
	 * To get a column long value
	 */
	public long getColumnValueLong(String sColumnName)
	{
		//if(sColumnName==null) return -1;
		String sValue = null;
		TableColumnItem tci = getColumn(sColumnName); //(TableColumnItem)m_htColumns.get(sColumnName.toUpperCase());
		if(tci!=null && tci.Value!=null)
		{
			sValue = new String(tci.Value);
			if(Util.isNumeric(sValue)) 
			{
				long lReturn = Util.toInteger(sValue);				
				return lReturn;
			}
		}
		return -1;
	}

	/**
	 * Get a count of the columns in this row
	 */
	public int getColumnCount()
	{
		return this.m_htColumns.size();
	}

	/**
	 * Compare two table rows to see if they are the same. Used by design refresh to see if
	 * 
	 */
	public boolean equals(TableRow rToCompare)
	{
		if(rToCompare==null) return false;
		if(getColumnCount() != rToCompare.getColumnCount()) return false; //columns don't match
		//maybe exclude this check...
		//if(!m_sTableName.equalsIgnoreCase(rToCompare.getTableName())) return false; //table name doesn't match

		Enumeration en = m_htColumns.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tciThis = (TableColumnItem)en.nextElement();
			TableColumnItem tciThat = rToCompare.getColumn(tciThis.Name);
			if(tciThat==null) return false;
			if(!tciThis.equals(tciThat)) return false;
		}

		return true;
	}

	/**
	 * To get a column String value
	 */
	public String getColumnValueString(String sColumnName)
	{
		if(sColumnName==null) return "";
		TableColumnItem tci = getColumn(sColumnName); //(TableColumnItem)m_htColumns.get(paramColumnName.toUpperCase());
		if(tci!=null)
		{
			return tci.getStringValue();
		}

		return "";
	}



	/**
	 * for adding character data types
	 */
	public boolean addColumn(String paramColumnName, String paramColumnValue, boolean isPrimaryKey, String ForeignKey)
	{
		if(paramColumnName==null || m_htColumns.containsKey(paramColumnName.toUpperCase())) return false;
		byte[] bValue=null;
		try
		{
			if(paramColumnValue != null ) bValue = paramColumnValue.getBytes("UTF-8");
		}catch(Exception e){}

		TableColumnItem tci = new TableColumnItem(paramColumnName, TableColumnItem.ITEM_TYPE_CHAR, isPrimaryKey, ForeignKey, bValue);
		m_htColumns.put(paramColumnName.toUpperCase(), tci);
		return true;
	}

	/**
	 * for adding SQL Integer data types
	 */
	public boolean addColumn(String paramColumnName, long paramColumnValue, boolean isPrimaryKey, String ForeignKey)
	{
		if(paramColumnName==null || m_htColumns.containsKey(paramColumnName.toUpperCase())) return false;
		String szValue = "" + paramColumnValue;
		TableColumnItem tci = new TableColumnItem(paramColumnName, TableColumnItem.ITEM_TYPE_INTEGER, isPrimaryKey, ForeignKey, Util.utf8FromString(szValue));
		m_htColumns.put(paramColumnName.toUpperCase(), tci);
		return true;
	}


	/**
	 * for adding SQL Numeric data types
	 */
	public boolean addColumn(String paramColumnName, double paramColumnValue, boolean isPrimaryKey, String ForeignKey)
	{
		if(paramColumnName==null || m_htColumns.containsKey(paramColumnName.toUpperCase())) return false;
		String szValue = "" + paramColumnValue;
		TableColumnItem tci = new TableColumnItem(paramColumnName, TableColumnItem.ITEM_TYPE_NUMERIC, isPrimaryKey, ForeignKey, Util.utf8FromString(szValue));
		m_htColumns.put(paramColumnName.toUpperCase(), tci);
		return true;
	}


	/**
	 * for adding SQL Blob data types
	 */
	public boolean addColumn(String paramColumnName, byte paramColumnValue[])
	{
		if(paramColumnName==null || m_htColumns.containsKey(paramColumnName.toUpperCase())) return false;

		TableColumnItem tci = new TableColumnItem(paramColumnName, TableColumnItem.ITEM_TYPE_BLOB, false, "", paramColumnValue);
		m_htColumns.put(paramColumnName.toUpperCase(), tci);
		return true;
	}


	/**
	 * for adding SQL LONGTEXT data types
	 */
	public boolean addColumn(String paramColumnName, String paramColumnValue)
	{
		if(paramColumnName==null || m_htColumns.containsKey(paramColumnName.toUpperCase())) return false;
		byte[] bValue=null;

		try
		{
			if(paramColumnValue != null ) bValue = paramColumnValue.getBytes("UTF-8");
		}catch(Exception e){}

		TableColumnItem tci = new TableColumnItem(paramColumnName, TableColumnItem.ITEM_TYPE_TEXT, false, "", bValue);
		m_htColumns.put(paramColumnName.toUpperCase(), tci);
		return true;
	}


	/**
	 * for adding SQL DATE and TIMESTAMP data types
	 */
	public boolean addColumn(String paramColumnName, java.util.Date paramColumnValue)
	{
		String szDate = null;
		byte Value[] = null;
		if(paramColumnName==null || m_htColumns.containsKey(paramColumnName.toUpperCase())) return false;

		if(paramColumnValue!=null)
		{
			szDate = String.valueOf(paramColumnValue.getTime());
			Value = Util.utf8FromString(szDate);
		}
		TableColumnItem tci = new TableColumnItem(paramColumnName, TableColumnItem.ITEM_TYPE_DATE, false, "", Value);
		m_htColumns.put(paramColumnName.toUpperCase(), tci);
		return true;
	}

	/**
	 * removes a column from the row
	 * @param paramColumnName
	 */
	public void removeColumn(String paramColumnName)
	{
		if(paramColumnName==null) return; 
		if(m_htColumns.containsKey(paramColumnName.toUpperCase()))
		{
			m_htColumns.remove(paramColumnName.toUpperCase());
		}
	}

	/**
	 * For debug purposes...
	 */
	public String toString()
	{
		String szReturn="TABLE:[" + this.m_sTableName + "]\r\n";
		Enumeration en = m_htColumns.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tci = (TableColumnItem)en.nextElement();
			/*switch(tci.Type)
			{
			case TableColumnItem.ITEM_TYPE_BLOB:
				szReturn += tci.Name + ": ** BLOB **";
				break;
			default:
				String szTmp;
			if(tci.isNull()) szTmp = "-NULL-"; 
			else szTmp = tci.getStringValue();

			szReturn += tci.Type + " " + tci.Name + ": [" + szTmp + "]";
			break;
			};
			szReturn += "\r\n";
			 */
			szReturn += tci.toString() + "\r\n";
		}
		return szReturn;
	}

	/**
	 * Copies all tci's to the document
	 */
	public void copyToDocument(Document doc)
	{
		Enumeration en = m_htColumns.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tci = (TableColumnItem)en.nextElement();
			switch(tci.Type)
			{
			case TableColumnItem.ITEM_TYPE_INTEGER:
				String s = new String(tci.Value);
				if(s.length()==0) s = "0";            
				new DocumentItem(doc, tci.Name, Long.parseLong(s));            
				break;
			case TableColumnItem.ITEM_TYPE_NUMERIC:
				String sD = new String(tci.Value);
				if(sD.length()==0) sD = "0";    
				Float f = new Float(sD);
				new DocumentItem(doc, tci.Name, f.doubleValue());
				break;
			case TableColumnItem.ITEM_TYPE_DATE:
				String szDateTmp = new String(tci.Value);
				if(szDateTmp.length()==0) szDateTmp = ""+System.currentTimeMillis();
				Float fLong = new Float(szDateTmp);  
				java.util.Date dtNull = null;
				java.util.Date dtDate = new java.util.Date(fLong.longValue());
				DocumentItem didate = new DocumentItem(doc, tci.Name, dtDate);
				if(tci.Value==null) didate.setDateValue(dtNull);
				break;
			case TableColumnItem.ITEM_TYPE_BLOB:
				byte bufTmp[]=null;
				if(tci.Value!=null) bufTmp = tci.Value;
				new DocumentItem(doc, tci.Name, bufTmp);
				break;
			default:
				String szTmp=null;
			if(tci.Value!=null) szTmp = new String(tci.Value);
			new DocumentItem(doc, tci.Name, szTmp);            
			break;
			};
		}//while
	}

	/**
	 * @return the position of a given column or -1 if not found
	 */
	public int getColumnPosition(String sColumnName)
	{
		int iPos=0;

		Enumeration en = m_htColumns.elements();
		while(en.hasMoreElements())
		{
			iPos++;
			TableColumnItem tci = (TableColumnItem)en.nextElement();
			if(tci.Name.equalsIgnoreCase(sColumnName)) return iPos;
		}
		return -1;
	}


	/**
	 * Only count those columns that are selectable
	 * @return the position of a given column or -1 if not found
	 */
	public int getSelectableColumnPosition(String sColumnName)
	{
		int iPos=0;

		Enumeration en = m_htColumns.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tci = (TableColumnItem)en.nextElement();
			if(tci.isSelectable() && !tci.isPrimaryKey) iPos++;
			if(tci.Name.equalsIgnoreCase(sColumnName)) return iPos;
		}
		return -1;
	}

	/**
	 * 
	 * @param sJDBCDriverName
	 */
	private void setDriverName(String sJDBCDriverName)
	{
		m_sJDBCDriverName = sJDBCDriverName;
	}

	/**
	 * 
	 * @param cx
	 * @return
	 */
	public String getJDBCDriver(Connection cx)
	{   
		if(m_sJDBCDriverName!=null && m_sJDBCDriverName.length()>0) return m_sJDBCDriverName;
		if(cx==null) return "??";

		try
		{			
			Class c = cx.getClass();           
			m_sJDBCDriverName = c.getName();
		}
		catch(Exception e){}
		//m_sJDBCDriver = m_SessCtx.getDataConnectionProperty(m_sConnectionName, "DBDriver");
		return m_sJDBCDriverName;
	}

	public PreparedStatement prepare(Connection cx, boolean bSelect)
	{
		return prepare(cx, bSelect, -1);
	}
	
	/**
	 * Creates an SQL insert clause for use with a prepared statement
	 * @param cx
	 * @param bSelect
	 * @param iPrepareOptions eg Statement.RETURN_GENERATED_KEYS
	 * @return
	 */
	public PreparedStatement prepare(Connection cx, boolean bSelect, int iPrepareOptions)
	{

		PreparedStatement prepStmt=null;
		int i=1;



		//
		try
		{
			setDriverName(getJDBCDriver(cx));
			boolean bIsOracle = m_sJDBCDriverName.toLowerCase().indexOf("oracle")>=0;
			//System.out.println("driver:"+cx.getMetaData().getDriverName());
			//System.out.println("product:"+cx.getMetaData().getDatabaseProductName());

			String sSQL = null;
			if(bSelect)			
				sSQL = getSelectSQL(cx);			
			else
				sSQL = getInsertSQL();
			//System.out.println(szSQL);

			if(iPrepareOptions>=0)				
				prepStmt = cx.prepareStatement(sSQL, iPrepareOptions);
			else
				prepStmt = cx.prepareStatement(sSQL);
			Enumeration en = m_htColumns.elements();
			while(en.hasMoreElements())
			{
				String szTmp;
				TableColumnItem tci = (TableColumnItem)en.nextElement();
				boolean bEmptyString = (tci.Value!=null && (tci.Type==TableColumnItem.ITEM_TYPE_CHAR || tci.Type==TableColumnItem.ITEM_TYPE_TEXT) && tci.getStringValue().length()==0);
				if(!(bSelect && tci.isNull())
						&& !(bSelect && tci.isPrimaryKey)
						&& !(bSelect && !tci.isSelectable())
						&& !(!bSelect && tci.isPrimaryKey)
						&& !(bSelect && bIsOracle && bEmptyString))
				{
					szTmp = tci.Name;
					//System.out.println("Setting: " + szTmp);
					if(tci.isNull())
					{						
						prepStmt.setNull(i, tci.getSQLType());					
					}
					else
					{
						switch(tci.Type)
						{
						case TableColumnItem.ITEM_TYPE_INTEGER:
							String s = new String(tci.Value);
							if(s.length()==0) s = "0";
							prepStmt.setInt(i, (int)Util.toInteger(s));
							break;
						case TableColumnItem.ITEM_TYPE_NUMERIC:
							String sF = new String(tci.Value);
							if(sF.length()==0) s = "0";
							Float f = new Float(sF);
							prepStmt.setDouble(i, f.doubleValue());
							break;
						case TableColumnItem.ITEM_TYPE_DATE:
							szTmp = new String(tci.Value);
							if(szTmp.length()==0) //szTmp = ""+System.currentTimeMillis();
							{
								prepStmt.setNull(i, tci.getSQLType());
							}
							else
							{
								//Float fLong = new Float(szTmp);
								long lDate = Util.toInteger(szTmp);
								Timestamp ts = new Timestamp(lDate); //fLong.longValue());
								prepStmt.setTimestamp(i, ts);                
								//java.sql.Date dtImport = new java.sql.Date(fLong.longValue());
								//prepStmt.setDate(i, dtImport);
							}
							break;
						case TableColumnItem.ITEM_TYPE_BLOB:
							prepStmt.setBytes(i, tci.Value);
							break;
						case TableColumnItem.ITEM_TYPE_CHAR:
						case TableColumnItem.ITEM_TYPE_TEXT:
						default:         
						{
							String sValue = tci.getStringValue();
							if(bIsOracle && bSelect && sValue.length()==0) //@see http://www.adp-gmbh.ch/ora/misc/null.html							
								prepStmt.setNull(i, tci.getSQLType());							
							else
								prepStmt.setString(i, sValue);
							break;
						}
						};//switch
					}//else not null column

					i++;
				}//if
			}
		}
		catch(Exception e)
		{
			System.err.println("TableRow.prepare() " + e.toString());
			e.printStackTrace();
		}

//		System.out.println("Cols=" + i);
		return prepStmt;
	}

	/**
	 * Creates an SQL insert clause for use with a prepared statement
	 */
	private String getInsertSQL()
	{
		//String szSQL="INSERT INTO " + m_sTableName + "(";
		StringBuilder sbQuestionMarks= new StringBuilder(50);
		StringBuilder sbColNames= new StringBuilder(1024);
		sbColNames.append("INSERT INTO " + m_sTableName + "(");
		int iAddCols=0;

		Enumeration en = m_htColumns.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tci = (TableColumnItem)en.nextElement();
			String sColumnName = escapeReservedWordColumn(tci.Name);
			if(!tci.isPrimaryKey) //these will be autoinc columns
			{
				//if(tci.isPrimaryKey) System.out.print(tci.Name + " val="+new String(tci.Value));
				if(iAddCols==0)
					sbColNames.append(sColumnName);
				else
					sbColNames.append("," + sColumnName);
				iAddCols++;
			}
		}

		for(int i=0; i<iAddCols; i++)
		{
			if(sbQuestionMarks.length()==0)
				sbQuestionMarks.append("?");
			else
				sbQuestionMarks.append(",?");
		}

		sbColNames.append(") VALUES(");
		sbColNames.append(sbQuestionMarks);
		sbColNames.append(')');
		//return szSQL + szColNames + ") VALUES(" + szQuestionMarks + ")";
		return sbColNames.toString();
	}


	/**
	 * Creates an SQL insert clause for use with a prepared statement
	 */
	private String getSelectSQL(Connection cx)
	{
		StringBuilder sbColNames = new StringBuilder(256);
		sbColNames.append("SELECT * FROM ");
		sbColNames.append(m_sTableName);
		sbColNames.append(" WHERE ");
		//String szSQL="SELECT * FROM " + m_sTableName + " WHERE ";
		//String szColNames="";

		String sJDBCDriver = getJDBCDriver(cx);
		boolean bIsOracle = sJDBCDriver!=null && sJDBCDriver.toLowerCase().indexOf("oracle")>=0;	

		int iAdded=0;
		Enumeration en = m_htColumns.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tci = (TableColumnItem)en.nextElement();
			String sColumnName = escapeReservedWordColumn(tci.Name);			
			if(tci.isSelectable() && !tci.isPrimaryKey)
			{
				boolean bEmptyString = (tci.Value!=null && (tci.Type==TableColumnItem.ITEM_TYPE_CHAR || tci.Type==TableColumnItem.ITEM_TYPE_TEXT) && tci.getStringValue().length()==0);
				if(iAdded==0)
				{
					if(tci.Value==null || (bIsOracle && bEmptyString))
						sbColNames.append(sColumnName + " IS NULL");
					else
						sbColNames.append(sColumnName + "=?");
				}
				else
				{
					if(tci.Value==null || (bIsOracle && bEmptyString))
						sbColNames.append(" AND " + sColumnName + " IS NULL");
					else
						sbColNames.append(" AND " + sColumnName + "=?");
				}
				iAdded++;
			}

		}

		return sbColNames.toString();
	}


	/**
	 * 
	 * @param sColumnName
	 * @return
	 */
	private String escapeReservedWordColumn(String sColumnName) 
	{		
		if(m_sJDBCDriverName!=null && m_sJDBCDriverName.length()>0) 
		{
			boolean bIsOracle = m_sJDBCDriverName.toLowerCase().indexOf("oracle")>=0;
			//go oracle you piece of crap. Comment is a reserved word?!?
			if(bIsOracle && sColumnName.equalsIgnoreCase("Comment")) 
			{
				//System.out.println("esc:"+sColumnName);
				return "\"COMMENT\"";
			}
		}
		return sColumnName;
	}


	/**
	 *
	 * @param szNewTableName
	 */
	public synchronized void setTableName(String szNewTableName)
	{
		m_sTableName = szNewTableName;
	}


	/**
	 *
	 *
	 */
	public String getTableName()
	{
		return m_sTableName;
	}

	public Hashtable getColumns()
	{
		return m_htColumns;
	}


	/**
	 * Convert this row into XML and return a byte array
	 * @return
	 */
	public byte[] toXML()
	{
		//byte buffer[]=null;
		StringBuilder sbOut = new StringBuilder(12500);
		puakma.coder.CoderB64 b64 = new puakma.coder.CoderB64();

		sbOut.append("<row table=\"");
		sbOut.append(m_sTableName);
		sbOut.append("\">\r\n");
		Enumeration en = m_htColumns.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tci = (TableColumnItem)en.nextElement();
			sbOut.append("\t<item");
			sbOut.append(" name=\"");
			sbOut.append(tci.Name);
			sbOut.append('\"');
			sbOut.append(" type=\"");
			sbOut.append(tci.Type);
			sbOut.append('\"');
			sbOut.append(" foreignkeyto=\"");
			sbOut.append(tci.ForeignKeyTo);
			sbOut.append('\"');
			sbOut.append(" isprimarykey=\"");
			sbOut.append(tci.isPrimaryKey);
			sbOut.append("\">\r\n");

			//buffer = puakma.util.Util.appendBytes(buffer, tci.Value);
			if(tci.isNull())
				sbOut.append("\t\t<value isNull=\"true\"/>\r\n");
			else
			{
				sbOut.append("\t\t<value isNull=\"false\" encoding=\"base64\">");
				sbOut.append(b64.encode(tci.Value));
				sbOut.append("</value>\r\n");
			}

			sbOut.append("\t</item>\r\n");
		}
		sbOut.append("</row>\r\n");

		try { return sbOut.toString().getBytes("UTF-8"); }catch(Exception e){}

		return new byte[0];
	}
}