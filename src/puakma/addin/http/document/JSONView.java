/* ***************************************************************
JSONView.java
Copyright (C) 2010  Brendon Upson 
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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

import org.json.JSONObject;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.system.SystemContext;
import puakma.util.Util;

/**
 * @created 29 Oct 2010
 * The purpose of this class is to provide an easy means of rendering to JSON a recordset with paging
 * It relies on the JSON objects from json.org, included in puakma.jar
 * Any ORDER BY clause added to the SQL statement should be at the end.
 * @author bupson
 *
 */
public class JSONView 
{
	public static long DEFAULT_ROWS = 25;

	private HTTPSessionContext m_pSession;
	private long m_lRowsPerView = DEFAULT_ROWS;	
	private TableManager m_tm;

	private String m_sSQL;
	private String m_sConnName;
	private int m_lCurrentViewRowCount;
	private boolean m_bIncludeBlobs = false;
	private long m_lTotalRowsInQuery=0;
	private JSONViewCallback m_callback = null;
	private static final String SQL_ORDER_BY = " ORDER BY ";


	public JSONView(HTTPSessionContext sess, String sSelect, String sConnectionName)
	{
		m_pSession = sess;
		m_sConnName = sConnectionName;
		m_sSQL = sSelect; 
		m_tm = new TableManager(m_pSession, m_sConnName, "");
	}

	/**
	 * If set to true, blod data will be base64 encoded and appear in the output
	 * @param bIncludeBlobs
	 */
	public synchronized void setIncludeBlobs(boolean bIncludeBlobs)
	{
		m_bIncludeBlobs = bIncludeBlobs;
	}

	public synchronized void setCallback(JSONViewCallback obj)
	{
		m_callback = obj;
	}

	/**
	 * 
	 * @param lRows
	 */
	public synchronized void setRowsPerView(long lRows)
	{
		m_lRowsPerView = lRows;
		if(m_lRowsPerView<1) m_lRowsPerView = DEFAULT_ROWS;
	}

	/**
	 * 
	 * @param sNewSQL
	 */
	public synchronized void setSQL(String sNewSQL)
	{
		m_sSQL = sNewSQL;
	}

	/**
	 * Call this AFTER getViewJSON();
	 * @return
	 */
	public long getTotalRowCount()
	{
		return m_lTotalRowsInQuery;
	}


	/**
	 * limit style 1 (default) = LIMIT 86,25
	 * limit style 2           = LIMIT 25 OFFSET 86
	 * returns null when limit not supported
	 * @return the full sql statement with the limit clause inserted
	 */
	private String getSQLWithLimitClause(String sSQL, long lStart, long lMaxRows)
	{		
		if(m_lRowsPerView<=0 || lMaxRows<1) return sSQL; //no limit on row count


		String sMax="";
		String sReturn = null;
		int iLimitStyle = 1;

		String sDriver = m_tm.getJDBCDriver();        
		if(sDriver!=null && sDriver.indexOf("postgres")>0) iLimitStyle = 2;
		if(sDriver!=null && sDriver.indexOf("hsqldb")>0) iLimitStyle = 3;
		if(sDriver!=null && sDriver.indexOf(".jtds.")>0) iLimitStyle = 4;
		if(sDriver!=null && sDriver.indexOf("oracle")>=0) iLimitStyle = 5;

		switch(iLimitStyle)
		{
		case 5://oracle select * from x where rownum<99 
			//http://www.experts-exchange.com/Programming/Programming_Languages/Delphi/Q_21619737.html
			sReturn = null; //TODO.... need to insert the rownum in the where if there is one
			//See /system/admin.pma/GetLog?OpenAction for syntax as needs a nested nested query
			break;
		case 4://no limiting, ms sql server http://www.planet-source-code.com/vb/scripts/ShowCode.asp?txtCodeId=850&lngWId=5
			sReturn = null; //use "SELECT TOP 99 ..."
			break;
		case 3://hsqldb
			if(lStart<=0) 
				sMax = " LIMIT " + lStart + " " + lMaxRows; //start is 0 based
			else
				sMax = " LIMIT " + lStart + " " + lMaxRows;
			//now we want select LIMIT 10 fields FROM TABLE ...
			if(sSQL.length()>6) 
				sReturn = "SELECT" + sMax + sSQL.substring(6);
			else
				sReturn = sSQL; //no limiting....
			break;
		case 2:   //postgres     
			if(lStart<=0) 
				sMax = " LIMIT " + lMaxRows;
			else
				sMax = " LIMIT " + lMaxRows + " OFFSET " + lStart; 
			sReturn = sSQL + sMax;
			break;
		case 1:   //mysql    
			if(lStart<=0) 
				sMax = " LIMIT " + lMaxRows;
			else
				sMax = " LIMIT " + lStart + "," + lMaxRows;
			sReturn = sSQL + sMax;
			break;
		default:
			sReturn = null; //default is manual limiting

		};

		//System.out.println(iLimitStyle + ">"+sReturn+"<");
		return sReturn;
	}

