/** ***************************************************************
pmaClassLoader.java
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A class loader that loads classes from directories and/or zip-format
 * file such as JAR file. It tracks the modification time of the classes
 * it loads to permit reloading through re-instantiation.
 * <P>
 * When the classloader reports its creator that one of the classes it
 * has loaded has changed on disk, it should discard the classloader
 * and create a new instance using <CODE>reinstantiate</CODE>.
 * The classes are then reloaded into the new classloader as required.
 *
 */
public class pmaClassLoader extends ClassLoader
{
	/**
	 * Generation counter, incremented for each classloader as they are
	 * created.
	 */
	//static private int generationCounter = 0;

	/**
	 * Generation number of the classloader, used to distinguish between
	 * different instances.
	 */
	//private int generation;

	/**
	 * Cache of the loaded classes. This contains ClassCacheEntry keyed
	 * by class names.
	 */
	private Hashtable<String, ClassCacheEntry> cache = new Hashtable<String, ClassCacheEntry>();;

	/**
	 * The classpath which this classloader searches for class definitions.
	 * Each element of the vector should be either a directory, a .zip
	 * file, or a .jar file.
	 * <p>
	 * It may be empty when only system classes are controlled.
	 */
	private Vector<File> repository;

	/**
	 * Private class used to maintain information about the classes that
	 * we loaded.
	 */
	private static class ClassCacheEntry
	{

		/**
		 * The actual loaded class
		 */
		Class loadedClass;

		/**
		 * The file from which this class was loaded; or null if
		 * it was loaded from the system.
		 */
		File origin;

		/**
		 * The time at which the class was loaded from the origin
		 * file, in ms since the epoch.
		 */
		long lastModified;

		/**
		 * Check whether this class was loaded from the system.
		 */
		public boolean isSystemClass() {
			return origin == null;
		}
	}

	//file://-------------------------------------------------------Constructors

