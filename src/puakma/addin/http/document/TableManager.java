/* ***************************************************************
TableManager.java
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
package puakma.addin.http.document;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.DocumentItem;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.util.Util;



/**
 * <p>TableManager is used for managing Document objects in and out of relational tables.
 * This object is designed to deal with one table at a time. Complex queries and
 * complex updates should use the standard JDBC structs (eg Statement/PreparedStatements).
 * This class is used in Action code.
 * </p>
 * <p>The following example gets a row from a relational database and applies it to the ActionDocument.
 * This allows fields on the current page to be prefilled with data from the database (where the field 
 * names match the database column names).
 * </p> 
 * <code>
 * TableManager t = new TableManager(pSystem, pSession, "YourConnectionName", "ATABLENAME");
 * t.populateDocument("SELECT * FROM ATABLENAME WHERE tid=1", ActionDocument);
 * </code>
 * <br>
 * <p>The following shows how to insert a new row into the database.</p>
 * <code>
 * TableManager t = new TableManager(pSystem, pSession, "YourConnectionName", "ATABLENAME");
 * t.setField("col1", 34);
 * t.setField("col2", "Some Name");
 * t.insertRow();
 * </code>
 * <br>
 * <p>The following shows how to perform an update on an existing database row.</p>
 * <code>
 * TableManager t = new TableManager(pSystem, pSession, "YourConnectionName", "ATABLENAME");
 * t.setField("col1", 34);
 * t.setField("col2", "Some Name");
 * t.updateRow("WHERE tid=7");
 * </code>
 * <br>
 * <p>TableManager also allows the execution of adhoc queries.</p>
 * <code>
 * TableManager t = new TableManager(pSystem, pSession, "YourConnectionName", "ATABLENAME");
 * t.executeQuery("DELETE FROM ATABLENAME WHERE tid=22");
 * </code>
 */
public class TableManager implements ErrorDetect
{
	private HTTPSessionContext m_sessCtx; 
	private SystemContext m_sysCtx;
	private String m_sConnectionName;
	private HTMLDocument m_docData;
	private String m_sTableName;
	private static final int TRACE_DEPTH=-1;

	private String m_sLastError="";
	private String m_sJDBCDriver="";
	private long m_lLastInsertID=-1;
	private String m_sDefaultProtocol=null;
	private String m_sDefaultHost=null;
	private Connection m_customConnection;


	/**
	 * @deprecated 4.March.09 The pSystem parameter is redundant so was removed
	 * @param pSystem
	 * @param pSession
	 * @param sConnectionName
	 * @param sTableName
	 */
	public TableManager(SystemContext pSystem, HTTPSessionContext pSession, String sConnectionName, String sTableName)
	{
		m_sessCtx = pSession;
		m_sysCtx = pSystem;
		m_sConnectionName = sConnectionName;
		m_docData = new HTMLDocument(m_sessCtx);
		m_sTableName = sTableName;
	}

	/**
	 * 
	 * @param pSession
	 * @param sConnectionName
	 * @param sTableName
	 */
	public TableManager(HTTPSessionContext pSession, String sConnectionName, String sTableName)
	{
		m_sessCtx = pSession;
		m_sysCtx = pSession.getSystemContext();
		m_sConnectionName = sConnectionName;
		m_docData = new HTMLDocument(m_sessCtx);
		m_sTableName = sTableName;
	}

	/**
	 * Get a String array containing a list of column names from the internal TableManager document.
	 * eg these columns are populated with a t.populateDocument(...);
	 * @return A String array of the column names. Will return a zero length String if no columns exist 
	 */
	public String[] getColumnNames()
	{
		if(m_docData==null) return new String[0];
		ArrayList arr = new ArrayList(m_docData.getItemCount());
		Enumeration en = m_docData.getAllItems();
		while(en.hasMoreElements())
		{
			DocumentItem di = (DocumentItem)en.nextElement();
			arr.add(di.getName());
		}
		return Util.objectArrayToStringArray(arr.toArray());
	}

	/**
	 *
	 */
	private Connection getConnection() throws Exception
	{
		if(m_customConnection!=null) return m_customConnection;

		if(m_sConnectionName!=null && m_sConnectionName.equals(SystemContext.DBALIAS_SYSTEM)) return m_sysCtx.getSystemConnection();
		return m_sessCtx.getDataConnection(m_sConnectionName);
	}

	/**
	 * Allows a custom connection to be created outside and passed in
	 * @param cx
	 */
	public void setCustomConnection(Connection cx)
	{
		m_customConnection = cx;
	}
	/**
	 * Return the database connection back to the pool. Force a commit.
	 * If a custom connection is used, then DO NOT RELEASE IT. We'll assume the same connection will be used for the life of this object.
	 * @param cx
	 */
	private void releaseConnection(Connection cx)
	{

		//Force a commit here. Autocommit is most likely ON already, but
		//as connections are pooled, the autocommit may not fire, thus triggers will
		//not run. Pop in a try/catch because we don't care about the outcome so much :-)
		try{ cx.commit(); }catch(Exception e){}

		if(m_customConnection!=null) return;

		if(m_sConnectionName!=null && m_sConnectionName.equals(SystemContext.DBALIAS_SYSTEM)) 
			m_sessCtx.getSystemContext().releaseSystemConnection(cx);
		else
			m_sessCtx.releaseDataConnection(cx);
	}

	/**
	 * returns the name of the JDBC driver used for this connection
	 */
	public String getJDBCDriver()
	{   
		if(m_sJDBCDriver!=null && m_sJDBCDriver.length()>0) return m_sJDBCDriver;

		Connection cx=null;
		try
		{
			cx=getConnection();
			/*DatabaseMetaData dbmd = cx.getMetaData();
          m_sJDBCDriver = dbmd.getDriverName();
			 */
			Class c = cx.getClass();           
			m_sJDBCDriver = c.getName();
		}
		catch(Exception e){}
		finally
		{
			releaseConnection(cx);
		}
		return m_sJDBCDriver;
	}

	/**
	 * Get the name of the currently active table that will be used for updates
	 * @return The table name currently in use
	 */
	public String getTableName()
	{
		return m_sTableName;
	}

	/**
	 * removes the old document so we have a clean slate to work with
	 */
	public void clearDocument()
	{
		m_sLastError = "";
		m_docData = new HTMLDocument(m_sessCtx);
	}

	/**
	 * set the table name where all updates will go into
	 * @param sTableName
	 */
	public synchronized void setTableName(String sTableName)
	{
		m_sTableName = sTableName;
	}

	/**
	 *
	 */
	public boolean contentEquals(TableManager tmToCompare)
	{
		return tmToCompare.contentEquals(m_docData);
	}

	/**
	 * Compare the content of this object with the passed document
	 */
	public boolean contentEquals(puakma.system.Document docToCompare)
	{
		return docToCompare.equals(m_docData);
	}

	public void setFieldInteger(String sFieldName, String sFieldValue)
	{     
		long lValue = 0;
		try{ lValue = Long.parseLong(sFieldValue); }catch(Exception e){}
		m_docData.replaceItem(sFieldName, lValue);
	}

	public void setFieldNumeric(String sFieldName, String sFieldValue)
	{     
		double dblValue = 0;
		try{ dblValue = Double.parseDouble(sFieldValue); }catch(Exception e){}
		m_docData.replaceItem(sFieldName, dblValue);
	}

	public void setFieldJSON(String sFieldName, String sFieldValue)
	{     
		m_docData.removeItem(sFieldName);
		DocumentItem di = new DocumentItem(m_docData, sFieldName, sFieldValue);
		di.setType(DocumentItem.ITEM_TYPE_JSON);

		//setFieldNull(sFieldName, DocumentItem.ITEM_TYPE_BUFFER);
	}

	public void setField(String sFieldName, JSONObject json)
	{
		setFieldJSON(sFieldName, json==null ? null : json.toString());
	}

	public void setField(String sFieldName, String sFieldValue)
	{
		m_docData.replaceItem(sFieldName, sFieldValue);
	}

	public void setField(String sFieldName, long lFieldValue)
	{
		m_docData.replaceItem(sFieldName, lFieldValue);
	}

