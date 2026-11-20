/** ***************************************************************
SharedActionClassLoader.java
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
package puakma.addin.http.action;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.addin.http.document.DesignElement;
import puakma.system.RequestPath;
import puakma.system.SystemContext;
import puakma.util.ClassData;
import puakma.util.Util;


/**
 * This class is used to load an action from the rdbms.
 * An action is loaded, then all libraries are loaded into a cache. This seems
 * to be a rather inefficient way of doing things, but can't think of a better way.
 */
public class SharedActionClassLoader extends ClassLoader
{
	private String m_sGroup;
	private String m_sApp;
	private String m_sBasePath;  
	private SystemContext m_pSystem;
	private Hashtable<String, ClassCacheEntry> m_cache=new Hashtable<String, ClassCacheEntry>(30);
	private Hashtable<String, String> m_loadedDesigns=new Hashtable<String, String>(30);  
	//lower case design element name -> the first class loaded from it. Saves scanning m_cache
	//on every request when the design name differs from the class name
	private Hashtable<String, String> m_htDesignToClass=new Hashtable<String, String>(30);
	//private int m_iTypeToLoad = 0;
	private Vector<ZipFile> m_vJars = new Vector<ZipFile>(); //open temp jar files, kept open for resource lookups
	private volatile boolean m_bClosed = false;

	public final static String LIBRARY="UseLibrary";

	private class ClassCacheEntry
	{
		byte[] m_ClassData;
		String m_sDesignName;
		String m_sClassName;
		boolean m_bIsDefined;
		boolean m_bIsResolved;
		Class m_ClassObject;

		protected String getPackageName()
		{
			if(m_sClassName==null) return null;
			int iPos = m_sClassName.lastIndexOf('.');
			if(iPos>=0)
			{
				return m_sClassName.substring(0, iPos);
			}
			return ""; //default package
		}
	}

	/**
	 * Used by the AGENDA task
	 * @param paramSystem
	 * @param paramGroup
	 * @param paramApp
	 * @param iTypeToLoad use DesignElement.DESIGN_TYPE_xxxx
	 */
	public SharedActionClassLoader(SystemContext paramSystem, String paramGroup, String paramApp)
	{
		super(SharedActionClassLoader.class.getClassLoader());    
		m_pSystem = paramSystem;
		m_sGroup = paramGroup;
		m_sApp = paramApp;
		RequestPath r = new RequestPath(m_sGroup, m_sApp, "", "");
		m_sBasePath = r.getPathToApplication();


		TornadoServerInstance tsi = TornadoServer.getInstance(m_pSystem);
		//load all Libraries      		
		/*
		String sLibNames[] = tsi.getAllDesignElementNames(m_sGroup, m_sApp, DesignElement.DESIGN_TYPE_LIBRARY); 
		if(sLibNames!=null)
		{
			for(int i=0; i<sLibNames.length; i++)  loadDesignData(sLibNames[i], DesignElement.DESIGN_TYPE_LIBRARY);
		}*/

		loadCache(tsi, m_sGroup, m_sApp, DesignElement.DESIGN_TYPE_LIBRARY);
		loadCache(tsi, m_sGroup, m_sApp, DesignElement.DESIGN_TYPE_ACTION);
		//loadCache(tsi, m_sGroup, m_sApp, DesignElement.DESIGN_TYPE_BUSINESSWIDGET);
		//loadCache(tsi, m_sGroup, m_sApp, DesignElement.DESIGN_TYPE_SCHEDULEDACTION);

		/*if(m_iTypeToLoad==DesignElement.DESIGN_TYPE_ACTION)
		{
			String sActionNames[] = tsi.getAllDesignElementNames(m_sGroup, m_sApp, DesignElement.DESIGN_TYPE_ACTION);
			if(sActionNames!=null)
			{
				for(int i=0; i<sActionNames.length; i++)  loadDesignData(sActionNames[i], false);
			}
		} */
	}