	/**
	 * For use with MyTableGrid, see http://pabloaravena.info/mytablegrid
	 * @return
	 */
	public String asMyTableGrid(long lPageNumber, long lFirstRow, long lRowsPerPage, String sSortField, boolean bAscendingSort)
	{
		StringBuilder sb = new StringBuilder(256);


		if(lPageNumber==0) lPageNumber = 1;


		JSONObject json[] = getViewJSON(lFirstRow, lRowsPerPage, sSortField, bAscendingSort);

		long lTotalRows = m_lTotalRowsInQuery;
		long lNumberOfPages = lTotalRows/lRowsPerPage; 

		if ((lTotalRows % lRowsPerPage) > 0) lNumberOfPages++;
		if (lPageNumber > lNumberOfPages) lPageNumber = lNumberOfPages;

		long lFrom = ((lPageNumber - 1) * lRowsPerPage);
		long lTo = (lPageNumber * lRowsPerPage) - 1;
		if (lTo > lTotalRows) lTo = lTotalRows;

		sb.append("{\r\n");
		sb.append("\"options\": {\r\n");
		sb.append("\t\"pager\": {\r\n");
		sb.append("\t\t\"currentPage\": "+lPageNumber+",\r\n");
		sb.append("\t\t\"total\": "+lTotalRows+",\r\n");
		sb.append("\t\t\"from\": "+lFrom+",\r\n");
		sb.append("\t\t\"to\": "+lTo+",\r\n");
		sb.append("\t\t\"pages\": " + lNumberOfPages + "\r\n");
		sb.append("\t}\r\n");
		sb.append("},\r\n");
		sb.append("\t\"rows\": ");
		sb.append(implode(json));
		sb.append("\r\n");
		sb.append("}\r\n");

		return sb.toString();
	}

	/**
	 * 
	 * @param json
	 * @return an empty or null recordset will return "[]"
	 */
	public static StringBuilder implode(JSONObject[] json) 
	{		
		StringBuilder sb = new StringBuilder(512);


		sb.append("[\r\n");
		if(json!=null)
		{
			for(int i=0; i<json.length; i++)
			{
				if(i>0) sb.append(",\r\n");
				sb.append(json[i]);
			}
		}
		sb.append("\r\n]\r\n");

		return sb;
	}

	/**
	 * 
	 * @param lFirstRow
	 * @param lNumberOfRowsToReturn
	 * @param sSortField
	 * @param bAscendingSort
	 * @return
	 */
	public JSONObject[] getViewJSON(long lFirstRow, long lNumberOfRowsToReturn, String sSortField, boolean bAscendingSort)
	{        
		m_lTotalRowsInQuery = getTotalRowsInQuery();


		String sSQL = null;

		if(sSortField!=null && sSortField.length()>0)
		{
			String sDirection = " DESC";
			if(bAscendingSort) sDirection = " ASC";
			sSQL = removeOrderBy(m_sSQL);
			sSQL += SQL_ORDER_BY + " " + sSortField + sDirection;
		}
		else //no change in sorting applied
		{
			sSQL = m_sSQL;
		}

		boolean bManualLimit = false;
		String sLimitedSQL = getSQLWithLimitClause(sSQL, lFirstRow, lNumberOfRowsToReturn<1?-1:(lNumberOfRowsToReturn+1));
		if(sLimitedSQL==null) 
			bManualLimit = true;		
		else
			sSQL = sLimitedSQL;


		Connection cx = null;
		m_lCurrentViewRowCount = 0;
		long lLoopCount=0;
		ArrayList arr = new ArrayList();
		boolean bSystemConnection = m_sConnName!=null && m_sConnName.equals(SystemContext.DBALIAS_SYSTEM); 

		Statement stmt = null;
		ResultSet rs = null;

		try 
		{
			if(bSystemConnection)
				cx = m_pSession.getSystemContext().getSystemConnection();
			else
				cx = m_pSession.getDataConnection(m_sConnName);
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(sSQL);
			while(rs.next())
			{                            
				lLoopCount++;
				if((bManualLimit && lLoopCount>=lFirstRow) || !bManualLimit)
				{
					m_lCurrentViewRowCount++;
					if(lNumberOfRowsToReturn>0 && m_lRowsPerView>0 && m_lCurrentViewRowCount>lNumberOfRowsToReturn) 
					{
						//m_bHasMoreResults = true;
						break;
					}
					else
					{	
						JSONObject json = TableManager.getColumnJSON(rs, m_bIncludeBlobs , m_pSession.getSessionContext());
						if(m_callback!=null) m_callback.jsonCallback(rs, json);
						arr.add(json);						
					}
				}
			}//while            
		}
		catch(Exception e)
		{
			m_pSession.getSystemContext().doError(e.toString(), m_pSession);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			if(bSystemConnection)
				m_pSession.getSystemContext().releaseSystemConnection(cx);
			else
				m_pSession.releaseDataConnection(cx);
		}

		JSONObject obj[] = new JSONObject[arr.size()];
		for(int i=0; i<arr.size(); i++)
		{
			JSONObject json = (JSONObject) arr.get(i);
			obj[i] = json;
		}

		return obj;
	}

	/**
	 * Get a count of the total number of rows returned by the query without any limiting applied
	 * @return
	 */
	public long getTotalRowsInQuery() 
	{
		String sSQLNoOrderBy = removeOrderBy(m_sSQL);

		int iPos = sSQLNoOrderBy.toUpperCase().indexOf(" FROM ");
		if(iPos<0) return -1; //bad query

		String sSQL = "SELECT COUNT(1) as tot" + sSQLNoOrderBy.substring(iPos);
		m_tm.populateDocument(sSQL);	
		return m_tm.getFieldInteger("tot");
	}

	/**
	 * 
	 * @param sSQL
	 * @return
	 */
	private static String removeOrderBy(String sSQL) 
	{
		if(sSQL==null) return null;

		int iPos = sSQL.toUpperCase().indexOf(JSONView.SQL_ORDER_BY);
		if(iPos>=0)
		{
			return sSQL.substring(0, iPos);
		}
		return sSQL; //no order by 
	}

}