	public void setField(String sFieldName, double dblFieldValue)
	{
		m_docData.replaceItem(sFieldName, dblFieldValue);
	}

	public void setField(String sFieldName, java.util.Date dtFieldValue)
	{
		m_docData.replaceItem(sFieldName, dtFieldValue);
	}

	public void setField(String sFieldName, byte[] btFieldValue)
	{
		m_docData.replaceItem(sFieldName, btFieldValue);
	}

	/**
	 * Sets a string field to null
	 */
	public void setFieldNull(String sFieldName)
	{
		m_docData.setItemNull(sFieldName);
	}

	/**
	 * Sets an integer field to null
	 */
	public void setFieldIntegerNull(String sFieldName)
	{    
		/*m_docData.replaceItem(sFieldName, "");
		m_docData.setItemNull(sFieldName);
		DocumentItem di = m_docData.getItem(sFieldName);
		if(di!=null)
		{
			di.setType(DocumentItem.ITEM_TYPE_INTEGER);
		}*/
		setFieldNull(sFieldName, DocumentItem.ITEM_TYPE_INTEGER);
	}

	/**
	 * Sets a date field to null
	 * @param sFieldName
	 */
	public void setFieldDateNull(String sFieldName)
	{    
		/*m_docData.replaceItem(sFieldName, "");
		m_docData.setItemNull(sFieldName);
		DocumentItem di = m_docData.getItem(sFieldName);
		if(di!=null)
		{
			di.setType(DocumentItem.ITEM_TYPE_DATE);			
		}*/
		setFieldNull(sFieldName, DocumentItem.ITEM_TYPE_DATE);
	}

	/**
	 * 
	 * @param sFieldName
	 */
	public void setFieldByteNull(String sFieldName)
	{    
		/*m_docData.replaceItem(sFieldName, "");
		m_docData.setItemNull(sFieldName);
		DocumentItem di = m_docData.getItem(sFieldName);
		if(di!=null)
		{
			di.setType(DocumentItem.ITEM_TYPE_BUFFER);			
		}*/
		setFieldNull(sFieldName, DocumentItem.ITEM_TYPE_BUFFER);
	}

	/**
	 * Sets an integer field to null
	 */
	public void setFieldNumericNull(String sFieldName)
	{    
		/*m_docData.replaceItem(sFieldName, "");
		m_docData.setItemNull(sFieldName);
		DocumentItem di = m_docData.getItem(sFieldName);
		if(di!=null)
		{
			di.setType(DocumentItem.ITEM_TYPE_NUMERIC);
		}*/
		setFieldNull(sFieldName, DocumentItem.ITEM_TYPE_NUMERIC);
	}

	/**
	 * For setting null field values
	 * @param sFieldName
	 * @param iItemType DocumentItem.ITEM_TYPE_xxxx
	 */
	private void setFieldNull(String sFieldName, int iItemType)
	{
		m_docData.replaceItem(sFieldName, "");
		m_docData.setItemNull(sFieldName);
		DocumentItem di = m_docData.getItem(sFieldName);
		if(di!=null)
		{
			di.setType(iItemType);
		}
	}


	/**
	 * Determines if a field contains a null value. If the item does not exist also 
	 * returns true.
	 * @return true of the item is null
	 */
	public boolean isFieldNull(String sFieldName)
	{
		return m_docData.isItemNull(sFieldName);
	}

	/**
	 * Get the type of the field as stored on the internal document (NOT the database column type).
	 * eg DocumentItem.ITEM_TYPE_STRING
	 * @param sFieldName
	 * @return -1 if the field does not exist
	 */
	public int getFieldType(String sFieldName)
	{
		DocumentItem di = m_docData.getItem(sFieldName);
		if(di!=null)		
			return di.getType();

		return -1;
	}

	/**
	 * Get the string value of a field.
	 * @return "" if null or empty
	 */
	public String getFieldString(String sFieldName)
	{
		return m_docData.getItemValue(sFieldName);
	}

	/**
	 * Get an array of bytes from a query result. This method is useful for
	 * retrieving file attachments from a database
	 * @param sFieldName
	 * @return null or the specified block of bytes
	 */
	public byte[] getFieldBytes(String sFieldName)
	{
		DocumentItem di = m_docData.getItem(sFieldName);
		if(di!=null && di.getType()==DocumentItem.ITEM_TYPE_BUFFER)
		{
			return di.getValue();
		}
		return null;
	}


	public java.util.Date getFieldDate(String sFieldName)
	{
		return m_docData.getItemDateValue(sFieldName);
	}

	public long getFieldInteger(String sFieldName)
	{
		return m_docData.getItemIntegerValue(sFieldName);
	}

	public double getFieldNumeric(String sFieldName)
	{
		return m_docData.getItemNumericValue(sFieldName);
	}


	public void removeField(String sFieldName)
	{
		m_docData.removeItem(sFieldName);
	}

	public boolean hasField(String sFieldName)
	{
		return m_docData.hasItem(sFieldName);
	}


	/**
	 * set the name of the database connection to use
	 * @param sConnectionName
	 */
	public synchronized void setConnectionName(String sConnectionName)
	{
		m_sConnectionName = sConnectionName;
	}


	/**
	 * Applies the data in m_docData to the table
	 * @return true if the row was saved successfully
	 */
	public boolean insertRow()
	{
		return saveRowInternal("", false, null);
	}

	/**
	 * Ask for a specific field for the generated key
	 * @param sGeneratedKeyFieldName
	 * @return
	 */
	public boolean insertRow(String sGeneratedKeyFieldName)
	{
		return saveRowInternal("", false, sGeneratedKeyFieldName);
	}


	/**
	 * Applies the data in m_docData to the table
	 * @param sWhereClause ("WHERE X=3" etc)
	 * @return true if the row was saved successfully
	 */
	public boolean updateRow(String sWhereClause)
	{
		return saveRowInternal(sWhereClause, true, null);
	}

	/**
	 * 
	 * @param sWhereClause
	 * @param bUpdate
	 * @return
	 * @deprecated
	 */	
	public boolean saveRow(String sWhereClause, boolean bUpdate)
	{
		return saveRowInternal(sWhereClause, bUpdate, null);
	}