	/**
	 * Assumes all actions names are unique!
	 * @param tsi
	 * @param sGroup
	 * @param sApp
	 * @param iDesignType
	 */
	private void loadCache(TornadoServerInstance tsi, String sGroup, String sApp, int iDesignType)
	{
		String sActionNames[] = tsi.getAllDesignElementNames(m_sGroup, m_sApp, iDesignType);// DesignElement.DESIGN_TYPE_ACTION);
		if(sActionNames!=null)
		{
			for(int i=0; i<sActionNames.length; i++)  loadDesignData(sActionNames[i], iDesignType);
		}
	}

	/**
	 * Recursively loads classes into the cache. 
	 * @return the actual class name
	 */
	private String loadDesignData(String sDesignElementName, int iDesignElementType)
	{
		DesignElement des = null;
		byte[] classContent=null;
		//int iType = 0;
		//, boolean bLoadALibrary
		String sClassName = null;
		//System.out.println("loading "+szDesignName+". LoadALibrary="+bLoadALibrary);

		//check that we haven't already processed the named design element.
		//this may occur if two design elements reference each other as libraries
		if(m_loadedDesigns.containsKey(sDesignElementName)) return getClassNameFromCache(sDesignElementName);
		m_loadedDesigns.put(sDesignElementName, sDesignElementName);
		TornadoServerInstance tsi = TornadoServer.getInstance(m_pSystem);

		//if(bLoadALibrary) iType = DesignElement.DESIGN_TYPE_LIBRARY; else iType = m_iTypeToLoad;

		des = tsi.getDesignElement(m_sGroup, m_sApp, sDesignElementName, iDesignElementType);

		if(des != null)
		{
			classContent = des.getContent();
			if(classContent!=null)
			{
				if(des.m_sFullClassName==null)
				{
					ClassData cd = new ClassData( new ByteArrayInputStream(classContent));
					if(cd.isValidClass())
					{
						//System.out.println("just parsed: " + cd.m_FullName);
						des.m_sFullClassName = cd.m_FullName;
						sClassName = cd.m_FullName;
					}

				}   

				if(des.m_sFullClassName!=null)
				{
					loadClassData(classContent, des.m_sFullClassName, sDesignElementName);
					sClassName = des.m_sFullClassName;
				}
				else
				{
					//test if a jar file and load contents
					if(!loadFromZIP(sDesignElementName, classContent))
					{              
						m_pSystem.doError("The content of design element " + sDesignElementName + " is not a class or ZIP file", m_pSystem);
					}
				}
			}
		}

		return sClassName;
	}

	/**
	 * Look into the cache and determine the actual classname from the design element name
	 */
	private String getClassNameFromCache(String sDesignActionName)
	{
		if(m_cache.containsKey(sDesignActionName)) 
		{
			//class and design name are the same
			ClassCacheEntry cache_entry = (ClassCacheEntry)m_cache.get(sDesignActionName);
			return cache_entry.m_sClassName;
		}

		//every cached class with a design name is indexed here, so no need to scan m_cache
		return m_htDesignToClass.get(sDesignActionName.toLowerCase());
	}

	/**
	 * Determine the actual classname to run based on the design element name. If it
	 * is not found it will try to load the data from the db.
	 * @return the class, or null if it could not be found (the error is logged)
	 */
	public synchronized Class getActionClass(String sDesignElementName, int iDesignType) throws ClassNotFoundException
	{    
		String sActionClassName = getClassNameFromCache(sDesignElementName);
		if(sActionClassName==null)
		{
			sActionClassName = loadDesignData(sDesignElementName, iDesignType);
		}    

		if(sActionClassName!=null && sActionClassName.length()>0)
		{
			try
			{
				return loadClass(sActionClassName, true);
			}
			catch(ClassNotFoundException cnfe){}
		}

		m_pSystem.doError("ActionClassLoader.ClassNotFound", new String[]{sActionClassName==null ? sDesignElementName : sActionClassName, m_sBasePath}, m_pSystem);
		return null;
	}

