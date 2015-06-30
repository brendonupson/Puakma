/** ***************************************************************
pmaSerial.java
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

import java.io.*;
import java.util.*;
import java.sql.*;
import puakma.system.*;
import puakma.util.*;
import java.util.zip.*;

public class pmaSerial implements TableRowImportCallback
{

	private RequestPath m_rPathNew;
	private boolean m_bSomeImported=false;


	/**
	 * used to store the primary key id translations
	 */
	private class KeyTranslator
	{
		public String m_sTable;
		public String m_sAttribute;
		public long m_lOldValue;
		public long m_lNewValue;

		public KeyTranslator(String sTable, String sAttribute, long lOldValue, long lNewValue)
		{
			m_sTable = sTable;
			m_sAttribute = sAttribute;
			m_lOldValue = lOldValue;
			m_lNewValue = lNewValue;
		}

		public String getKey()
		{
			String szKey = m_sTable + "~" + m_sAttribute + "~" + m_lOldValue;
			return szKey.toUpperCase();
		}

		public String toString()
		{
			return getKey() + " new=[" + m_lNewValue + "]";
		}

	};

	private static String METHOD_IMPORT = "IMPORT";
	private static String METHOD_EXPORT = "EXPORT";
	private Hashtable vTranslators = new Hashtable();
	private Connection m_cx=null;
	private boolean m_bOK=false;
	private boolean m_bSourceExport=true;
	private InputStream m_in;

	//USAGE: pmaSerial EXPORT|IMPORT /puakma/config/puakma.config /datafile.pmx /group/app.pma
	public pmaSerial(Connection cx, String szMethod, String szDataFile, String szAppPath)
	{
		doWork(cx, szMethod, szDataFile, szAppPath);
	}

	public pmaSerial(Connection cx, String szMethod, String szDataFile, String szAppPath, boolean bSourceExport)
	{
		m_bSourceExport=bSourceExport;
		doWork(cx, szMethod, szDataFile, szAppPath);
	}

	/**
	 * default constructor for performing ad hoc data exports
	 */
	public pmaSerial()
	{

	}

	/**
	 * Constructor for stream based importing
	 * @param is
	 */
	public pmaSerial(InputStream is)
	{
		//m_import = new TableRowInputStream(is);
		m_in = is;    
	}


	/**
	 * Main entry point for most constructors. Where all the work occurs
	 * @param cx
	 * @param sMethod
	 * @param sDataFile
	 * @param sAppPath
	 */
	private void doWork(Connection cx, String sMethod, String sDataFile, String sAppPath)
	{
		RequestPath rPath=null;
		if(sAppPath!=null && sAppPath.length()!=0) rPath = new RequestPath(sAppPath);
		m_cx = cx;

		if(sMethod.equals(METHOD_EXPORT)) 
		{
			OutputStream out;
			try 
			{
				out = new FileOutputStream(sDataFile);
				doExport(out, rPath);
			} 
			catch (FileNotFoundException e) 
			{			
				e.printStackTrace();
			}

		}
		if(sMethod.equals(METHOD_IMPORT)) 
		{
			try 
			{
				m_in = new FileInputStream(sDataFile);
				doImport(rPath);
			} 
			catch (FileNotFoundException e) 
			{
				e.printStackTrace();
			}
		}

	}

	/**
	 * Add a translation key to the table, only if it isn't in there....
	 */
	private void addTranslator(String szTable, String szAttribute, long lOldValue, long lNewValue)
	{
		KeyTranslator keyTrans = new KeyTranslator(szTable, szAttribute, lOldValue, lNewValue);

		if(!vTranslators.containsKey(keyTrans.getKey()))
		{
			vTranslators.put(keyTrans.getKey(), keyTrans);			
		}

	}


	/**
	 * Get the new key value from the table
	 * @return -1 if the key is not found
	 */
	private long getTranslated(String szTable, String szAttribute, long lOldValue)
	{
		String szKey = szTable.toUpperCase() + "~" + szAttribute.toUpperCase() + "~" + lOldValue;
		//if(lOldValue>=0) System.out.println("Searching: "+szKey);
		KeyTranslator keyTrans = (KeyTranslator) vTranslators.get(szKey);
		if(keyTrans!=null)
		{
			//System.out.println(" Found:"+keyTrans.m_lNewValue);
			return keyTrans.m_lNewValue;
		}

		return -1;
	}




	/**
	 * for launching from the commandline
	 * param1 = EXPORT || IMPORT
	 * param2 = configfile (puakma.config)
	 * param3 = database to process (/group/app.pma)
	 * param4 = optional file to use (if blank will use /tempdir/group~app.pmx)
	 */
	public static void main(String args[])
	{

		String szSystemDriverClass, szSystemDBURL, SystemDBName, szSystemUser, szSystemPW;
		String szAppPath;
		String szConfigFile, szDataFile, szAction;
		Properties propConfig=new Properties(); //configuration parameters
		//int iErrorCount=0;
		Connection cx=null;


		if(args.length < 3)
		{
			System.out.println("Correct parameters not passed. Aborting...");
			System.out.println("USAGE: pmaSerial EXPORT|IMPORT /puakma/config/puakma.config /group/app.pma [/datafile.pmx]");
			return;
		}

		szAction = args[0].toUpperCase();
		szConfigFile = args[1];
		szAppPath = args[2];


		try
		{
			FileInputStream fs = new FileInputStream(szConfigFile);
			propConfig.load(fs);
		}
		catch(Exception e)
		{
			System.out.println("FATAL: Error loading system config from: " + szConfigFile);
			return;
		}
		System.out.println("Using CONFIG: " + szConfigFile);

		String szTempDir = propConfig.getProperty("SystemTempDir");
		if(args.length > 3)
			szDataFile = args[3];
		else //make up the file....
			szDataFile = "";
		szDataFile = setFile(szTempDir, szDataFile, szAppPath);
		szAppPath = szAppPath.replaceAll("~", "/");


		SystemDBName = propConfig.getProperty("SystemDBName");
		if(SystemDBName==null) SystemDBName = "";

		szSystemDBURL = propConfig.getProperty("SystemDBURL");
		szSystemDBURL += SystemDBName;
		szSystemUser = propConfig.getProperty("SystemUser");
		szSystemPW = propConfig.getProperty("SystemPW");
		szSystemDriverClass = propConfig.getProperty("SystemDriverClass");
		System.out.println("");
		System.out.println("RDBMS URL: " + szSystemDBURL);
		System.out.println("Driver class: " + szSystemDriverClass);
		System.out.println("Username: " + szSystemUser);
		System.out.println("Password: " + szSystemPW);
		System.out.println(szAction + " file: " + szDataFile);
		System.out.println("");
		if(szAppPath==null)
			System.out.println("- ALL DESIGN DATA WILL BE PROCESSED -");
		else
			System.out.println("- PROCESSING: " + szAppPath + " -");

		try
		{
			Class.forName(szSystemDriverClass).newInstance();
			cx = DriverManager.getConnection(szSystemDBURL, szSystemUser, szSystemPW);
			System.out.println("");
		}
		catch (Exception E)
		{
			System.err.println("FATAL: Unable to load JDBC driver. " + E.toString());
			E.printStackTrace();
			return;
		}

//		System.out.println(szAction + " will begin in 3 seconds...");
//		try{Thread.sleep(3000);}catch(Exception e){}
		System.out.println("***** STARTING " + szAction + " *****");

		pmaSerial ImporterExporter = new pmaSerial(cx, szAction, szDataFile, szAppPath);

		System.out.println("");
		if(ImporterExporter.isSuccess())
			System.out.println("**** " + szAction + " SUCCESSFUL ****");
		else
			System.out.println("!!!! " + szAction + " FAILED !!!!");

	}

	/**
	 * Sets up the correct path...
	 */
	private static String setFile(String szInPath, String szInFile, String szAppPath)
	{
		String szDataFile;
		String szTempDir="";

		if( !(szInPath==null || szInPath.length()==0) ) szTempDir = szInPath;
		if(szTempDir.length()!=0)
		{
			if(!szTempDir.endsWith("/")) szTempDir += "/";
		}

		if(szInFile==null || szInFile.length()==0)
		{
			int iPos;
			szDataFile = Util.trimChars(szAppPath, "/");
			iPos = szDataFile.indexOf(".pma");
			if(iPos>0) szDataFile = szDataFile.substring(0, iPos);
			szDataFile +=  ".pmx";
			szDataFile = szDataFile.replace('/', '~');
		}
		else
		{
			szDataFile = szInFile;
		}

		if(szDataFile.charAt(0)!='/') //put it in the temp dir
		{
			szDataFile = szTempDir + szDataFile;
		}

		return szDataFile;
	}

	/**
	 * IMPORTS entire design with keys intact
	 */
	public void doImport(RequestPath rPath)
	{
		long lAppID=1;
		int iCount=0;
		String szApplication = rPath.Application;

		while(lAppID>=0)
		{
			lAppID = getAppID(rPath.Group, szApplication);
			iCount++;
			if(lAppID>=0) szApplication = rPath.Application + iCount;
		}
		m_rPathNew = new RequestPath(rPath.Group + "/" + szApplication + ".pma");

		System.out.println("IMPORTING AS: " + m_rPathNew.getFullPath());

		m_bSomeImported = false;    
		XMLImportParser xml = new XMLImportParser(m_in, true);	
		try
		{
			xml.parse(this);
		}
		catch(Exception e)
		{
			System.err.println(e.toString());
			e.printStackTrace();
		}
		if(m_bSomeImported) m_bOK = true;
	}


	/**
	 * set the foreign keys etc, based on values in the vector
	 */
	private void transposeKeys(TableRow tr)
	{
		String sTableName = tr.getTableName();
		if(sTableName.equalsIgnoreCase("APPLICATION")) return;
		String sParentTable = getParentTable(sTableName);		
		Hashtable ht = tr.getColumns();
		Enumeration en = ht.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tci = (TableColumnItem)en.nextElement();
			long lID = getTranslated(sParentTable, tci.Name, tr.getColumnValueLong(tci.Name));
			if(lID>=0) //only update if there is a valid one
			{
				tci.Type = TableColumnItem.ITEM_TYPE_INTEGER;
				String sNewValue = "" + lID;
				//System.out.println("TRANSLATING: " + tci.Name + " from=" + tci.Value + " to=" + new String(szNewValue.getBytes()));
				tci.Value = sNewValue.getBytes();
			}
		}
	}


	/**
	 * returns mapping based on design tables
	 */
	private String getParentTable(String szChildTable)
	{
		if(szChildTable.equalsIgnoreCase("DESIGNBUCKET")) return "APPLICATION";
		if(szChildTable.equalsIgnoreCase("APPPARAM")) return "APPLICATION";
		if(szChildTable.equalsIgnoreCase("DESIGNBUCKETPARAM")) return "DESIGNBUCKET";
		if(szChildTable.equalsIgnoreCase("ROLE")) return "APPLICATION";
		if(szChildTable.equalsIgnoreCase("PERMISSION")) return "ROLE";
		if(szChildTable.equalsIgnoreCase("DBCONNECTION")) return "APPLICATION";
		if(szChildTable.equalsIgnoreCase("PMATABLE")) return "DBCONNECTION";
		if(szChildTable.equalsIgnoreCase("ATTRIBUTE")) return "PMATABLE";
		if(szChildTable.equalsIgnoreCase("KEYWORD")) return "APPLICATION";
		if(szChildTable.equalsIgnoreCase("KEYWORDDATA")) return "KEYWORD";
		if(szChildTable.equalsIgnoreCase("STRINGTABLEKEY")) return "APPLICATION";
		if(szChildTable.equalsIgnoreCase("STRINGTABLEVALUE")) return "STRINGTABLEKEY";
		//else don't know....
		return "";

	}

	/**
	 * Get the standard XML preamble
	 */
	public String getXMLHeader()
	{
		StringBuilder sb = new StringBuilder(256);
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		sb.append("<!-- Created by: Tornado Server - http://www.puakma.net/ -->\r\n");
		sb.append("<!-- Created on: " + puakma.util.Util.formatDate(new java.util.Date(), "dd.MM.yyyy HH:mm:ss") + " -->\r\n\r\n");
		return sb.toString();
	}

	/**
	 * Export the named app the outputstream
	 * @param out_bare
	 * @param rPath
	 * @return
	 */
	public boolean doExport(OutputStream out_bare, RequestPath rPath)
	{
		String sWhere;
		Statement stmt, stmt2, stmt3;
		ResultSet rs, rs2;
		long lID, lDBConnectionID, lTableID;
		long lAppID = getAppID(rPath.Group, rPath.Application);
		if(lAppID<0)
		{
			System.out.println("The application to export could not be found: " + rPath.getFullPath());
			return false;
		}

		ZipOutputStream zipoutput=null;

		try
		{
			String sPath = rPath.getPathToApplication().replaceAll("/", "~");
			ZipEntry ze = new ZipEntry(sPath);
			zipoutput = new ZipOutputStream(out_bare);
			zipoutput.putNextEntry(ze);

			//out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n".getBytes("UTF-8"));
			//out.write(("<!-- Created by: " + pmaSystem.getVersionString() + " -->\r\n").getBytes("UTF-8"));
			//out.write(("<!-- Created on: " + puakma.util.Util.formatDate(new java.util.Date(), "dd.MM.yyyy HH:mm:ss") + " -->\r\n\r\n").getBytes("UTF-8"));

			zipoutput.write(getXMLHeader().getBytes("UTF-8"));
			zipoutput.write(("<application name=\"" + rPath.getFullPath() + "\">\r\n").getBytes("UTF-8"));
			stmt = m_cx.createStatement();
			stmt2 = m_cx.createStatement();
			stmt3 = m_cx.createStatement();
			String sAppIDWhere = " WHERE AppID=" + lAppID;
			exportTable(stmt, "APPLICATION", sAppIDWhere, "", zipoutput);
			exportTable(stmt, "APPPARAM", sAppIDWhere, "", zipoutput);      
			exportTable(stmt, "DBCONNECTION", sAppIDWhere, "", zipoutput);


			exportTable(stmt, "ROLE", sAppIDWhere, "", zipoutput);
//			do the permissions
			rs = stmt.executeQuery("SELECT RoleID FROM ROLE WHERE AppID=" + lAppID);
			while(rs.next())
			{
				lID = rs.getLong(1);
				sWhere = " WHERE RoleID=" + lID;
				exportTable(stmt2, "PERMISSION", sWhere, "", zipoutput);
			}
			rs.close();

			exportTable(stmt, "KEYWORD", sAppIDWhere, "", zipoutput);
			//do keyworddata
			rs = stmt.executeQuery("SELECT KeywordID FROM KEYWORD WHERE AppID=" + lAppID);
			while(rs.next())
			{
				lID = rs.getLong(1);
				sWhere = " WHERE KeywordID=" + lID;
				exportTable(stmt2, "KEYWORDDATA", sWhere, "", zipoutput);
			}
			rs.close();
			
			exportTable(stmt, "STRINGTABLEKEY", sAppIDWhere, "", zipoutput);
			//do stringtablevalue
			rs = stmt.executeQuery("SELECT StringTableKeyID FROM STRINGTABLEKEY WHERE AppID=" + lAppID);
			while(rs.next())
			{
				lID = rs.getLong(1);
				sWhere = " WHERE StringTableKeyID=" + lID;
				exportTable(stmt2, "STRINGTABLEVALUE", sWhere, "", zipoutput);
			}
			rs.close();

			exportTable(stmt, "DESIGNBUCKET", sAppIDWhere, "ORDER BY DesignType DESC", zipoutput);
			//do design bucket params
			rs = stmt.executeQuery("SELECT DesignBucketID FROM DESIGNBUCKET WHERE AppID=" + lAppID);
			while(rs.next())
			{
				lID = rs.getLong(1);
				sWhere = " WHERE DesignBucketID=" + lID;
				exportTable(stmt2, "DESIGNBUCKETPARAM", sWhere, "", zipoutput);
			}
			rs.close();

			//do pmaTable
			rs = stmt.executeQuery("SELECT DBConnectionID FROM DBCONNECTION WHERE AppID=" + lAppID);
			while(rs.next())
			{
				lDBConnectionID = rs.getLong(1);

				//do pmaTable
				rs2 = stmt2.executeQuery("SELECT TableID FROM PMATABLE WHERE DBConnectionID=" + lDBConnectionID + " ORDER BY BuildOrder ASC");
				while(rs2.next())
				{
					lTableID = rs2.getLong(1);
					sWhere = " WHERE TableID=" + lTableID;
					exportTable(stmt3, "PMATABLE", sWhere, "", zipoutput);
					//ATTRIBUTES
					exportTable(stmt3, "ATTRIBUTE", sWhere, "", zipoutput);
				}
				rs2.close();
			}
			rs.close();

			stmt.close();
			stmt2.close();
			stmt3.close();
			m_bOK = true;

			//write the trailer and close the stream
			zipoutput.write("</application>\r\n".getBytes("UTF-8"));			
			//zp.setComment("PUAKMA DATA EXPORT: " + new java.util.Date());
			zipoutput.closeEntry();
			zipoutput.flush();			
			zipoutput.finish(); //close();
		}
		catch(Exception e)
		{
			System.err.println("Error: " + e.toString());
			return false;
		}

		return true;
	}


	/**
	 * 
	 * @param stmt
	 * @param szTableName
	 * @param szWhere
	 * @param sOrderBy
	 * @param out
	 * @throws Exception
	 */
	private void exportTable(Statement stmt, String szTableName, String szWhere, String sOrderBy, OutputStream out) throws Exception
	{
		String szSQLQuery;
		if(sOrderBy==null) sOrderBy = "";

		szSQLQuery = "SELECT * FROM " + szTableName + szWhere + " " + sOrderBy;
		ResultSet rs = stmt.executeQuery(szSQLQuery);
		exportQuery(szTableName, rs, out);
		rs.close();
	}


	/**
	 * Gets the ID of the requested application
	 * @param sGroup
	 * @param sApplication
	 * @return -1 if the app cannot be found
	 */
	private long getAppID(String sGroup, String sApplication)
	{
		Statement stmt = null;
		ResultSet rs = null;
		String szGroupClause="";
		long lAppID=-1;
		if(sGroup.length()==0)
			szGroupClause=" AND (AppGroup='' OR AppGroup IS NULL)";
		else
			szGroupClause=" AND UPPER(AppGroup)='" + sGroup.toUpperCase() + "'";


		try
		{
			String szQuery = "SELECT AppID FROM APPLICATION WHERE UPPER(AppName)='" + sApplication.toUpperCase() + "' " + szGroupClause;
			//System.out.println(szQuery);
			stmt = m_cx.createStatement();
			rs = stmt.executeQuery(szQuery);
			if(rs.next())
			{
				lAppID = rs.getLong(1);
			}			
		}
		catch (Exception sqle)
		{
			System.out.println("getAppID() " + sqle.getMessage());
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
		}
		return lAppID;
	}


	/**
	 * Method to control whether source code is created inside the pmx. Default is true - 
	 * source code is exported.
	 */
	public void setExportSources(boolean bAllowSourceExport)
	{
		m_bSourceExport = bAllowSourceExport;
	}

	/**
	 * Export a single row from a ResultSet. Assumes the cursor has already been positioned.
	 * @param sTableName
	 * @param rs
	 * @param out
	 * @throws Exception
	 */
	public void exportRow(String sTableName, ResultSet rs, OutputStream out) throws Exception
	{
		ResultSetMetaData rsmd = rs.getMetaData();
		//szTableName = rsmd.getTableName(1);
		TableRow tr = new TableRow(sTableName);
		for(int i=1; i<=rsmd.getColumnCount(); i++)
		{
			String sColumnName = rsmd.getColumnName(i);          
			boolean bPrimaryKey=isPrimaryKey(sTableName, sColumnName);
			//szForeignKeyTo=getForeignTable(szTableName, szName);
			switch(rsmd.getColumnType(i))
			{
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.LONGVARCHAR:
				tr.addColumn(sColumnName, rs.getString(i), bPrimaryKey, "");
				break;
			case Types.BIT:
			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:
			case Types.BIGINT:
				//TODO! primary key & foreign keys
				tr.addColumn(sColumnName, rs.getLong(i), bPrimaryKey, "");
				break;
			case Types.FLOAT:
			case Types.REAL:
			case Types.DOUBLE:
			case Types.NUMERIC:
			case Types.DECIMAL:
				tr.addColumn(sColumnName, rs.getDouble(i), bPrimaryKey, "");
				break;
			case Types.DATE:
			case Types.TIME:
			case Types.TIMESTAMP:
				java.util.Date dt = null;
				Timestamp ts = rs.getTimestamp(i);
				if(ts!=null) dt = new java.util.Date(ts.getTime());                
				tr.addColumn(sColumnName, dt);
				break;
			case Types.LONGVARBINARY:
				tr.addColumn(sColumnName, Util.getBlobBytes(rs, i));
				break;
			default:
				tr.addColumn(sColumnName, Util.getBlobBytes(rs, i));
			};
			if(rs.wasNull())
			{
				TableColumnItem tci = tr.getColumn(sColumnName);
				if(tci!=null) tci.setValue(null);
			}


			//Note: if this is used in a generic way, then a table with the same name and column name
			//may not be exported !!
			if(!m_bSourceExport)         
			{
				//determine if this is a source column we are dealing with
				//if so
				if(sTableName.equalsIgnoreCase("DESIGNBUCKET") && sColumnName.equalsIgnoreCase("DesignSource"))
				{
					TableColumnItem tci = tr.getColumn(sColumnName);
					tci.setValue(null); //clear the source column
				}
			}
		}//for
		//System.out.print(".");
		//System.out.println(tr.toString());

		out.write(tr.toXML());

		//out.writeObject(tr);
	}

	/**
	 * Exports the data from the resultset to the OutputStream.
	 * @return true if export was OK
	 */
	public boolean exportQuery(String sTableName, ResultSet rs, OutputStream out)
	{		
		try
		{        
			while(rs.next())
			{
				exportRow(sTableName, rs, out);
			}
		}
		catch(Exception e)
		{
			System.out.println("exportTableRow Error: " + e.toString());
			return false;
		}
		return true;
	}


	/**
	 * Determines if the attribute passed is the primary key in the table passed
	 */
	private boolean isPrimaryKey(String szTableName, String szAttribute)
	{
		if(szTableName==null || szAttribute==null || m_cx==null) return false;
		if(szTableName.trim().length()==0 || szAttribute.trim().length()==0) return false;

		boolean bIsPrimary=false;
		boolean bFound=false;
		try
		{
			DatabaseMetaData dbmd = m_cx.getMetaData();
			//Note: postgresql likes the table name in lower case...
			//This will likely fuck us over later when using another RDBMS :-(
			ResultSet RS = dbmd.getPrimaryKeys(null, null, szTableName);
			while(RS.next())
			{
				bFound=true;
				String szATT = RS.getString("COLUMN_NAME");
				if(szATT!=null && szATT.equalsIgnoreCase(szAttribute)) 
				{
					bIsPrimary=true;
					break;
				}
			}
			RS.close();
			if(!bFound) //OK, we may be using postgresql which forces lowercase so check
			{
				RS = dbmd.getPrimaryKeys(null, null, szTableName.toLowerCase());
				while(RS.next())
				{
					bFound=true;
					String szATT = RS.getString("COLUMN_NAME");
					if(szATT!=null && szATT.equalsIgnoreCase(szAttribute)) 
					{
						bIsPrimary=true;
						break;
					}
				}
				RS.close();
			}
		}
		catch(Exception e)
		{
			System.out.println("isPrimaryKey(): " + e.toString());
			e.printStackTrace();
		}

		return bIsPrimary;
	}

	/**
	 * Set the internal database Connection object. Use this method when performing
	 * an ad hoc data export. The setting of the connection is required for isPrimaryKey()
	 * to work correctly 
	 */
	public void setConnection(Connection cx)
	{
		m_cx = cx;
	}

	/**
	 * THIS DOES NOT CURRENTLY WORK getMetaData() always returns an empty resultset!!!
	 */
	/*private String getForeignTable(String szTableName, String szAttribute)
  {
    String szForeignTable="";

    try
    {
      DatabaseMetaData dbmd = m_cx.getMetaData();
      ResultSet RS = dbmd.getImportedKeys(null, "", szTableName);
      while(RS.next())
      {
        szForeignTable = RS.getString("FKTABLE_NAME");

        if(szForeignTable!=null && szForeignTable.equalsIgnoreCase(szAttribute)) ;
      }
      RS.close();
    }
    catch(Exception e)
    {
      System.out.println("getForeignTable() Error: " + e.toString());
    }

    return szForeignTable;
  }
	 */

	/**
	 * Flags whether the import was successful or not
	 * @return
	 */
	public boolean isSuccess()
	{
		return m_bOK;
	}

	/**
	 *
	 */
	public void importCallback(TableRow tr) 
	{
		ResultSet rs = null;
		PreparedStatement prepStmt = null;
		try
		{
			String sTableName = tr.getTableName();
			String sIDColumn = sTableName + "ID";
			if(sIDColumn.equalsIgnoreCase("pmatableid")) sIDColumn = "TableID";
			if(sTableName.equalsIgnoreCase("APPLICATION")) sIDColumn = "AppID"; //why did I name it like THIS???!
			int iPos = tr.getColumnPosition(sIDColumn);
			if(iPos>=0)
			{
				//System.out.println("IMPORTING [" + tr.getTableName() + "]");
				//String szSQL = tr.getInsertSQL();
				//System.out.println(szSQL);
				transposeKeys(tr);
				//a fix as some old schemas still have this column in the table
				if(sTableName.equalsIgnoreCase("DBCONNECTION")) tr.removeColumn("LastUsed");

				if(sTableName.equalsIgnoreCase("ATTRIBUTE"))
				{
					TableColumnItem tci = tr.getColumn("RefTable");              
					//System.out.println(tci.toString());
					long lTableID = getTranslated("PMATABLE", "TableID", tr.getColumnValueLong(tci.Name));              
					if(lTableID>=0) 
					{
						tci.Value = String.valueOf(lTableID).getBytes();
						tci.Type = TableColumnItem.ITEM_TYPE_INTEGER;
					}
				}


				if(sTableName.equalsIgnoreCase("APPLICATION"))
				{					
					tr.getColumn("AppName").setStringValue(m_rPathNew.Application);
					tr.getColumn("AppGroup").setStringValue(m_rPathNew.Group);				
				}

				
				prepStmt = tr.prepare(m_cx, false);				
				prepStmt.execute();//do the insert					
				try{m_cx.commit();}catch(Exception exc){} //force a commit - oracle to make triggers go??
				Util.closeJDBC(prepStmt);


				//System.out.println("Selecting from " + sTableName);
				prepStmt = tr.prepare(m_cx, true);
				rs = prepStmt.executeQuery();
				if(rs.next())
				{
					long lID = rs.getLong(sIDColumn);	
					//System.out.println("Row found " + sIDColumn + "=" + lID);
					addTranslator(tr.getTableName(), sIDColumn, tr.getColumnValueLong(sIDColumn), lID);
				}
				/*else
				{
					System.out.println("Could not locate: "+tr.toString());
				}*/

			}
			m_bSomeImported = true;
		}
		catch(Exception sqle)
		{						
			System.err.println("importCallback(): " + sqle.toString() + " \r\n" + tr.toString());
			//sqle.printStackTrace();
		}  
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(prepStmt);
		}
	}


	/**
	 * F^%$king Oracle exports the data as numeric in the id columns, so here 
	 * we change the column types to INTEGER
	 * @param tr
	 */
	/*private void tweakColumnTypes(TableRow tr) 
	{
		Hashtable ht = tr.getColumns();
		Enumeration en = ht.elements();
		while(en.hasMoreElements())
		{
			TableColumnItem tci = (TableColumnItem) en.nextElement();
			if(tci.Name.toLowerCase().endsWith("id")) tci.Type = TableColumnItem.ITEM_TYPE_INTEGER;
		}

	}*/

}