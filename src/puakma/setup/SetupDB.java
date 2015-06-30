/** ***************************************************************
SetupDB.java
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

/*
 * THIS IS NOT CURRENTLY USED......
 */

package puakma.setup;

import java.io.*;
import java.util.*;
import java.sql.*;
/**
* This class is designed to set up the relational system database. It uses the
* configuration file /puakma/config/puakma.config and a sql data definition file
* /puakma/config/datadef.sql Remember to edit the config file before you begin
* to run this class!
*
* The RDBMS is created, then the data is imported (TODO!).
* @deprecated use puakma.addin.db.SETUP and load as a server addin
*/
public class SetupDB
{

  private SetupDB(){} //don't allow instantiation...

  /** Main entry point
  */
  public static void main(String args[])
  {
    String sSysAdminPW=null;
    String szSystemDriverClass, szSystemDBURL, SystemDBName, szSystemUser, szSystemPW;
    

    String szConfigFile, szDataDef, szQuery;
    Properties propConfig=new Properties(); //configuration parameters
    int iErrorCount=0;
    Connection cx=null;
    File fSQL;
    FileInputStream fin;
    BufferedReader bin;
    System.out.println("*** PUAKMA RDBMS SETUP ***");
    if(args.length < 2)
    {
      System.out.println("Database definition file parameter not passed. Aborting...");
      System.out.println("USAGE: SetupDB /puakma/config/puakma.config /puakma/config/datadef.sql [SysAdmin password]");
      //try{Thread.sleep(5000);}catch(Exception e){}
      System.exit(1);
      return;
    }
    else
    {      
      if(args.length==3) 
          sSysAdminPW = args[2];
      else
      {
          sSysAdminPW = propConfig.getProperty("SysAdminPWSetup");
          if(sSysAdminPW!=null && sSysAdminPW.length()==0) sSysAdminPW=null;
      }
      if(sSysAdminPW==null) sSysAdminPW = puakma.util.Util.formatDate(new java.util.Date(), "HHmmss");
      
      szConfigFile = args[0];
      try
      {
        FileInputStream fs = new FileInputStream(szConfigFile);
        propConfig.load(fs);
      }
      catch(Exception e)
      {
        System.out.println("FATAL: Error loading system config from: " + szConfigFile);
        System.exit(1);
        return;
      }
      System.out.println("Using CONFIG: " + szConfigFile);
      SystemDBName = propConfig.getProperty("SystemDBName");
      if(SystemDBName==null) SystemDBName = "";

      szSystemDBURL = propConfig.getProperty("SystemDBURL");
      szSystemUser = propConfig.getProperty("SystemUser");
      szSystemPW = propConfig.getProperty("SystemPW");
      szSystemDriverClass = propConfig.getProperty("SystemDriverClass");

      szDataDef = args[1];
      System.out.println("");
      System.out.println("RDBMS URL: " + szSystemDBURL);
      System.out.println("RDBMS DB Name: " + SystemDBName);
      System.out.println("Driver class: " + szSystemDriverClass);
      System.out.println("SQL definition file: " + szDataDef);
      System.out.println("DB Username: " + szSystemUser);
      System.out.println("DB Password: " + szSystemPW);
      System.out.println("");
      System.out.println("Login: SysAdmin\tPassword: "+sSysAdminPW);
      System.out.println("");
      fSQL = new File(szDataDef);
      if(!fSQL.canRead())
      {
        System.out.println("FATAL: " + szDataDef + " cannot be read. Aborting...");
        System.exit(1);
        return;
      }
      //System.out.println("Build will begin in 3 seconds...");
      //try{Thread.sleep(3000);}catch(Exception e){}
    }

    System.out.println("***** STARTING BUILD *****");
    try
    {
      fin = new FileInputStream(fSQL);
      bin = new BufferedReader(new InputStreamReader(fin));
      Class.forName(szSystemDriverClass).newInstance();
      cx = DriverManager.getConnection(szSystemDBURL, szSystemUser, szSystemPW);
      System.out.println(">> Connected to data source");
    }
    catch (Exception E)
    {
      System.err.println("FATAL: Unable to load JDBC driver. " + E.toString());
      //E.printStackTrace();
      System.exit(1);
      return;
    }

    try
    {
      Statement Stmt = cx.createStatement();
      try
      {
        Stmt.execute("CREATE DATABASE " + SystemDBName);
//        Stmt.close();
//        cx.close();
      }
      catch(Exception sqle)
      {
        System.err.println("WARNING: The system database '" + SystemDBName + "' could not be created. " + sqle.toString());
      }

      try
      {
        String sFullConnect = szSystemDBURL;
        if(sFullConnect.endsWith("/"))
          sFullConnect += SystemDBName;
        else
          sFullConnect += "/" + SystemDBName;
        cx = DriverManager.getConnection(sFullConnect, szSystemUser, szSystemPW);
        Stmt = cx.createStatement();

        //CRAP!! The following only seems to work on some databases!!
        //Stmt.execute("USE " + SystemDBName);
      }
      catch(Exception sqle2)
      {
        System.err.println("FATAL: The system database '" + SystemDBName + "' could not be opened. " + sqle2.toString());
        System.exit(1);
        return;
      }

      szQuery = bin.readLine();
      while(szQuery != null)
      {
        if(!(szQuery.length()==0 || szQuery.startsWith("#")))
        {
           //System.out.println(szQuery);
           System.out.println("");
           System.out.println(">> " + szQuery);
           try
           {
              Stmt.execute(szQuery);
           }
           catch(Exception sqle)
           {
              System.err.println("Error: '" + sqle.toString() + "'. Check that your database server understands the commands in " + szDataDef );
              iErrorCount++;
           }
        }
        szQuery = bin.readLine();
      }//while
    }
    catch(Exception ex)
    {
      System.err.println("Error: " + ex.toString());
      //ex.printStackTrace();
      System.exit(1);
      return;
    }

    String szFullUserName = "CN=System Administrator/O=System";
    String szUserName="SysAdmin";
    
    try
    {
      Statement Stmt = cx.createStatement();
      String szEncryptedPassword = puakma.util.Util.encryptString(sSysAdminPW);
      //System.out.println("enc=[" + szEncryptedPassword + "]");
      ResultSet RS = Stmt.executeQuery("SELECT * FROM PERSON WHERE UserName='" + szFullUserName + "'");
      boolean bAdminCreated = false;
      if(RS.next()) bAdminCreated = true;
      RS.close();
      if(!bAdminCreated)
      {
        Stmt.execute("INSERT INTO PERSON(FirstName,LastName,ShortName,UserName,Alias,Comment,Password,Created,CreatedBy,LoginFlag) VALUES('System','Administrator','" + szUserName + "','" + szFullUserName + "','admin','','" + szEncryptedPassword + "',CURRENT_TIMESTAMP,'System Setup','')");
        Stmt.execute("INSERT INTO PMAGROUP(GroupName,Description) VALUES('Admin','Users who may administer the system')");
        RS = Stmt.executeQuery("SELECT GroupID FROM PMAGROUP WHERE GroupName='Admin'");
        if(RS.next())
        {
          String szGroupID = RS.getString(1);
          Stmt.execute("INSERT INTO PMAGROUPMEMBER(GroupID,Member) VALUES(" + szGroupID +",'" + szFullUserName + "')");
        }
        RS.close();
        Stmt.close();
        System.out.println("\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n");
        System.out.println("***** FOLLOWING IS YOUR USERNAME AND PASSWORD FOR INITIAL SYSTEM ADMINISTRATION *****");
        System.out.println("");
        System.out.println("");
        System.out.println("  UserName: " + szUserName + " (" + szFullUserName + ")");
        System.out.println("  Password: " + sSysAdminPW);
        System.out.println("");
        System.out.println("");
      }
      Stmt.close();
    }
    catch(Exception sqle)
    {
      iErrorCount++;
      System.err.println("FATAL: The system administration account could not be created. " + sqle.toString());
    }


    System.out.println("");
    System.out.println("***** RDBMS BUILD COMPLETE (" + iErrorCount + " errors) *****");
    
  }

}