	/**
	 * Resolves the specified name to a Class. Classes from this application's design
	 * elements are tried first, then the parent classloader.
	 * Synchronized as this is the context classloader for actions, so may be called 
	 * concurrently from many threads; two threads must not define the same class.
	 *
	 * @param      name the name of the desired Class.
	 * @param      resolve true if the Class needs to be resolved
	 * @return     the resulting Class.
	 * @exception  ClassNotFoundException  if the class loader cannot
	 *             find a the requested class.
	 */
	protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException
	{
		if(name==null || name.length()==0) throw new ClassNotFoundException(name);

		//try loading the class from this classloader first
		ClassCacheEntry cache_entry = (ClassCacheEntry)m_cache.get(name);
		if(cache_entry==null) return getParent().loadClass(name);

		if(!cache_entry.m_bIsDefined) 
		{
			String sPackageName = cache_entry.getPackageName();
			Package pkg = getPackage(sPackageName);
			if(pkg==null)
			{
				definePackage(sPackageName, cache_entry.m_sClassName, "specversion",
						"TornadoServer",  "impltitle", "implversion",
						"implVendor", null);
			}
			cache_entry.m_ClassObject = defineClass(cache_entry.m_sClassName, cache_entry.m_ClassData, 0, cache_entry.m_ClassData.length);
			cache_entry.m_ClassData = null; //no longer needed, the JVM has its own copy
			cache_entry.m_bIsDefined = true;
		}

		if(resolve && !cache_entry.m_bIsResolved) 
		{
			resolveClass(cache_entry.m_ClassObject);				
			cache_entry.m_bIsResolved = true;
		}

		return cache_entry.m_ClassObject;
	}

	/**
	 * Attempts to load all the class files in a ZIP or JAR file
	 */
	private boolean loadFromZIP(String szDesignName, byte[] data)
	{
		File fileTmp = null;
		ZipFile zipfile = null;
		try
		{
			fileTmp = File.createTempFile(String.valueOf(m_pSystem.getUniqueNumber())+"_zip_", null, m_pSystem.getTempDir() );
			fileTmp.deleteOnExit(); //close() deletes it on reload; this catches loaders still live at shutdown
			try(FileOutputStream fout = new FileOutputStream(fileTmp))
			{
				fout.write(data);
			}

			zipfile = new ZipFile(fileTmp);      
			Enumeration<? extends ZipEntry> e = zipfile.entries();
			while(e.hasMoreElements())
			{
				ZipEntry entry = e.nextElement();
				if(entry.isDirectory() || !entry.getName().endsWith(".class")) continue;

				try
				{
					byte[] buffer = Util.readStreamToByteArray(zipfile.getInputStream(entry));
					ClassData cd = new ClassData(new ByteArrayInputStream(buffer));            
					if(cd.isValidClass()) loadClassData(buffer, cd.m_FullName, szDesignName);
				}
				catch(Exception ex) //skip just this entry
				{
					m_pSystem.doError("Error reading " + entry.getName() + " from design element " + szDesignName + ": " + ex.toString(), m_pSystem);
				}
			}
		}
		catch(Exception exc)
		{
			if(zipfile!=null) try{ zipfile.close(); } catch(IOException ignored){}
			if(fileTmp!=null) fileTmp.delete();
			return false;
		}

		//keep the jar open for getResource() etc. It is closed and deleted by close()
		m_vJars.add(zipfile);
		return true;
	}

	/**
	 * Close and delete the temp jar files used by this classloader. Called when the 
	 * application's classloader is discarded (eg design reload). Classes already
	 * loaded remain usable, but resources in jars will no longer be found.
	 */
	public void close()
	{
		m_bClosed = true;
		synchronized(m_vJars)
		{
			for(ZipFile zipfile : m_vJars)
			{
				try{ zipfile.close(); } catch(IOException ignored){}
				new File(zipfile.getName()).delete();
			}
			m_vJars.clear();
		}
	}