	/**
	 * Creates a new class loader that will load classes from specified
	 * class repositories.
	 *
	 * @param classRepository An set of File classes indicating
	 *        directories and/or zip/jar files. It may be empty when
	 *        only system classes are loaded.
	 * @throws java.lang.IllegalArgumentException if the objects contained
	 *        in the vector are not a file instance or the file is not
	 *        a valid directory or a zip/jar file.
	 */
	public pmaClassLoader(Vector<File> classRepository)
			throws IllegalArgumentException
	{
		// Create the cache of loaded classes
		//cache = new Hashtable();

		// Verify that all the repository are valid.
		/*//fixed 18/1/2025 Generics added
		Enumeration e = classRepository.elements();
		while(e.hasMoreElements()) {
			Object o = e.nextElement();

			//BJU 16/11/2006 to get past the above compiler warning
			if(o instanceof File)
				;
			else
				throw new IllegalArgumentException("Object " + o + " is not a valid \"File\" instance");
		}
		 */

		// Store the class repository for use
		this.repository = classRepository;

		// Increment and store generation counter
		//this.generation = generationCounter++;
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
	protected synchronized Class loadClass(String name, boolean resolve)
			throws ClassNotFoundException
	{
		// The class object that will be returned.
		Class c = null;

		// Use the cached value, if this class is already loaded into
		// this classloader.
		ClassCacheEntry entry = (ClassCacheEntry) cache.get(name);

		if (entry != null) {
			// Class found in our cache
			c = entry.loadedClass;
			if (resolve) resolveClass(c);
			return c;
		}

		if (!securityAllowsClass(name)) {
			return loadSystemClass(name, resolve);
		}

		// Try to load it from each repository
		Enumeration repEnum = repository.elements();

		// Cache entry.
		ClassCacheEntry classCache = new ClassCacheEntry();
		while (repEnum.hasMoreElements()) {
			byte[] classData;

			File file = (File) repEnum.nextElement();
			try {
				if (file.isDirectory()) {
					classData =
							loadClassFromDirectory(file, name, classCache);
				} else {
					classData =
							loadClassFromZipfile(file, name, classCache);
				}
			} catch(IOException ioe) {
				// Error while reading in data, consider it as not found
				classData = null;
			}

			if (classData != null) {
				// Define the class
				String sPackageName = getPackageNameFromClass(name);
				Package pkg = getPackage(sPackageName);
				if(pkg==null)
				{
					//System.out.println("definePackage: classname=["+name + "]" + sPackageName);
					definePackage(sPackageName, "title", "specversion",
							"TornadoServer",  "impltitle", "implversion",
							"implVendor", null);
				} 
				//else
				//	System.out.println(sPackageName + " is already defined");

				c = defineClass(name, classData, 0, classData.length);
				// Cache the result;
				classCache.loadedClass = c;
				// Origin is set by the specific loader
				classCache.lastModified = classCache.origin.lastModified();
				cache.put(name, classCache);

				// Resolve it if necessary
				if (resolve) resolveClass(c);

				return c;
			}
		}

		// Attempt to load the class from the system - last resort!!
		try {
			c = loadSystemClass(name, resolve);
			if (c != null) {
				if (resolve) resolveClass(c);
				return c;
			}
		} catch (Exception e) {
			c = null;
		}

		// If not found in any repository
		throw new ClassNotFoundException(name);
	}

	/**
	 * Determine the package name from the class name
	 * @param sClassName
	 * @return
	 */
	private String getPackageNameFromClass(String sClassName) 
	{
		if(sClassName==null) return null;
		int iPos = sClassName.lastIndexOf('.');
		if(iPos>=0)
		{
			return sClassName.substring(0, iPos);
		}
		return ""; //default package
	}

	/**
	 * Load a class using the system classloader.
	 *
	 * @exception  ClassNotFoundException  if the class loader cannot
	 *             find a the requested class.
	 * @exception  NoClassDefFoundError  if the class loader cannot
	 *             find a definition for the class.
	 */
	private Class loadSystemClass(String name, boolean resolve)
			throws NoClassDefFoundError, ClassNotFoundException
	{
		Class c = findSystemClass(name);
		// Throws if not found.

		// Add cache entry
		ClassCacheEntry cacheEntry = new ClassCacheEntry();
		cacheEntry.origin = null;
		cacheEntry.loadedClass = c;
		cacheEntry.lastModified = Long.MAX_VALUE;
		cache.put(name, cacheEntry);

		if (resolve) resolveClass(c);
		return c;
	}


	/**
	 * Checks whether a classloader is allowed to define a given class,
	 * within the security manager restrictions.
	 */    
	private boolean securityAllowsClass(String className) 
	{  
		//allow everything.....
		return true;
		/*
        try {
            SecurityManager security = System.getSecurityManager();

            if (security == null) {
                // if there's no security manager then all classes
                // are allowed to be loaded
                return true;
            }

            int lastDot = className.lastIndexOf('.');
            // Check if we are allowed to load the class' package
            security.checkPackageDefinition((lastDot > -1)
                ? className.substring(0, lastDot) : "");
            // Throws if not allowed
            return true;
        } catch (SecurityException e) 
        {
            //System.out.println(e.toString());
            return false;
        }  
		 */               
	}

	/**
	 * Tries to load the class from a directory.
	 *
	 * @param dir The directory that contains classes.
	 * @param name The classname
	 * @param cache The cache entry to set the file if successful.
	 */
	private byte[] loadClassFromDirectory(File dir, String name,
			ClassCacheEntry cache)
					throws IOException
	{
		// Translate class name to file name
		String classFileName = name.replace('.', File.separatorChar) + ".class";

		// Check for garbage input at beginning of file name
		// i.e. ../ or similar
		if (!Character.isJavaIdentifierStart(classFileName.charAt(0))) {
			// Find real beginning of class name
			int start = 1;
			while (!Character.isJavaIdentifierStart(
					classFileName.charAt(start++)));
			classFileName = classFileName.substring(start);
		}

		File classFile = new File(dir, classFileName);

		if (classFile.exists()) {
			cache.origin = classFile;
			InputStream in = new FileInputStream(classFile);
			try {
				return loadBytesFromStream(in, (int) classFile.length());
			} finally {
				in.close();
			}
		} else {
			// Not found
			return null;
		}
	}

	/**
	 * Tries to load the class from a zip file.
	 *
	 * @param file The zipfile that contains classes.
	 * @param name The classname
	 * @param cache The cache entry to set the file if successful.
	 */
	private byte[] loadClassFromZipfile(File file, String name, ClassCacheEntry cache) throws IOException
	{
		// Translate class name to file name
		String classFileName = name.replace('.', '/') + ".class";

		//System.out.print(file.getName() + " loading: "+classFileName);
		ZipFile zipfile=null;

		try 
		{
			zipfile = new ZipFile(file);
			ZipEntry entry = zipfile.getEntry(classFileName);
			if (entry != null) 
			{
				cache.origin = file;
				//System.out.println("... OK");
				return loadBytesFromStream(zipfile.getInputStream(entry),
						(int) entry.getSize());
			} 
			else 
			{
				//System.out.println(" !!!");
				// Not found
				return null;
			}
		} 
		catch(java.lang.OutOfMemoryError ome)
		{
			//System.gc();
			try{Thread.sleep(5000);} catch(Exception e){}
			System.err.println("ERROR processing zip: " + file.getAbsolutePath() + " entry:" + classFileName);
			ome.printStackTrace();			
		}
		catch(Throwable t)
		{
			System.err.println("ERROR processing zip: " + file.getAbsolutePath() + " entry:" + classFileName);
			t.printStackTrace();
		}
		finally 
		{
			if(zipfile!=null) zipfile.close();
		}
		return null;
	}


	/**
	 * Loads all the bytes of an InputStream.
	 */
	private byte[] loadBytesFromStream(InputStream in, int length)
			throws IOException
	{
		byte[] buf = new byte[length];
		int nRead, count = 0;

		while ((length > 0) && ((nRead = in.read(buf,count,length)) != -1))
		{
			count += nRead;
			length -= nRead;
		}

		return buf;
	}

	/**
	 * Read until end of stream
	 * @param in
	 * @return
	 * @throws IOException
	 */
	/*private byte[] loadBytesFromStream(InputStream in) throws IOException
	{
		int iBufSize = 2048;
		byte[] buf = new byte[iBufSize];
		int nRead, count = 0;
		ByteArrayOutputStream baos = new ByteArrayOutputStream(iBufSize);

		while(((nRead = in.read(buf)) != -1))
		{
			count += nRead;
			//length -= nRead;
			baos.write(buf, 0, nRead);
		}

		return baos.toByteArray();
	}*/

	/**
	 * Get an InputStream on a given resource.  Will return null if no
	 * resource with this name is found.
	 * <p>
	 * The JServClassLoader translate the resource's name to a file
	 * or a zip entry. It looks for the resource in all its repository
	 * entry.
	 *
	 * @see     java.lang.Class#getResourceAsStream(String)
	 * @param   name    the name of the resource, to be used as is.
	 * @return  an InputStream on the resource, or null if not found.
	 */
	public InputStream getResourceAsStream(String name) 
	{
		// Try to load it from the system class
		InputStream s = getSystemResourceAsStream(name);
		if (s == null) 
		{
			// Try to find it from every repository
			Enumeration<File> repEnum = repository.elements();
			while (repEnum.hasMoreElements()) 
			{
				File file = repEnum.nextElement();
				if (file.isDirectory()) 
				{
					s = loadResourceFromDirectory(file, name);
				}
				else 
				{
					if(name.endsWith(".initArgs")) 
					{
						File dir = new File(file.getParent());
						s = loadResourceFromDirectory(dir, name);
					} 
					else 
					{
						s = loadResourceFromZipfile(file, name);
					}
				}                
				if (s != null) break;                
			}
		}

		return s;
	}

	/**
	 * Loads resource from a directory.
	 */
	private InputStream loadResourceFromDirectory(File dir, String name) {
		// Name of resources are always separated by /
		String fileName = name.replace('/', File.separatorChar);
		File resFile = new File(dir, fileName);

		if (resFile.exists()) 
		{
			try 
			{
				return new FileInputStream(resFile);
			} 
			catch (FileNotFoundException shouldnothappen) 
			{
				return null;
			}
		} 
		else 
		{
			return null;
		}
	}

	/**
	 * Loads resource from a zip file
	 */
	private InputStream loadResourceFromZipfile(File file, String name) 
	{
		ZipFile zipfile = null; 

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
			ome.printStackTrace();
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

	/**
	 * Find a resource with a given name.  The return is a URL to the
	 * resource. Doing a getContent() on the URL may return an Image,
	 * an AudioClip,or an InputStream.
	 * <p>
	 * This classloader looks for the resource only in the directory
	 * repository for this resource.
	 *
	 * @param   name    the name of the resource, to be used as is.
	 * @return  an URL on the resource, or null if not found.
	 */
	public URL getResource(String name) {

		// First ask the primordial class loader to fetch it from the classpath
		URL u = getSystemResource(name);
		if (u != null) 
		{
			return u;
		}

		if (name == null) 
		{
			return null;
		}

		// We got here so we have to look for the resource in our list of repository elements
		Enumeration<File> repEnum = repository.elements();
		while (repEnum.hasMoreElements()) 
		{
			File file = repEnum.nextElement();
			// Construct a file://-URL if the repository is a directory
			if (file.isDirectory()) 
			{
				String fileName = name.replace('/', File.separatorChar);
				File resFile = new File(file, fileName);
				if (resFile.exists()) {
					// Build a file:// URL form the file name
					try {
						return new URL("file", null, resFile.getAbsolutePath());
					} catch(java.net.MalformedURLException badurl) {
						badurl.printStackTrace();
						return null;
					}
				}
			}
			else 
			{
				// a jar:-URL *could* change even between minor releases, but
				// didn't between JVM's 1.1.6 and 1.3beta. Tested on JVM's from
				// IBM, Blackdown, Microsoft, Sun @ Windows and Sun @Solaris
				try {
					ZipFile zf = new ZipFile(file.getAbsolutePath());
					ZipEntry ze = zf.getEntry(name);

					if (ze != null) {
						try {
							return new URL("jar:file:" + file.getAbsolutePath() + "!/" + name);
						} catch(java.net.MalformedURLException badurl) {
							badurl.printStackTrace();
							return null;
						}
					}
				} catch (IOException ioe) {
					ioe.printStackTrace();
					return null;
				}
			}
		}

		// Not found
		return null;
	}
}


