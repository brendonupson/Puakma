/** ***************************************************************
pmaMain.java
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


package puakma;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.Vector;

/**
 * Where it all begins. Call this main method to instantiate the server.
 */
public class pmaMain extends Thread
{
	public final static String PUAKMA_RESTART="$$PUAKMA_RESTART";
	private boolean m_bRunning=false;
	private String m_sArgs[];


	public pmaMain(String args[])
	{
		m_sArgs = args;
	}

	/** Main entry point
	 */
	public static void main(String args[])
	{
		pmaMain p = new pmaMain(args);
		p.run();
	}

	/**
	 * is the server running?
	 */
	public boolean isRunning()
	{
		return m_bRunning;
	}

	/**
	 * ask the server to quit
	 */
	public void requestQuit()
	{
		m_bRunning=false;
		Runtime.getRuntime().exit(0); // ??? is this right?

	}

	/**
	 * This was created so the server may be launched from another container with ease
	 */
	public void run()
	{
		m_bRunning=true;
		String szConfigFile;
		System.out.println("*** PUAKMA TECHNOLOGY - http://www.puakma.net/ ***");
		if(m_sArgs==null || m_sArgs.length == 0)
		{
			System.out.println("CONFIG FILE PARAMETER NOT PASSED, USING DEFAULT...");
			szConfigFile = "../config/puakma.config";
		}
		else
			szConfigFile = m_sArgs[0];

		// Check for 1.4+ of JVM??

		String szServerClass = "puakma.server.pmaServer";

		boolean bFirstTime=true;
		while(bFirstTime || System.getProperty(PUAKMA_RESTART).equals("1"))
		{
			Vector<File> v = new Vector<File>();
			setParameters(szConfigFile, v);
			bFirstTime = false;
			System.setProperty(PUAKMA_RESTART, "0");
			try
			{								
				System.out.println("");
				System.out.println("");
				System.out.println("-- STARTING SERVER --");
				pmaClassLoader loader = new pmaClassLoader(v);
				Object objServer = (loader.loadClass(szServerClass)).newInstance();
				((Runnable)objServer).run();
				loader = null;
				objServer = null;
				System.runFinalization();
				try{ Thread.sleep(1000); }catch(Exception fin){}
				System.gc();
			}
			catch (Exception e)
			{
				System.out.println("FATAL ERROR: When loading pmaServer Object : " + e.toString());
				e.printStackTrace();
			}
		}//while

		m_bRunning=false;
	}



	/**
	 * Loads the jars/classes needed to kick the whole thing off
	 */
	private static void setParameters(String szConfigFile, Vector<File> v)
	{
		Properties propConfig=new Properties(); //configuration parameters
		try
		{
			FileInputStream fs = new FileInputStream(szConfigFile);
			try{propConfig.load(fs);} catch(IOException ioe){System.out.println("Error loading system properties from: " + szConfigFile);}
		}
		catch(FileNotFoundException e)
		{
			System.out.println("Error loading system config from: " + szConfigFile + " " + e.toString());
			System.exit(1);
		}
		System.out.println("CONFIG: " + szConfigFile);
		System.out.println("SYSTEM CLASSES: " + propConfig.getProperty("PuakmaClassPath"));
		System.out.println("ADDIN CLASSES: " + propConfig.getProperty("PuakmaAddInPath"));
		System.out.println("LIBRARY CLASSES: " + propConfig.getProperty("PuakmaLibPath"));
		v.addElement(new File(propConfig.getProperty("PuakmaClassPath")));
		addJDBCJars(propConfig.getProperty("PuakmaJDBCJarPath"), v);
		addClasses(propConfig.getProperty("PuakmaAddInPath"), v);
		addClasses(propConfig.getProperty("PuakmaLibPath"), v);

		System.setProperty("PuakmaConfigFile", szConfigFile);
	}

	/**
	 * Add the AddIn path to the classpath
	 */
	private static void addClasses(String szPath, Vector<File> v)
	{
		if(szPath==null) return;
		File fDir = new File(szPath);
		File fTest;
		int i;

		//check it exists and is a directory
		if(!fDir.exists()) return;
		if(!fDir.isDirectory()) return;

		v.addElement(fDir); //takes care of bare .class files

		String szFileList[] = fDir.list();

		for(i=0; i<szFileList.length; i++)
		{
			fTest = new File(szPath, szFileList[i]);
			int iPos = szFileList[i].lastIndexOf('.');
			String szExt = ""; 

			if(iPos>=0) szExt = szFileList[i].substring(iPos, szFileList[i].length()).toUpperCase();

			if(!fTest.isDirectory() && (szExt.equals(".JAR") || szExt.equals(".ZIP")))
			{
				System.out.println("LOADING: " + fTest.getAbsolutePath());
				v.addElement(fTest);        
			}
			else
			{
				if(fTest.isDirectory()) addClasses(fTest.getAbsolutePath(), v);
			}
		}

	}

	/**
	 *
	 */
	private static void addJDBCJars(String szPath, Vector<File> v)
	{
		if(szPath==null) return;
		File fJDBCDir = new File(szPath);
		File fTest;
		int i;

		//check it exists and is a directory
		if(!fJDBCDir.exists()) return;
		if(!fJDBCDir.isDirectory()) return;
		String szFileList[] = fJDBCDir.list();

		for(i=0; i<szFileList.length; i++)
		{
			fTest = new File(szPath, szFileList[i]);
			if(!fTest.isDirectory() && isJarOrZip(szFileList[i]))
			{
				System.out.println("LOADING JDBC DRIVER: " + szFileList[i]);
				v.addElement(fTest);
			}
			else
			{
				if(fTest.isDirectory()) addClasses(fTest.getAbsolutePath(), v);
			}
		}
	}

	/**
	 * Does a case insensitive test to see if the filename is a .jar or .zip file
	 */
	private static boolean isJarOrZip(String szFileName)
	{
		String szFileTest = szFileName.toUpperCase();
		//String szExt = szFileTest.substring(szFileTest.lastIndexOf("."), szFileTest.length());
		int iPos = szFileTest.lastIndexOf('.');
		String szExt = ""; 

		if(iPos>=0) szExt = szFileTest.substring(iPos, szFileTest.length()).toUpperCase();

		if(szExt.equals(".JAR") || szExt.equals(".ZIP")) return true;

		return false;
	}

}