	/**
	 * Loads a class into the cache, assume the byte array is a valid class.
	 * This was made public so we can externally load classes in an ad hoc fashion.
	 */
	public void loadClassData(byte[] classData, String szClassName, String szDesignName)
	{

		if(!m_cache.containsKey(szClassName))
		{
			//System.out.println("loadClassData(): " + szClassName);
			ClassCacheEntry c = new ClassCacheEntry();
			c.m_ClassData = classData;
			c.m_sDesignName = szDesignName;
			c.m_sClassName = szClassName;
			m_cache.put(szClassName, c);
			if(szDesignName!=null) m_htDesignToClass.putIfAbsent(szDesignName.toLowerCase(), szClassName);
			//if(m_ActionClass==null) m_ActionClass = szClassName;
		}
	}

	/**
	 * Always delegate to the parent classloader
	 */
	public InputStream getResourceAsStream(String name) 
	{     
		if(!m_bClosed)
		{
			for(ZipFile zipfile : getJars())
			{
				InputStream is = loadResourceFromZipfile(zipfile, name);
				if(is!=null) return is;
			}
		}

		ClassLoader cl = getParent();
		if(cl!=null) return cl.getResourceAsStream(name);      
		return null;
	}

	/*public Package getPackage(String sPackageName)
	{
		//TODO definePackage() ??
		System.out.println("getPackage()=["+sPackageName + "]" );
		Package pkg =  super.getPackage(sPackageName);
		if(pkg==null) 
			System.out.println("getPackage()=["+sPackageName + "] NOT DEFINED" );
		else
			System.out.println("getPackage() " + sPackageName + " " + pkg.toString());
		return pkg;
	}*/

	/**
	 * Finds all resources with the given name across the jar files loaded
	 * as design elements (m_vJars). This backs the inherited
	 * <code>getResources()</code>, which is what <code>ServiceLoader</code>
	 * (and therefore e.g. <code>ScriptEngineManager</code>) uses to discover
	 * provider-configuration files such as META-INF/services entries.
	 * Without this override, such files bundled in a jar loaded via this
	 * classloader are invisible to ServiceLoader.
	 */
	protected Enumeration<URL> findResources(String name) throws IOException
	{
		Vector<URL> urls = new Vector<URL>();
		if(m_bClosed) return urls.elements();
		for(ZipFile zipfile : getJars())
		{
			URL u = getResourceURLFromJar(zipfile, name);
			if(u!=null) urls.add(u);
		}
		return urls.elements();
	}

	/**
	 * This method should not return anything useful for resources that
	 * come from the db.
	 */
	public URL getResource(String name)
	{
		if (name == null) return null;

		if(!m_bClosed)
		{
			for(ZipFile zipfile : getJars())
			{
				URL u = getResourceURLFromJar(zipfile, name);
				if(u!=null) return u;          
			}
		}

		//try the parent classloader (pmaClassLoader)
		ClassLoader cl = getParent();
		if(cl!=null) return cl.getResource(name);      

		return null;
	}


	/**
	 * Snapshot of the open jars, so resource reads don't hold the m_vJars lock
	 */
	private ZipFile[] getJars()
	{
		return m_vJars.toArray(new ZipFile[0]);
	}

	/**
	 * returns a jar: URL if the jar file contains the file, else null
	 */
	private URL getResourceURLFromJar(ZipFile zipfile, String sFilePath)
	{
		try 
		{
			if(zipfile.getEntry(sFilePath) == null) return null;
			return new URL("jar:" + new File(zipfile.getName()).toURI() + "!/" + sFilePath);
		}
		catch(java.net.MalformedURLException badurl) 
		{
			badurl.printStackTrace();
		}
		catch(IllegalStateException closed){} //zip was closed by close()

		return null;
	}

	/**
	 * Loads resource from a zip file
	 */
	private InputStream loadResourceFromZipfile(ZipFile zipfile, String name) 
	{
		try 
		{
			ZipEntry entry = zipfile.getEntry(name);
			if(entry == null) return null;
			return new ByteArrayInputStream(Util.readStreamToByteArray(zipfile.getInputStream(entry)));
		} 
		catch(IOException e) 
		{
			return null;
		} 
		catch(IllegalStateException closed) //zip was closed by close()
		{
			return null;
		}
	}


}//class
