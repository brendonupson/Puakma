/* ***************************************************************
SETUP.java
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
package puakma.addin.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.port.pmaSerial;
import puakma.system.SystemContext;
import puakma.util.Util;

public class SETUP extends pmaAddIn 
{
	private pmaAddInStatusLine m_pStatus;	
	private boolean m_bAddInRunning=false;
	private static String FULL_USER_NAME = "CN=System Administrator/O=System";

	public void pmaAddInMain()
	{
		setAddInName("SETUP");
		m_pStatus = createStatusLine();
		m_pStatus.setStatus("Starting...");

		if(!doSetup(m_pSystem, this))
			m_pSystem.doError("There was a problem performing the database setup. Check the logs for further errors", this);

		m_bAddInRunning = true;
		m_pStatus.setStatus("Shutting down");

		m_pSystem.doInformation("Setup complete", this);

		removeStatusLine(m_pStatus);
	}

	/**
	 * 
	 */
	public boolean addInReady()
	{
		return m_bAddInRunning; //run to completion before loading other tasks
	}

	/**
	 * 
	 * @param pSystem
	 * @param aiLogObject
	 * @return
	 */
	public static boolean doSetup(SystemContext pSystem, pmaAddIn aiLogObject)
	{
		Object objLog = pSystem;
		if(aiLogObject!=null) objLog = aiLogObject;

		pSystem.doInformation("Checking database is exists", objLog);
		if(!SETUP.createDatabase(pSystem, aiLogObject)) return false;

		pSystem.doInformation("Checking database tables", objLog);
		if(SETUP.createTables(pSystem, aiLogObject)) return false;

		return true;
	}

	/**
	 * 
	 * @param pSystem
	 * @param aiLogObject
	 * @return
	 */
	private static boolean createTables(SystemContext pSystem, pmaAddIn aiLogObject) 
	{
		Object objLog = pSystem;
		if(aiLogObject!=null) objLog = aiLogObject;

		int iAppCount = 0;
		int iUserCount = 0;

		Connection cx = null;
		Statement stmt = null;
		ResultSet rs = null;

		try
		{
			cx = pSystem.getSystemConnection();
			if(cx==null) return false;

			boolean bCreateTables = false;
			stmt = cx.createStatement();


			//see if the db is built....
			try
			{
				stmt.execute("SELECT * FROM PMALOG");
			}
			catch(Exception t){ bCreateTables = true; }

			if(bCreateTables) 
				createTablesFromDefinition(pSystem, aiLogObject, cx); 
			else
			{
				rs = stmt.executeQuery("SELECT COUNT(AppID) FROM APPLICATION");
				if(rs.next())
				{
					iAppCount = rs.getInt(1);
				}
				Util.closeJDBC(rs);
				Util.closeJDBC(stmt);

				stmt = cx.createStatement();
				rs = stmt.executeQuery("SELECT COUNT(PersonID) FROM PERSON");
				if(rs.next())
				{
					iUserCount = rs.getInt(1);
				}
				Util.closeJDBC(rs);
				Util.closeJDBC(stmt);				
			}

			if(iAppCount==0)          
			{
				pSystem.doInformation("No HTTP apps detected, starting pmx file import...", objLog);              
				File fTemp = pSystem.getTempDir();
				String sImportText = "Importing pmx file: ";

				File fImportFiles[] = fTemp.listFiles();
				for(int i=0; i<fImportFiles.length; i++)
				{
					File fImport = fImportFiles[i];
					String sFile = fImport.getAbsolutePath();
					//only try to import pmx files in the temp dir
					if(fImport.isFile() && fImport.length()>0 && sFile.endsWith(".pmx"))
					{
						pSystem.doInformation(sImportText+sFile, objLog);
						String sPMAName = getPMAName(fImport.getName());
						new pmaSerial(cx, "IMPORT", sFile, sPMAName);
					}

				}//for				
			}


			if(iUserCount==0)
			{
				String sUserName="SysAdmin";
				String sSysAdminPW = pSystem.getSystemProperty("SysAdminPWSetup");
				if(sSysAdminPW==null) sSysAdminPW="";

				String sEncryptedPassword="";
				if(sSysAdminPW.length()>0) sEncryptedPassword = puakma.util.Util.encryptString(sSysAdminPW);
				pSystem.doInformation("No users detected, creating account for " + sUserName, objLog);
				stmt = cx.createStatement();
				stmt.execute("INSERT INTO PERSON(FirstName,LastName,ShortName,UserName,Alias,Comment,Password,Created,CreatedBy,LoginFlag) VALUES('System','Administrator','" + sUserName + "','" + FULL_USER_NAME + "','admin','','" + sEncryptedPassword + "',CURRENT_TIMESTAMP,'System Setup','')");
				stmt.execute("INSERT INTO PMAGROUP(GroupName,Description) VALUES('Admin','Users who may administer the system')");
				rs = stmt.executeQuery("SELECT GroupID FROM PMAGROUP WHERE GroupName='Admin'");
				if(rs.next())
				{
					String sGroupID = rs.getString(1);
					stmt.execute("INSERT INTO PMAGROUPMEMBER(GroupID,Member) VALUES(" + sGroupID +",'" + FULL_USER_NAME + "')");
				}
				Util.closeJDBC(rs);
				Util.closeJDBC(stmt);
			}

		}
		catch(Exception e)
		{
			pSystem.doError("FATAL: createTables()" + e.toString(), objLog);
			Util.logStackTrace(e, pSystem, 999);
			return false;
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			pSystem.releaseSystemConnection(cx);
		}
		return false;
	}

	/**
	 * 
	 * @param pSystem
	 * @param aiLogObject
	 */
	public static boolean createDatabase(SystemContext pSystem, pmaAddIn aiLogObject) 
	{
		Object objLog = pSystem;
		if(aiLogObject!=null) objLog = aiLogObject;

		String sSystemDBName = pSystem.getSystemProperty("SystemDBName");
		if(sSystemDBName==null) sSystemDBName = "puakma";

		Connection cx = null;
		Statement stmt = null;

		try
		{
			try{ cx = pSystem.getSystemConnection();} //HSQLDB will succeed here and create the db
			catch(Exception r){}
			if(cx==null)
			{
				pSystem.doInformation("Creating system database " + sSystemDBName, objLog);
				//create it
				String sSystemDBURL = pSystem.getSystemProperty("SystemDBURL");
				String sSystemUser = pSystem.getSystemProperty("SystemUser");
				String sSystemPW = pSystem.getSystemProperty("SystemPW");
				String sSystemDriverClass = pSystem.getSystemProperty("SystemDriverClass");

				System.out.println("");
				System.out.println("RDBMS URL: " + sSystemDBURL);
				System.out.println("RDBMS DB Name: " + sSystemDBName);
				System.out.println("Driver class: " + sSystemDriverClass);

				System.out.println("DB Username: " + sSystemUser);
				System.out.println("DB Password: " + sSystemPW);


				Class.forName(sSystemDriverClass).newInstance();
				//connect to the server, not a specific db. Some db servers may not allow this
				cx = DriverManager.getConnection(sSystemDBURL, sSystemUser, sSystemPW);
				stmt = cx.createStatement();
				stmt.execute("CREATE DATABASE " + sSystemDBName);
				pSystem.doInformation("System database [" + sSystemDBName + "] created", objLog);				
			}
			else
				pSystem.doInformation("System database " + sSystemDBName + " exists", objLog);
		}
		catch(Exception e)
		{
			pSystem.doError("FATAL: createDatabase() " + e.toString() + ". You may need to manually create a database named [" + sSystemDBName + "] first", objLog);				
			return false;
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}

		return true;		
	}

	/**
	 * 
	 * @param pSystem
	 * @param aiLogObject
	 * @param cx
	 * @throws Exception
	 */
	private static void createTablesFromDefinition(SystemContext pSystem, pmaAddIn aiLogObject, Connection cx) throws Exception
	{
		Object objLog = pSystem;
		if(aiLogObject!=null) objLog = aiLogObject;

		String sSystemDriverClass = pSystem.getSystemProperty("SystemDriverClass");
		if(sSystemDriverClass==null) sSystemDriverClass = "";
		sSystemDriverClass = sSystemDriverClass.toLowerCase();
		String sSuffix = "hsqldb"; 		
		if(sSystemDriverClass.indexOf("mysql")>=0) sSuffix = "mysql";
		if(sSystemDriverClass.indexOf("postgresql")>=0) sSuffix = "postgresql";
		if(sSystemDriverClass.indexOf("oracle")>=0) sSuffix = "oracle";
		if(sSystemDriverClass.indexOf(".jtds.")>=0) sSuffix = "sqlserver";


		String sDefFile = pSystem.getConfigDir().getAbsolutePath() +  "/datadef." + sSuffix;
		int iErrorCount=0;
		try
		{
			pSystem.doInformation("Creating DB with commands from: "+sDefFile, objLog);
			Statement stmt = cx.createStatement();
			File fSQL = new File(sDefFile);
			if(!fSQL.canRead()) 
			{
				pSystem.doError("Could not open datadef file: " + sDefFile, objLog);
				return;
			}
			FileInputStream fin = new FileInputStream(fSQL);
			BufferedReader bin = new BufferedReader(new InputStreamReader(fin));

			String szQuery = bin.readLine();
			while(szQuery != null)
			{
				if(!(szQuery.length()==0 || szQuery.startsWith("#")))
				{
					//System.out.println(szQuery);
					System.out.println("");
					System.out.println(">> " + szQuery);
					try
					{
						stmt.execute(szQuery);
					}
					catch(Exception sqle)
					{
						pSystem.doError("Error: '" + sqle.toString() + "'. Check that your database server understands the commands in " + sDefFile, objLog );
						iErrorCount++;
					}
				}
				szQuery = bin.readLine();
			}//while

		}
		catch (Exception e)
		{
			pSystem.doError("createTables() " + e.toString(), objLog);          
		}

		pSystem.doInformation("DB creation completed with "+iErrorCount+" errors", objLog);

	}

	/**
	 * 
	 * @param sPMXFile
	 * @return
	 */
	private static String getPMAName(String sPMXFile)
	{
		if(sPMXFile==null || sPMXFile.length()==0) return "/unknown.pma"; 


		// '/' is bad, should use the platform specific path separator
		int iPos = sPMXFile.lastIndexOf('/');
		if(iPos>=0) sPMXFile = sPMXFile.substring(iPos+1);

		sPMXFile = sPMXFile.replace('~', '/');
		sPMXFile = sPMXFile.replaceAll(".pmx", ".pma");

		return sPMXFile;
	}
}
