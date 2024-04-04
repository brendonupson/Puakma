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
import java.io.ByteArrayOutputStream;
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
	private Hashtable m_cache=new Hashtable();
	private Hashtable m_loadedDesigns=new Hashtable();  
	//private int m_iTypeToLoad = 0;
	private Vector m_vJars = new Vector(); //all File objects for jar files

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

		Enumeration en = m_cache.elements();
		while(en.hasMoreElements())
		{
			ClassCacheEntry cache_entry = (ClassCacheEntry)en.nextElement();
			if(cache_entry.m_sDesignName.equalsIgnoreCase(sDesignActionName)) return cache_entry.m_sClassName;
		}

		return null;
	}

	/**
	 * Determine the actual classname to run based on the design element name. If it
	 * is not found it will try to load the data from the db.
	 */
	public synchronized Class getActionClass(String sDesignElementName, int iDesignType) throws ClassNotFoundException
	{    
		String sActionClassName = getClassNameFromCache(sDesignElementName);
		if(sActionClassName==null)
		{
			sActionClassName = loadDesignData(sDesignElementName, iDesignType);
		}    
		//System.out.println(sActionClassName);
		return loadClass(sActionClassName, true);
	}

	
	protected Class findClass(String className) 
	{
		Class c = null;
		try
		{
		c = loadClass(className, false);
		}
		catch(Exception e)
		{}
		
		return c;
	}

	/**
	 * Resolves the specified name to a Class. The method loadClass()
	 * is called by the virtual machine.  As an abstract method,
	 * loadClass() must be defined in a subclass of ClassLoader.
	 *
	 * @param      name the name of the desired Class.
	 * @param      resolve true if the Class needs to be resolved;
	 *             false if the virtual machine just wants to determine
	 *             whether the class exists or not
	 * @return     the resulting Class.
	 * @exception  ClassNotFoundException  if the class loader cannot
	 *             find a the requested class.
	 */
	//synchronized?
	protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException
	{

		if(name==null || name.length()==0) return null;
		// The class object that will be returned.
		Class c = null; 

		boolean bFound=false;


		//try loading the class from this classloader first
		ClassCacheEntry cache_entry = (ClassCacheEntry)m_cache.get(name);
		if(cache_entry!=null)
		{
			if(!cache_entry.m_bIsDefined) 
			{
				//System.out.println("classname=["+cache_entry.m_sClassName + "]" + cache_entry.getPackageName());
				//todo define package??
				String sPackageName = cache_entry.getPackageName();
				Package pkg = getPackage(sPackageName);
				if(pkg==null)
				{
					//System.out.println("definePackage: classname=["+cache_entry.m_sClassName + "]" + cache_entry.getPackageName());
					definePackage(sPackageName, cache_entry.m_sClassName, "specversion",
							"TornadoServer",  "impltitle", "implversion",
							"implVendor", null);
				}
				//else
				//	System.out.println(cache_entry.getPackageName() + " is already defined");
				c = defineClass(cache_entry.m_sClassName, cache_entry.m_ClassData, 0, cache_entry.m_ClassData.length);				
				cache_entry.m_bIsDefined = true;
				cache_entry.m_ClassObject = c;
			}
			//System.out.println(name+" defined");
			if(!cache_entry.m_bIsResolved && resolve) 
			{
				resolveClass(c);				
				cache_entry.m_bIsResolved = true;
			}
			//if already defined, used the cached version
			if(c==null) c = cache_entry.m_ClassObject;

			//System.out.println(name+" resolved");
			bFound = true;
		}

		if(!bFound) //if that fails, then default to parent class loaders
		{			
			//NB: BJU 12/7/07 swapped order due to odd outofmemory exceptions in parent loader
			try
			{			
				ClassLoader clParent = this.getClass().getClassLoader();
				c = clParent.loadClass(name);
				if(c!=null) return c;
			}
			catch(java.lang.OutOfMemoryError ome)
			{
				ome.printStackTrace();
				return null;
			}		
			catch(Throwable cnfe){}
		}


		if(!bFound)
		{			
			m_pSystem.doError("ActionClassLoader.ClassNotFound", new String[]{name, m_sBasePath}, m_pSystem);
			return null;          
		}

		return c;
	}

	/**
	 * Attempts to load all the class files in a ZIP or JAR file
	 */
	private boolean loadFromZIP(String szDesignName, byte[] data)
	{
		File fileTmp = null;
		ZipEntry entry;
		try
		{
			fileTmp = File.createTempFile(String.valueOf(m_pSystem.getUniqueNumber())+"_zip_", null, m_pSystem.getTempDir() );
			fileTmp.deleteOnExit();
			FileOutputStream fout = new FileOutputStream(fileTmp);
			fout.write(data);
			fout.flush();
			fout.close();

			ZipFile zipfile = new ZipFile(fileTmp);      
			Enumeration e = zipfile.entries();
			while(e.hasMoreElements())
			{
				entry = (ZipEntry)e.nextElement();
				if(!entry.isDirectory() && entry.getName().endsWith(".class"))
				{
					InputStream is = zipfile.getInputStream(entry);
					int iTotal = (int)entry.getSize();
					int iTotalRead=0;
					byte[] buffer = new byte[iTotal];
					byte[] readbuf = new byte[65536]; //max 64K chunks?
					while(iTotalRead<iTotal)
					{
						int iRead = is.read(readbuf);
						System.arraycopy(readbuf, 0, buffer, iTotalRead, iRead);
						iTotalRead += iRead;
					}
					//System.out.println("Read " + iTotalRead + " bytes for " + entry.getName());
					is.close();
					//String szName = entry.getName().replace('/', '.');
					ClassData cd = new ClassData(new ByteArrayInputStream(buffer));            

					if(cd.isValidClass())
					{
						//if(!m_cache.containsKey(cd.m_FullName))
						//{
						loadClassData(buffer, cd.m_FullName, szDesignName);
						//}
					}//if valid class
				}
			}

			zipfile.close();
			//fileTmp.delete();
		}
		catch(java.lang.OutOfMemoryError ome)
		{
			//System.gc();
			try{ Thread.sleep(1000); } catch(Exception e){}
			return false;
		}
		catch(Exception exc)
		{
			if(fileTmp!=null) fileTmp.delete();
			return false;
		}

		if(fileTmp!=null) m_vJars.add(fileTmp);
		return true;
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
			//if(m_ActionClass==null) m_ActionClass = szClassName;
		}
	}

	/**
	 * Always delegate to the parent classloader
	 */
	public InputStream getResourceAsStream(String name) 
	{     
		//System.out.println("getResourceAsStream("+name+");");

		for(int i=0; i<m_vJars.size(); i++)
		{
			File fJar = (File)m_vJars.get(i);
			InputStream is = loadResourceFromZipfile(fJar, name);
			if(is!=null) return is;
		}

		ClassLoader cl = this.getClass().getClassLoader();
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
	 * This method should not return anything useful for resources that 
	 * come from the db.
	 */
	public URL getResource(String name) 
	{
		URL u = null;        
		if (name == null) return null;

		for(int i=0; i<m_vJars.size(); i++)
		{
			File fJar = (File)m_vJars.get(i);

			u = getResourceURLFromJar(fJar, name);
			if(u!=null) return u;          
		}

		//try the classloader for this class (pmaClassLoader)
		ClassLoader cl = this.getClass().getClassLoader();
		if(cl!=null) return cl.getResource(name);      

		return null;
	}


	/**
	 * returns true if the jar file contains the file
	 */
	private URL getResourceURLFromJar(File fJar, String sFilePath)
	{
		//System.out.println("getResourceURLFromJar() ["+fJar.getName() + "] for:["+sFilePath+"]");

		try 
		{
			ZipFile zipfile = new ZipFile(fJar.getAbsolutePath());
			ZipEntry entry = zipfile.getEntry(sFilePath);

			if (entry != null)
			{            
				try {
					return new URL("jar:file:" + fJar.getAbsolutePath() + "!/" + sFilePath);
				} catch(java.net.MalformedURLException badurl) {
					badurl.printStackTrace();
					return null;
				}            
			}
		}catch(Exception f){}

		return null;
	}

	/**
	 * Loads resource from a zip file
	 */
	private InputStream loadResourceFromZipfile(File file, String name) 
	{
		ZipFile zipfile = null; 

		//System.out.println("loadResourceFromZipfile() " + file.getName() + " " + name);  
		try 
		{
			zipfile = new ZipFile(file);
			ZipEntry entry = zipfile.getEntry(name);

			if (entry != null) 
			{   
				ByteArrayOutputStream baos = new ByteArrayOutputStream((int)entry.getSize());                
				InputStream is = zipfile.getInputStream(entry);
				byte buf[]=new byte[(int)entry.getSize()];
				int iRead = is.read(buf);
				while(iRead>0)
				{
					baos.write(buf, 0, iRead);
					iRead = is.read(buf);
				}
				if(baos.size()<=0) return null;                
				ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
				//System.out.println("... OK");
				return (InputStream)bais;
			} 
			else 
			{
				//System.out.println(name + "... NOT FOUND !!");
				return null;
			}
		} 
		catch(java.lang.OutOfMemoryError ome)
		{
			//System.gc();
			try{ Thread.sleep(1000); } catch(Exception e){}	
			return null;
		}
		catch(IOException e) 
		{
			return null;
		} 
		finally 
		{
			if ( zipfile != null ) 
			{                
				try 
				{
					zipfile.close();
				} 
				catch ( IOException ignored ){ }
			}
		}
	}


}//class