	/**
	 * Applies the data in m_docData to the table
	 * @param sWhereClause ("WHERE X=3" etc), bUpdate (false does an insert, true does an update)
	 * @return true if the row was saved successfully
	 */
	private boolean saveRowInternal(String sWhereClause, boolean bUpdate, String sGeneratedKeyFieldName)
	{
		StringBuilder sbSQL = new StringBuilder(256);
		StringBuilder sbMoreSQL=new StringBuilder(256), sbValuesSQL=new StringBuilder(256);
		int iPosition=1;
		Connection cx=null;    
		boolean bSavedOK=false;

		if(bUpdate)
		{
			sbSQL.append("UPDATE ");
			sbSQL.append(m_sTableName);
			sbSQL.append(" SET ");
		}
		else //insert
		{
			sbSQL.append("INSERT INTO ");
			sbSQL.append(m_sTableName);
			sbSQL.append('(');
		}
		Enumeration en = m_docData.getAllItems();
		while(en.hasMoreElements())
		{
			DocumentItem di = (DocumentItem)en.nextElement();
			String sColName = escapeReservedWordColumn(di.getName());	
			String sValuePlaceHolder = di.getType()==DocumentItem.ITEM_TYPE_JSON ? "to_json(?::json)" : "?"; //this is for postgresql...
			if(bUpdate)
			{
				if(sbMoreSQL.length()==0)
				{
					sbMoreSQL.append(sColName);
					//sbMoreSQL.append("=?");
					sbMoreSQL.append("=" + sValuePlaceHolder);
				}
				else
				{
					sbMoreSQL.append(',');
					sbMoreSQL.append(sColName);
					//sbMoreSQL.append("=?");
					sbMoreSQL.append("=" + sValuePlaceHolder);
				}
			}
			else
			{
				if(sbMoreSQL.length()==0)
					sbMoreSQL.append(sColName);
				else
				{
					sbMoreSQL.append(',');
					sbMoreSQL.append(sColName);
				}

				if(sbValuesSQL.length()==0)
					//sbValuesSQL.append('?');
					sbValuesSQL.append(sValuePlaceHolder);
				else
					//sbValuesSQL.append(",?");
					sbValuesSQL.append(","+sValuePlaceHolder);
			}

		}//while


		if(bUpdate)
		{
			sbSQL.append(sbMoreSQL.toString());
			sbSQL.append(' ');
			sbSQL.append(sWhereClause);
		}
		else
		{
			sbSQL.append(sbMoreSQL.toString());
			sbSQL.append(") VALUES (");
			sbSQL.append(sbValuesSQL.toString());
			sbSQL.append(')');
		}

		//System.out.println("SQL: " + szSQL);
		m_sysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "TableManager.saveRow() SQL: " + sbSQL.toString(), m_sysCtx);
		//now fill in the prepared statement stuff
		PreparedStatement prepStmt = null;
		try
		{
			cx = getConnection();      
			//prepStmt = cx.prepareStatement(sbSQL.toString(), PreparedStatement.RETURN_GENERATED_KEYS);
			if(bUpdate)
				prepStmt = cx.prepareStatement(sbSQL.toString());
			else
				prepStmt = cx.prepareStatement(sbSQL.toString(), PreparedStatement.RETURN_GENERATED_KEYS);
			iPosition=1;
			en = m_docData.getAllItems();
			while(en.hasMoreElements())
			{
				DocumentItem di = (DocumentItem)en.nextElement();

				switch(di.getType())
				{
				case DocumentItem.ITEM_TYPE_DATE:
					if(di.isNull())
						prepStmt.setNull(iPosition, Types.TIMESTAMP);
					else
						prepStmt.setTimestamp(iPosition, new Timestamp(di.getDateValue().getTime()));
					break;
				case DocumentItem.ITEM_TYPE_NUMERIC:
					if(di.isNull())
						prepStmt.setNull(iPosition, Types.NUMERIC);
					else
						prepStmt.setDouble(iPosition, di.getNumericValue());
					break;
				case DocumentItem.ITEM_TYPE_INTEGER:
					if(di.isNull())
						prepStmt.setNull(iPosition, Types.INTEGER);
					else
						prepStmt.setLong(iPosition, di.getIntegerValue());
					break;
				case DocumentItem.ITEM_TYPE_FILE:
				case DocumentItem.ITEM_TYPE_BUFFER:
					if(di.isNull())
						prepStmt.setNull(iPosition, Types.BINARY); //was Types.BLOB but postgres complained :-(
					else
						prepStmt.setBytes(iPosition, di.getValue());
					break;
				default:
					if(di.isNull())
						prepStmt.setNull(iPosition, Types.VARCHAR);
					else					
						prepStmt.setString(iPosition, di.getStringValue());
				};
				iPosition++;
			}//while          
			prepStmt.executeUpdate();
			bSavedOK = true;

			//damn postgres throws an exception way above: org.postgresql.util.PSQLException: Returning autogenerated keys is not supported.
			if(!bUpdate) //if an insert
			{     
				try{
					setLastInsertID(prepStmt, sGeneratedKeyFieldName);					
				}catch(Exception y)
				{
					m_sysCtx.doError("TableManager.GeneratedKeysError", new String[]{y.toString()}, this);
					Util.logStackTrace(y, m_sysCtx, -1);
				} //don't log this, not all db's support getGeneratedKeys()
			}

		}
		catch(Exception e)
		{
			m_sLastError = e.toString();
			m_sysCtx.doError("TableManager.SaveError", new String[]{e.toString()}, this);
			puakma.util.Util.logStackTrace(e, m_sysCtx, this, TRACE_DEPTH);
			bSavedOK = false;
		}
		finally
		{        
			Util.closeJDBC(prepStmt);
			releaseConnection(cx);
		}
		return bSavedOK;
	}

	private void setLastInsertID(PreparedStatement prepStmt, String sGeneratedKeyFieldName) throws Exception 
	{
		m_lLastInsertID = -1; //reset

		ResultSet rsKeys = prepStmt.getGeneratedKeys();
		if(rsKeys!=null)
		{							
			if(rsKeys.next())
			{
				ResultSetMetaData rsmd = rsKeys.getMetaData();
				//if there's many columns and we haven't specified a key, use the primary key column
				//Not recommended: Programmer should be specifying the column in t.insertRow("MyAutoGenColumnName")
				if(rsmd.getColumnCount()>1 && (sGeneratedKeyFieldName==null || sGeneratedKeyFieldName.length()==0))
				{
					sGeneratedKeyFieldName = getFirstPrimaryKeyColumnName(prepStmt); 
				}
				int column = getResultSetColumn(rsKeys, rsmd, sGeneratedKeyFieldName);
				if(column<1) column = 1;
				m_lLastInsertID = rsKeys.getLong(column);
			}
			rsKeys.close();
		}
		
	}

	private String getFirstPrimaryKeyColumnName(PreparedStatement prepStmt) 
	{		
		ResultSet tables = null;
		ResultSet primaryKeys = null;
		try
		{
			DatabaseMetaData dbmd = prepStmt.getConnection().getMetaData();
			tables = dbmd.getTables(null, null, "%", new String[] { "TABLE" }); //somewhat inefficient, gets all the tables. Case sensitivity
			while (tables.next()) 
			{
				String catalog = tables.getString("TABLE_CAT");
				String schema = tables.getString("TABLE_SCHEM");
				String tableName = tables.getString("TABLE_NAME");
				if(tableName.equalsIgnoreCase(m_sTableName))
				{
					//System.out.println("Table: " + tableName);
					primaryKeys = dbmd.getPrimaryKeys(catalog, schema, tableName); 
					if (primaryKeys.next()) 
					{
						//System.out.println("Primary key: " + primaryKeys.getString("COLUMN_NAME"));
						return primaryKeys.getString("COLUMN_NAME");
					}
				}
			}
			// similar for exportedKeys
		}
		catch(Exception e)
		{
			System.err.println("getPrimaryKeyColumnName() " + e.toString());
		}
		finally
		{
			Util.closeJDBC(tables);
			Util.closeJDBC(primaryKeys);
		}
		return null;
	}

	private static int getResultSetColumn(ResultSet rs, ResultSetMetaData rsmd, String sColumnName) 
	{
		if(sColumnName==null || sColumnName.length()==0) return -1; 
		try
		{			
			for(int column=1; column<=rsmd.getColumnCount(); column++)
			{
				String sLabel = rsmd.getColumnLabel(column);
				if(sLabel==null) sLabel = "";
				//we are dealing with a single table insert, so make case insensitive
				if(sColumnName.equalsIgnoreCase(sLabel)) return column;
			}
			//return rs.findColumn(sColumnName); //Warning: case sensitive https://bugs.mysql.com/bug.php?id=96398
		}
		catch(Exception e) {}
		return -1;
	}

	/**
	 * 
	 * @param sColumnName
	 * @return
	 */
	private String escapeReservedWordColumn(String sColumnName) 
	{		
		String sJDBCDriverName = getJDBCDriver();
		if(sJDBCDriverName!=null && sJDBCDriverName.length()>0) 
		{
			boolean bIsOracle = sJDBCDriverName.toLowerCase().indexOf("oracle")>=0;
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
	 * Based on the data in the internal document, gets the associated row from the database.
	 * You need to manually set the fields!
	 * @return true if a row matching the criteria was found. Also populates the internal document
	 *  with all columns from the matching row
	 */
	public boolean getRowFromDB()
	{
		StringBuilder sbSQL = new StringBuilder(256);
		StringBuilder sbWhereSQL=new StringBuilder(256);
		int iPosition=1;
		Connection cx=null;
		PreparedStatement prepStmt=null;
		ResultSet rs=null;
		boolean bGotOK=false;

		sbSQL.append("SELECT * FROM ");
		sbSQL.append(m_sTableName);
		sbSQL.append(" WHERE ");

		String sDriver = getJDBCDriver();
		boolean bIsOracle = sDriver!=null && sDriver.toLowerCase().indexOf("oracle")>=0;

		Enumeration en = m_docData.getAllItems();
		while(en.hasMoreElements())
		{
			DocumentItem di = (DocumentItem)en.nextElement();
			if(di.getType()!=DocumentItem.ITEM_TYPE_BUFFER)
			{
				String sColName = di.getName();
				if(bIsOracle) sColName = escapeReservedWordColumn(sColName);
				String sVal = di.getStringValue();//Oracle treats empty Strings as NULL. Larry must die				
				if(di.isNull() || (bIsOracle && sVal!=null && di.getType()==DocumentItem.ITEM_TYPE_STRING && sVal.length()==0))
				{
					/*if(sbWhereSQL.length()==0)
					{
						sbWhereSQL.append(sColName);
						sbWhereSQL.append(" IS NULL");
					}
					else
					{
						sbWhereSQL.append(" AND ");
						sbWhereSQL.append(sColName);
						sbWhereSQL.append(" IS NULL");
					}*/
					if(sbWhereSQL.length()>0) sbWhereSQL.append(" AND ");
					sbWhereSQL.append(sColName);
					sbWhereSQL.append(" IS NULL");
				}
				else
				{
					/*if(sbWhereSQL.length()==0)
					{
						sbWhereSQL.append(sColName);
						sbWhereSQL.append("=?");
					}
					else
					{
						sbWhereSQL.append(" AND ");
						sbWhereSQL.append(sColName);
						sbWhereSQL.append("=?");
					}*/
					if(sbWhereSQL.length()>0) sbWhereSQL.append(" AND ");
					sbWhereSQL.append(sColName);
					sbWhereSQL.append("=?");
				}
			}
		}//while

		sbSQL.append(sbWhereSQL.toString());

		//if(bIsOracle) m_SysCtx.doDebug(0, "TableManager.getRowFromDB() SQL: " + sbSQL.toString(), m_SysCtx);
		//System.out.println("SQL: " + sbSQL.toString());
		m_sysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "TableManager.getRow() SQL: " + sbSQL.toString(), m_sysCtx);
		//now fill in the prepared statement stuff
		try
		{
			cx = getConnection();
			prepStmt = cx.prepareStatement(sbSQL.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			prepStmt.setFetchSize(1); 
			iPosition=1;
			en = m_docData.getAllItems();
			while(en.hasMoreElements())
			{
				DocumentItem di = (DocumentItem)en.nextElement();				
				String sVal = di.getStringValue();//Oracle treats empty Strings as NULL
				if(!di.isNull() && !(bIsOracle && sVal!=null && di.getType()==DocumentItem.ITEM_TYPE_STRING && sVal.length()==0))
				{					
					switch(di.getType())
					{
					case DocumentItem.ITEM_TYPE_DATE:
						if(di.isNull())
							prepStmt.setNull(iPosition, Types.TIMESTAMP);
						else
							prepStmt.setTimestamp(iPosition, new Timestamp(di.getDateValue().getTime()));
						//System.out.println("name=" + di.getName() + " value=" + di.getStringValue());
						break;
					case DocumentItem.ITEM_TYPE_NUMERIC:
						if(di.isNull())
							prepStmt.setNull(iPosition, Types.NUMERIC);
						else
							prepStmt.setDouble(iPosition, di.getNumericValue());
						break;
					case DocumentItem.ITEM_TYPE_INTEGER:
						if(di.isNull())
							prepStmt.setNull(iPosition, Types.INTEGER);
						else
							prepStmt.setLong(iPosition, di.getIntegerValue());
						break;
					case DocumentItem.ITEM_TYPE_BUFFER: //skip!!
						iPosition--;
						break;
					default:
						if(di.isNull())
							prepStmt.setNull(iPosition, Types.VARCHAR);
						else
							prepStmt.setString(iPosition, di.getStringValue());
					};
					iPosition++;
				}//if value !=null
			}//while

			/*
			 * NOTE: Oracle is a pile of pants. It treats '' the same as NULL.
			 * http://www.adp-gmbh.ch/ora/misc/null.html
			 * "Oracle treats the empty string ('') as null. This is not ansi compliant. 
			 * Consequently, the length of an emtpy string is null, not 0."
			 */

			rs = prepStmt.executeQuery();
			if(rs.next()) //just get the first row
			{
				bGotOK = true;
				clearDocument();
				populateDocumentFromResultSet(rs, m_docData);
			}           
		}
		catch(Exception e)
		{
			m_sLastError = e.toString();
			m_sysCtx.doError("TableManager.SaveError", new String[]{e.toString()}, this);
			puakma.util.Util.logStackTrace(e, m_sysCtx, this, TRACE_DEPTH);
			bGotOK = false;
		}
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(prepStmt);
			releaseConnection(cx);
		}
		return bGotOK;
	}


	/**
	 * copies all items off the passed document into the internal representation m_docData
	 * @param docHTML
	 */
	public void setupDocument(HTMLDocument docHTML)
	{
		Enumeration en = docHTML.getAllItems();
		DocumentItem diExist;

		while(en.hasMoreElements())
		{
			diExist = (DocumentItem)en.nextElement();
			if(diExist.getName().charAt(0)!='@') // startsWith("@"))
			{
				switch(diExist.getType())
				{
				case DocumentItem.ITEM_TYPE_DATE:
					new DocumentItem(m_docData, diExist.getName(), diExist.getDateValue());
					break;
				case DocumentItem.ITEM_TYPE_NUMERIC:
					new DocumentItem(m_docData, diExist.getName(), diExist.getNumericValue());
					break;
				case DocumentItem.ITEM_TYPE_INTEGER:
					new DocumentItem(m_docData, diExist.getName(), diExist.getIntegerValue());
					break;
				case DocumentItem.ITEM_TYPE_FILE:
				case DocumentItem.ITEM_TYPE_BUFFER:
					new DocumentItem(m_docData, diExist.getName(),  diExist.getValue());
					break;
				case DocumentItem.ITEM_TYPE_JSON:
					DocumentItem diJson = new DocumentItem(m_docData, diExist.getName(),  diExist.getStringValue());
					diJson.setType(DocumentItem.ITEM_TYPE_JSON);
					break;
				case DocumentItem.ITEM_TYPE_STRING:
				default:
					new DocumentItem(m_docData, diExist.getName(), diExist.getStringValue());
				};
			}//if fieldname startswith @
		}//while

	}


	/**
	 * Executes an arbitrary query
	 * @param sSQLQuery
	 * @return true if the query was without error
	 */
	public boolean executeQuery(String sSQLQuery)
	{
		Connection cx=null;
		boolean bReturn = true;
		Statement stmt = null;

		try
		{
			cx = getConnection();
			stmt = cx.createStatement();   //not read only, may be used for updates    
			stmt.execute(sSQLQuery);      

		}
		catch(Exception e)
		{
			m_sLastError = e.toString();
			m_sysCtx.doError("TableManager.PopulateError", new String[]{e.toString()}, this);
			puakma.util.Util.logStackTrace(e, m_sysCtx, this, TRACE_DEPTH);
			bReturn = false;
		}
		finally{
			Util.closeJDBC(stmt);
			releaseConnection(cx);
		}
		return bReturn;
	}

	/**
	 * Gets some data from the rdbms and returns it capable for the choices="" parameter of
	 * a list item. Assume all columns except the last are the description and the last col is the actual value/alias.
	 * ie "SELECT PageName,' ',PageType,PageID from PAGEDATA WHERE PageType='M'"
	 * @param sSQLQuery
	 * @return the choice list, ie: "Some text|choice1,more text|choice2,etc|etc"
	 */
	public String makeChoices(String sSQLQuery)
	{
		Connection cx=null;
		StringBuilder sbReturn = new StringBuilder(1024);
		String sCol_1, sCol_Alias;
		StringBuilder sb = new StringBuilder(150);
		Statement stmt = null;
		ResultSet rs = null;

		try
		{
			cx = getConnection();
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);      
			rs = stmt.executeQuery(sSQLQuery);
			ResultSetMetaData rsmd = rs.getMetaData();
			int iColumnCount = rsmd.getColumnCount();
			while(rs.next()) 
			{        
				if(iColumnCount>1) 
				{            
					sb.delete(0, sb.length());
					sCol_Alias = rs.getString(iColumnCount);
					for(int i=1; i<iColumnCount; i++)
					{
						sb.append(rs.getString(i));
					}
					sCol_1 = sb.toString();
				}
				else
				{
					sCol_1 = rs.getString(1);
					sCol_Alias = sCol_1;
				}

				if(sbReturn.length()==0)
				{
					sbReturn.append(sCol_1);
					sbReturn.append('|');
					sbReturn.append(sCol_Alias);          
				}
				else
				{
					sbReturn.append(',');
					sbReturn.append(sCol_1);
					sbReturn.append('|');
					sbReturn.append(sCol_Alias);          
				}
			}      
		}
		catch(Exception e)
		{
			m_sLastError = e.toString();
			m_sysCtx.doError("TableManager.PopulateError", new String[]{e.toString()}, this);
			puakma.util.Util.logStackTrace(e, m_sysCtx, this, TRACE_DEPTH);
			sbReturn.delete(0, sbReturn.length());
		}
		finally{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			releaseConnection(cx);
		}
		return sbReturn.toString();
	}


	/**
	 * Useful for building a combobox array with the first element set to an
	 * arbitrary value
	 */
	public String[] makeChoicesArray(String sSQLQuery, String sFirstValue)
	{
		String sArray[] = makeChoicesArray(sSQLQuery);
		String sReturn[];
		if(sArray==null)
		{
			sReturn = new String[1];
			sReturn[0] = sFirstValue;
			return sReturn;
		}
		sReturn = new String[sArray.length+1];
		sReturn[0] = sFirstValue;
		for(int i=0; i<sArray.length; i++)
		{
			//System.out.println(sArray[i]);
			sReturn[i+1] = sArray[i];
		}
		return sReturn;
	}

	/**
	 * Gets some data from the rdbms and returns it capable for the choices="" parameter of
	 * a list item. Assume all columns except the last are the description and the last col is the actual value/alias.
	 * ie "SELECT PageName,' ',PageType,PageID from PAGEDATA WHERE PageType='M'"
	 * @param sSQLQuery
	 * @return the choice list, ie: "Some text|choice1", "more text|choice2", "etc|etc"
	 */
	public String[] makeChoicesArray(String sSQLQuery)
	{
		Connection cx=null;    
		String sCol_1, sCol_Alias;
		ArrayList ar=null;
		StringBuilder sb = new StringBuilder(50);
		ResultSet rs = null;
		Statement stmt = null;

		try
		{
			cx = getConnection();
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);      
			rs = stmt.executeQuery(sSQLQuery);
			ResultSetMetaData rsmd = rs.getMetaData();
			int iColumnCount = rsmd.getColumnCount();
			while(rs.next()) 
			{
				if(ar==null) ar = new ArrayList();

				if(iColumnCount>1)
				{
					sb.delete(0, sb.length());
					sCol_Alias = rs.getString(iColumnCount);
					if(sCol_Alias==null) sCol_Alias = "";
					for(int i=1; i<iColumnCount; i++)
					{
						String sValue = rs.getString(i);
						if(sValue==null) sValue = "";
						sb.append(sValue);
					}
					sCol_1 = sb.toString();
				}
				else
				{
					sCol_1 = rs.getString(1);
					if(sCol_1==null) sCol_1 = "";
					sCol_Alias = sCol_1;
				}        
				ar.add(sCol_1 + '|' + sCol_Alias);
			}            
		}
		catch(Exception e)
		{
			m_sLastError = e.toString();
			m_sysCtx.doError("TableManager.PopulateError", new String[]{e.toString()}, this);
			Util.logStackTrace(e, m_sysCtx, this, TRACE_DEPTH);      
		}
		finally{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			releaseConnection(cx);
		}
		if(ar==null) return null;

		/*Object obj[] = ar.toArray();
    String sReturn[] = new String[obj.length];
    for(int i=0; i<obj.length; i++) sReturn[i] = (String)obj[i];
		 **/
		return puakma.util.Util.objectArrayToStringArray(ar.toArray());
	}


	/**
	 * When passed a query ie "select * from atable where atableid=4" will copy all
	 * the resulting values into items on the INTERNAL document
	 * @param sSQLQuery
	 * @return Returns true of the query did not cause an exception
	 */
	public boolean populateDocument(String sSQLQuery)
	{
		return populateDocument(sSQLQuery, m_docData);
	}


	/**
	 * When passed a query ie "select * from atable where atableid=4" will copy all
	 * the resulting values into items on the document
	 * @param sSQLQuery
	 * @param docHTML
	 * @return Returns true if the query was executed successfully
	 */
	public boolean populateDocument(String sSQLQuery, HTMLDocument docHTML)
	{
		Connection cx=null;
		boolean bReturn = true;
		ResultSet rs=null;
		Statement stmt=null;

		try
		{
			cx = getConnection();
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setFetchSize(1); //Integer.MIN_VALUE for mysql??
			//System.out.println(szSQLQuery);
			rs = stmt.executeQuery(sSQLQuery);
			if(rs.next()) //only dealing with ONE row (cause it has to go in a document)
			{
				populateDocumentFromResultSet(rs, docHTML);
			}      
		}
		catch(Exception e)
		{
			m_sLastError = e.toString();
			m_sysCtx.doError("TableManager.PopulateError", new String[]{e.toString()}, this);
			puakma.util.Util.logStackTrace(e, m_sysCtx, this, TRACE_DEPTH);
			bReturn = false;
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			releaseConnection(cx);
		}
		return bReturn;
	}



	public void populateDocumentFromResultSet(ResultSet rs, HTMLDocument docHTML) throws Exception
	{
		populateDocumentFromResultSet(rs, (puakma.system.Document)docHTML);
	}


	/**
	 * Fills the internal document with the results in a resultset. Can be used to 
	 * copy data between tables/databases.
	 */
	public void populateDocumentFromResultSet(ResultSet rs) throws Exception
	{
		populateDocumentFromResultSet(rs, (puakma.system.Document)m_docData);
	}


	/**
	 * Sets all the values in the document based on the current row in the resultset.
	 * Remember to call rs.next() before calling this method!
	 * @param rs
	 */
	public void populateDocumentFromResultSet(ResultSet rs, puakma.system.Document docHTML) throws Exception
	{
		int i;
		String sColumnName="";
		ResultSetMetaData rsmd = rs.getMetaData();
		for(i=1; i<=rsmd.getColumnCount(); i++)
		{
			//sColumnName = rsmd.getColumnName(i);
			sColumnName = rsmd.getColumnLabel(i);

			switch(rsmd.getColumnType(i))
			{
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.LONGVARCHAR:
			case Types.LONGNVARCHAR:
			case Types.NCHAR:
			case Types.NVARCHAR:
				//FIXME https://dev.mysql.com/doc/connector-j/5.1/en/connector-j-reference-type-conversions.html
				docHTML.replaceItem(sColumnName, rs.getString(i));
				break;
			case Types.BIT:
			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:
			case Types.BIGINT:
				docHTML.replaceItem(sColumnName, rs.getLong(i));
				break;
			case Types.FLOAT:
			case Types.REAL:
			case Types.DOUBLE:
			case Types.NUMERIC:
			case Types.DECIMAL:
				docHTML.replaceItem(sColumnName, rs.getDouble(i));
				break;
			case Types.DATE:
			case Types.TIME:
			case Types.TIMESTAMP:				
				Timestamp ts = rs.getTimestamp(i);
				Date dtNull=null;
				if(ts==null) docHTML.replaceItem(sColumnName, dtNull);
				else docHTML.replaceItem(sColumnName, new Date( ts.getTime()) );
				break;
			default:
				//docHTML.replaceItem(sColumnName, rs.getBytes(i));
				docHTML.replaceItem(sColumnName, Util.getBlobBytes(rs, i));
			};//switch
			//getLong etc will always return a valid value (0) even when null.
			if(rs.wasNull()) docHTML.setItemNull(sColumnName);
		}//for 
	}

	/**
	 * When passed an SQL query, returns the output as an XML stringbuffer.
	 * Specify the name of an xsl resource in this database which will be added
	 * as a URL to the xml output
	 * the data only.
	 * @param sSQLQuery
	 * @param sStyleSheetURL
	 * @return An XML StringBuilder describing the first row returned by the query
	 */
	public StringBuilder getXML(String sSQLQuery, String sStyleSheetURL)
	{
		return getXML(sSQLQuery, true, sStyleSheetURL, false);
	}

	/**
	 * When passed an SQL query, returns the output as an XML stringbuffer.
	 * Optionally specify true to put the full xml tags around the data,
	 * or false to return the data only.
	 * @param sSQLQuery
	 * @param bFullXML
	 * @return An XML StringBuilder describing the first row returned by the query
	 */
	public StringBuilder getXML(String sSQLQuery, boolean bFullXML, boolean bIncludeBlobs)
	{
		return getXML(sSQLQuery, bFullXML, null, bIncludeBlobs);
	}

	/**
	 * Display a full xml document
	 * @param sSQLQuery
	 * @return An XML StringBuilder describing the first row returned by the query
	 */
	public StringBuilder getXML(String sSQLQuery)
	{
		return getXML(sSQLQuery, true, null, false);
	}

	/**
	 * When passed an SQL query, returns the output as an XML stringbuffer.
	 * Specify true to put the full xml tags around the data, or false to return
	 * the data only.
	 * @param sSQLQuery
	 * @param bFullXML
	 * @return An XML StringBuilder describing the first row returned by the query
	 */
	public StringBuilder getXML(String sSQLQuery, boolean bFullXML, String sStyleSheetURL, boolean bIncludeBlobs)
	{
		Connection cx=null;
		StringBuilder sbReturn = new StringBuilder(2048);
		ResultSet rs = null;
		Statement stmt = null;

		if(bFullXML)
		{
			sbReturn.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"); 
			if(sStyleSheetURL!=null && sStyleSheetURL.length()>0) sbReturn.append("<?xml-stylesheet type=\"text/xsl\" href=\"" + sStyleSheetURL + "\" ?>");
			sbReturn.append(getRootDataNode(m_sDefaultProtocol, m_sDefaultHost, m_sessCtx.getRequestPath().getPathToApplication()));//"<data path=\"" + m_SessCtx.getRequestPath().getPathToApplication() + "\">\r\n\r\n");
		}

		try
		{
			cx = getConnection();
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);      
			rs = stmt.executeQuery(sSQLQuery);
			sbReturn.append( getXML(rs, false, bIncludeBlobs) );      
		}
		catch(Exception e)
		{
			m_sLastError = e.toString();
			m_sysCtx.doError("TableManager.getXML", new String[]{e.toString()}, this);
			puakma.util.Util.logStackTrace(e, m_sysCtx, this, TRACE_DEPTH);
		}
		finally{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			releaseConnection(cx);
		}

		if(bFullXML) sbReturn.append("</data>\r\n");

		return sbReturn;
	}


	/**
	 * Gets in XML format all the rows in a resultset
	 * @param rs
	 * @param bFullXML
	 * @param bIncludeBlobs
	 * @return An XML StringBuilder describing the first row returned by the query
	 * @throws Exception
	 */
	public StringBuilder getXML(ResultSet rs, boolean bFullXML, boolean bIncludeBlobs) throws Exception
	{
		return getXML(rs, -1, bFullXML, bIncludeBlobs);
	}

	public StringBuilder getXML(ResultSet rs) throws Exception
	{
		return getXML(rs, false, false);
	}

	/**
	 * Get the standard xml preamble. Includes three attributes path="" group="" and application=""
	 * so that the path to the current application may be constructed in the xsl
	 * @param sAppPath
	 * @return A StringBuilder of the standard XML header preamble
	 */
	public static StringBuilder getHeaderXML(String sAppPath)
	{		
		return getHeaderXML("", "", sAppPath);
	}

	/**
	 * New method for adding the hostname and protocol to the XML <data> envelope. This is useful 
	 * for xsl transforms that need to link to another server
	 * @param sProtocol
	 * @param sHost
	 * @param sAppPath
	 * @return
	 */
	public static StringBuilder getHeaderXML(String sProtocol, String sHost, String sAppPath)
	{		
		StringBuilder sb = new StringBuilder(150);
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		sb.append(getRootDataNode(sProtocol, sHost, sAppPath));		
		return sb;
	}

	/**
	 * 
	 * @param sAppPath
	 * @return
	 */
	public static StringBuilder getRootDataNode(String sProtocol, String sHost, String sAppPath)
	{
		RequestPath rp = new RequestPath(sAppPath);
		StringBuilder sb = new StringBuilder(150);		
		sb.append("<data ");
		sb.append("path=\"");
		sb.append(rp.getPathToApplication()); 
		sb.append("\" ");
		sb.append("group=\"");
		sb.append(rp.Group);
		sb.append("\" ");
		sb.append("application=\"");
		sb.append(rp.Application + rp.FileExt);
		sb.append("\" ");
		sb.append("host=\""); //eg "dev.yourserver.com"
		if(sHost!=null) sb.append(sHost);
		sb.append("\" ");
		sb.append("protocol=\""); //eg "http://"
		if(sProtocol!=null) sb.append(sProtocol);
		sb.append("\"");
		sb.append(">\r\n\r\n");
		return sb;
	}

	/**
	 * Finishes off the XML data package
	 * @param sbXML
	 */
	public static void addTrailerXML(StringBuilder sbXML)
	{
		sbXML.append("</data>\r\n");
	}

	/**
	 * Render the resultset into xml format. Pass true as the last parameter to add <?xml?> etc to make
	 * a full xml document
	 * <pre>
	 * <row>
	 * 	<item name="TemplateName" type="String">
	 * 		<value isNull="false" dt:dt="bin.base64"></value>
	 * 	</item>
	 * </row>
	</pre>
	 * @param rs
	 * @param iNumRecords
	 * @param bFullXML
	 * @return A StringBuilder of xml describing the row from the database 
	 * @throws Exception
	 */
	public StringBuilder getXML(ResultSet rs, int iNumRecords, boolean bFullXML, boolean bIncludeBlobs) throws Exception
	{
		StringBuilder sbReturn = null;
		int i=0;

		if(bFullXML) 
			sbReturn = getHeaderXML(m_sDefaultProtocol, m_sDefaultHost, m_sessCtx.getRequestPath().getPathToApplication());
		else
			sbReturn = new StringBuilder();

		while((i<iNumRecords||iNumRecords<0) && rs.next())
		{
			sbReturn.append("<row>\r\n");
			sbReturn.append( getColumnXML(rs, bIncludeBlobs, m_sessCtx.getSessionContext()) );
			sbReturn.append("</row>\r\n\r\n");
			i++;
		}
		if(bFullXML) addTrailerXML(sbReturn);

		return sbReturn;
	}


	/**
	 * Parse the xml data passed and extract the column names.
	 * Opens the document as navigates to the first <row> and 
	 * enumerates all the columns in that row. Assumes all <row>s
	 * have the same columns.
	 */
	public static String[] getXMLColumnNames(StringBuilder sbXML)
	{
		ArrayList arr = new ArrayList();
		try
		{
			ByteArrayInputStream is = new ByteArrayInputStream(sbXML.toString().getBytes());
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = dbf.newDocumentBuilder();                  
			org.w3c.dom.Document xmlDocument = parser.parse(is);

			Node rootnode = xmlDocument.getFirstChild(); //<data>         
			if(rootnode==null) return null;

			NodeList nlRows = rootnode.getChildNodes(); //<row>
			for(int j=0; j<nlRows.getLength(); j++)
			{
				Node nodeFirstRow = nlRows.item(j);        
				if(nodeFirstRow.getNodeType()==Node.ELEMENT_NODE)
				{
					//System.out.println("row>> "+nodeFirstRow.getNodeName());
					NodeList nlColumns = nodeFirstRow.getChildNodes();
					for(int i=0; i<nlColumns.getLength(); i++)
					{
						Node node = nlColumns.item(i);
						if(node.getNodeType()==Node.ELEMENT_NODE)
						{
							//System.out.println("column>> "+node.getNodeName());
							NamedNodeMap atts = node.getAttributes();                  
							Node nName = atts.getNamedItem("name");
							if(nName!=null) arr.add(nName.getNodeValue());
						}
					}
					break;
				}              
			}//for
		}
		catch(Exception e) {}
		return puakma.util.Util.objectArrayToStringArray(arr.toArray());
	}

	/**
	 * returns the last error message
	 */
	public String getLastError()
	{
		return m_sLastError;
	}

	/**
	 * gets the last id created by an auto incrementing field.
	 * @return -1 if not found or insert has never been called
	 */
	public long getLastInsertID()
	{
		return m_lLastInsertID;
	}

	/**
	 * 
	 */
	public static StringBuilder getColumnXML(ResultSet rs) throws Exception
	{
		return getColumnXML(rs, true, null);
	}

	/**
	 * @deprecated
	 * @param rs
	 * @param bIncludeBlobs
	 * @return
	 * @throws Exception
	 */
	public static StringBuilder getColumnXML(ResultSet rs, boolean bIncludeBlobs) throws Exception
	{
		return getColumnXML(rs, bIncludeBlobs, null);
	}


	/**
	 * Gets the JSON representation of a resultset
	 * @param rs
	 * @param bIncludeBlobs
	 * @param sess
	 * @return
	 * @throws Exception
	 */
	public static JSONObject getColumnJSON(ResultSet rs, boolean bIncludeBlobs, SessionContext sess) throws Exception
	{
		JSONObject json = new JSONObject();

		puakma.coder.CoderB64 b64 = new puakma.coder.CoderB64();
		String sColumnName;
		String sVal;

		TimeZone tz = null;
		Locale loc = null;
		if(sess!=null)
		{
			tz = sess.getTimeZone();
			loc = sess.getLocale();
		}

		ResultSetMetaData rsmd = rs.getMetaData();
		for(int i=1; i<=rsmd.getColumnCount(); i++)
		{
			//sColumnName = rsmd.getColumnName(i);
			sColumnName = rsmd.getColumnLabel(i);
			String sColumnNameLow = sColumnName.toLowerCase();
			//BJU force lowercase so that we remain db independent
			//mysql obeys case, but postgresql forces lowercase this may break object referencing 

			//FIXME null values get converted to "". Is this correct??

			switch(rsmd.getColumnType(i))
			{
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.LONGVARCHAR:
				sVal = rs.getString(i);
				if(sVal==null) sVal = "";
				json.put(sColumnNameLow, sVal);
				break;			
			case Types.BIT:
			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:
			case Types.BIGINT:				
				json.put(sColumnNameLow, rs.getLong(i));
				break;
			case Types.FLOAT:
			case Types.REAL:
			case Types.DOUBLE:
			case Types.NUMERIC:
			case Types.DECIMAL:				
				json.put(sColumnNameLow, rs.getDouble(i));
				break;
			case Types.DATE:
			case Types.TIME:
			case Types.TIMESTAMP:
				Timestamp ts = rs.getTimestamp(i);          
				if(ts==null) 
					json.put(sColumnNameLow, "");
				else
				{
					java.util.Date dtRecord = new Date(ts.getTime());
					/*JSONObject jsonDate = new JSONObject();
x
					jsonDate.put("year", Util.formatDate(dtRecord, "yyyy", loc, tz));
					jsonDate.put("month", Util.formatDate(dtRecord, "MM", loc, tz));
					jsonDate.put("day", Util.formatDate(dtRecord, "dd", loc, tz));
					jsonDate.put("hour", Util.formatDate(dtRecord, "HH", loc, tz));
					jsonDate.put("minute", Util.formatDate(dtRecord, "mm", loc, tz));
					jsonDate.put("second", Util.formatDate(dtRecord, "ss", loc, tz));
					jsonDate.put("zone", Util.formatDate(dtRecord, "Z", loc, tz));

					jsonDate.put("monthnameshort", Util.formatDate(dtRecord, "MMM", loc, tz));
					jsonDate.put("monthnamelong", Util.formatDate(dtRecord, "MMMM", loc, tz));
					jsonDate.put("daynameshort", Util.formatDate(dtRecord, "EEE", loc, tz));
					jsonDate.put("daynamelong", Util.formatDate(dtRecord, "EEEE", loc, tz));

					json.put(sColumnNameLow + '-' +Util.SHORT_DATE, Util.formatDate(dtRecord, Util.SHORT_DATE, loc, tz));
					json.put(sColumnNameLow + '-' +Util.LONG_DATE, Util.formatDate(dtRecord, Util.LONG_DATE, loc, tz));
					json.put(sColumnNameLow + '-' +Util.SHORT_DATE_TIME, Util.formatDate(dtRecord, Util.SHORT_DATE_TIME, loc, tz));
					json.put(sColumnNameLow + '-' +Util.LONG_DATE_TIME, Util.formatDate(dtRecord, Util.LONG_DATE_TIME, loc, tz));

					json.put(sColumnNameLow, jsonDate);	*/
					//ISO format
					String sISO = Util.formatDate(dtRecord, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", loc, tz);
					json.put(sColumnNameLow, sISO==null?"":sISO);
				}
				break;
			case Types.BLOB: 
			case Types.LONGVARBINARY:
			case Types.CLOB:
			case Types.BINARY:
			case Types.VARBINARY:
				if(bIncludeBlobs)
				{
					byte buf[] = Util.getBlobBytes(rs, i);
					if(buf==null)
						json.put(sColumnNameLow, "");
					else
					{
						json.put(sColumnNameLow, b64.encode(buf));
					}					
				}
				break;      
			default:
				sVal = rs.getString(i);
				if(sVal==null) 
					json.put(sColumnNameLow, "");
				else
					json.put(sColumnNameLow, sVal);			
			};//switch

		}//for

		return json;
	}

	/**
	 * 
	 * @param bIncludeBlobs
	 * @return
	 * @throws JSONException 
	 * @throws Exception
	 */
	public JSONObject getJSON(boolean bIncludeBlobs) throws JSONException
	{
		JSONObject json = new JSONObject();

		puakma.coder.CoderB64 b64 = new puakma.coder.CoderB64();		


		TimeZone tz = null;
		Locale loc = null;
		if(m_sessCtx!=null)
		{
			tz = m_sessCtx.getTimeZone();
			loc = m_sessCtx.getLocale();
		}

		Enumeration en = m_docData.getAllItems();
		while(en.hasMoreElements())
		{
			DocumentItem di = (DocumentItem)en.nextElement();
			String sColumnName = di.getName();
			String sColumnNameLow = sColumnName.toLowerCase();
			//BJU force lowercase so that we remain db independent
			//mysql obeys case, but postgresql forces lowercase this may break object referencing 

			if(di.isNull())					
			{
				json.put(sColumnNameLow, JSONObject.NULL);
				continue;
			}
			switch(di.getType())
			{
			case DocumentItem.ITEM_TYPE_DATE:
				java.util.Date dtRecord = di.getDateValue();
				/*
				JSONObject jsonDate = new JSONObject();

				jsonDate.put("year", Util.formatDate(dtRecord, "yyyy", loc, tz));
				jsonDate.put("month", Util.formatDate(dtRecord, "MM", loc, tz));
				jsonDate.put("day", Util.formatDate(dtRecord, "dd", loc, tz));
				jsonDate.put("hour", Util.formatDate(dtRecord, "HH", loc, tz));
				jsonDate.put("minute", Util.formatDate(dtRecord, "mm", loc, tz));
				jsonDate.put("second", Util.formatDate(dtRecord, "ss", loc, tz));
				jsonDate.put("zone", Util.formatDate(dtRecord, "Z", loc, tz));
				jsonDate.put("iso", Util.formatDate(dtRecord, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", loc, tz));

				jsonDate.put("monthnameshort", Util.formatDate(dtRecord, "MMM", loc, tz));
				jsonDate.put("monthnamelong", Util.formatDate(dtRecord, "MMMM", loc, tz));
				jsonDate.put("daynameshort", Util.formatDate(dtRecord, "EEE", loc, tz));
				jsonDate.put("daynamelong", Util.formatDate(dtRecord, "EEEE", loc, tz));

				json.put(sColumnNameLow + '-' +Util.SHORT_DATE, Util.formatDate(dtRecord, Util.SHORT_DATE, loc, tz));
				json.put(sColumnNameLow + '-' +Util.LONG_DATE, Util.formatDate(dtRecord, Util.LONG_DATE, loc, tz));
				json.put(sColumnNameLow + '-' +Util.SHORT_DATE_TIME, Util.formatDate(dtRecord, Util.SHORT_DATE_TIME, loc, tz));
				json.put(sColumnNameLow + '-' +Util.LONG_DATE_TIME, Util.formatDate(dtRecord, Util.LONG_DATE_TIME, loc, tz));

				json.put(sColumnNameLow, jsonDate);
				 */
				//ISO format
				String sISO = Util.formatDate(dtRecord, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", loc, tz);
				json.put(sColumnNameLow, sISO==null?"":sISO);
				break;
			case DocumentItem.ITEM_TYPE_NUMERIC:
				json.put(sColumnNameLow, di.getNumericValue());
				break;
			case DocumentItem.ITEM_TYPE_INTEGER:
				json.put(sColumnNameLow, di.getIntegerValue());
				break;
			case DocumentItem.ITEM_TYPE_FILE:
			case DocumentItem.ITEM_TYPE_BUFFER:
				if(bIncludeBlobs)
					json.put(sColumnNameLow, b64.encode(di.getValue()));				
				break;
			default:
				json.put(sColumnNameLow, di.getStringValue());
			};
		}//while


		return json;
	}

	/**
	 * Return an XML representation of the internal document
	 * @return
	 */
	public StringBuilder getXML()
	{
		if(m_docData==null) return new StringBuilder();

		return m_docData.toXML();
	}

	/**
	 * Get the xml representation of a column in the resultset
	 * @param rs the resultset to extract from
	 * @param bIncludeBlobs set to true to include blob data in the output. This 
	 * was added since most output is for views and blobs 
	 * @param sess Used for localisation and timezones
	 * @return A StringBuilder of XML describing the database row
	 */
	public static StringBuilder getColumnXML(ResultSet rs, boolean bIncludeBlobs, SessionContext sess) throws Exception
	{
		StringBuilder sbReturn = new StringBuilder(256);
		String sColumnName;
		String sVal;

		TimeZone tz = null;
		Locale loc = null;
		if(sess!=null)
		{
			tz = sess.getTimeZone();
			loc = sess.getLocale();
		}

		ResultSetMetaData rsmd = rs.getMetaData();
		for(int i=1; i<=rsmd.getColumnCount(); i++)
		{
			//sColumnName = rsmd.getColumnName(i);
			sColumnName = rsmd.getColumnLabel(i);
			//BJU force lowercase so that we remain db independent
			//mysql obeys case, but postgresql forces lowercase this breaks xsl when 
			//we render
			sbReturn.append("\t<item name=\"");
			sbReturn.append(sColumnName.toLowerCase());
			sbReturn.append("\">\r\n");

			switch(rsmd.getColumnType(i))
			{
			case Types.CHAR:
			case Types.VARCHAR:
				//case Types.LONGVARCHAR:
				sVal = rs.getString(i);
				if(sVal==null) sbReturn.append("\t\t<value datatype=\"string\" isnull=\"true\"></value>\r\n");
				//BJU added CDATA, in case people put in & etc in strings
				else 
				{
					sbReturn.append("\t\t<value datatype=\"string\" isnull=\"false\"><![CDATA[");
					sbReturn.append(cleanXMLString(sVal));
					sbReturn.append("]]></value>\r\n");
				}
				break;
			case Types.LONGVARCHAR: //encase the slab in CDATA tags
				sVal = rs.getString(i);
				if(sVal==null) sbReturn.append("\t\t<value datatype=\"text\" isnull=\"true\"></value>\r\n");
				else
				{
					sbReturn.append("\t\t<value datatype=\"text\" isnull=\"false\"><![CDATA[");
					sbReturn.append(cleanXMLString(sVal));
					sbReturn.append("]]></value>\r\n");
				}
				break;
			case Types.BIT:
			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:
			case Types.BIGINT:
				sVal = rs.getString(i);
				if(sVal==null) sbReturn.append("\t\t<value datatype=\"integer\" isnull=\"true\"></value>\r\n");
				else
				{
					sbReturn.append("\t\t<value datatype=\"integer\" isnull=\"false\">");
					sbReturn.append(sVal);
					sbReturn.append("</value>\r\n");
				}
				break;
			case Types.FLOAT:
			case Types.REAL:
			case Types.DOUBLE:
			case Types.NUMERIC:
			case Types.DECIMAL:
				sVal = rs.getString(i);
				if(sVal==null) sbReturn.append("\t\t<value datatype=\"float\" isnull=\"true\"></value>\r\n");
				else 
				{
					sbReturn.append("\t\t<value datatype=\"float\" isnull=\"false\">");
					sbReturn.append(sVal);
					sbReturn.append("</value>\r\n");
				}
				break;
			case Types.DATE:
			case Types.TIME:
			case Types.TIMESTAMP:
				Timestamp ts = rs.getTimestamp(i);          
				if(ts==null) sbReturn.append("\t\t<value datatype=\"time\" isnull=\"true\"></value>\r\n");
				else
				{
					java.util.Date dtRecord = new java.util.Date(ts.getTime());
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
			case Types.BLOB:
			case Types.LONGVARBINARY:
			case Types.CLOB:
			case Types.BINARY:
			case Types.VARBINARY:
				if(bIncludeBlobs)
				{
					//byte buf[] = rs.getBytes(i);
					byte buf[] = Util.getBlobBytes(rs, i);
					if(buf==null)
						sbReturn.append("\t\t<value datatype=\"binary\" isnull=\"true\"></value>\r\n");
					else
					{
						puakma.coder.CoderB64 b64 = new puakma.coder.CoderB64();
						sbReturn.append("\t\t<value datatype=\"binary\" encoding=\"base64\" isnull=\"false\">");
						sbReturn.append(b64.encode(buf));
						sbReturn.append("</value>\r\n");
					}
				}
				break;        
			default:
				sVal = rs.getString(i);
				if(sVal==null) sbReturn.append("\t\t<value datatype=\"unknown\" isnull=\"true\"></value>\r\n");
				else
				{
					sbReturn.append("\t\t<value datatype=\"unknown\" isnull=\"false\"><![CDATA[");
					sbReturn.append(cleanXMLString(sVal));
					sbReturn.append("]]></value>\r\n");
				}
			};//switch
			sbReturn.append("\t</item>\r\n");
		}//for

		return sbReturn;
	}

	/**
	 * Remove illegal xml characters 0x00 - 0x1f (0x20 is a space). Illegal chars will
	 * be replaced with a space ' '.
	 */
	public static String cleanXMLString(String sIn)
	{
		if(sIn==null || sIn.length()==0) return sIn;
		char cArray[] = sIn.toCharArray();
		for(int i=0; i<cArray.length; i++)
		{
			char c = cArray[i];
			if(c<0x20)
			{
				if(!(c=='\r'||c=='\n'||c=='\t')) cArray[i] = ' ';
			}
		}
		return new String(cArray);
	}

	/**
	 * Set the protocol value to be used in the xsl transforms
	 * @param sProto
	 */
	public synchronized void setDefaultProtocol(String sProto)
	{
		m_sDefaultProtocol = sProto;
	}

	/**
	 * Set the host (server) value to be used in the xsl transform
	 */
	public synchronized void setDefaultHost(String sHost)
	{
		m_sDefaultHost = sHost;
	}

	public String getDefaultProtocol()
	{
		return m_sDefaultProtocol;
	}

	public String getDefaultHost()
	{
		return m_sDefaultHost;
	}



	/**
	 * For debug purposes...
	 * @return A String representation of the internal Document
	 */
	public String toString()
	{
		return m_docData.toString();
	}

	public String getErrorSource()
	{		
		return "TableManager:"+m_sessCtx.getErrorSource();
	}

	public String getErrorUser()
	{
		return m_sessCtx.getUserName();
	}

}