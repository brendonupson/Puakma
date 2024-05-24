/* ***************************************************************
ActionRunner.java
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
package puakma.system;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.action.SharedActionClassLoader;
import puakma.addin.http.document.HTMLDocument;
import puakma.error.ErrorDetect;
import puakma.util.Util;

/**
 * This is the skeleton class used to run a user programmer defined action.
 * Actions should extend this class
 * Here is a sample of how it is called by HTTPRequestManager:
 * <br>
 * <br>
 * <code>
 * object = (aClassLoader.loadClass(sActionClassName)).newInstance();
 * ActionRunner act = (ActionRunner)object;
 * act.init(...);
 * String sRedirect = act.execute();
 * </code>
 */
public class ActionRunner implements ActionRunnerInterface,ErrorDetect
{
	protected HTTPSessionContext pSession;
	protected SystemContext pSystem;
	protected String ApplicationGroup;
	protected String Application;
	protected HTMLDocument ActionDocument; //careful may be null!!  
	//private byte[] byteBuffer=null;
	private String ContentType="text/html";
	private boolean m_bShouldQuit=false;
	//private final int CHUNK_SIZE=1024; //increase buffer by this amount when we run out of space
	private int m_iBufferSize=-1;
	private boolean m_bHasStreamedData=false; 
	private ByteArrayOutputStream m_baos = new ByteArrayOutputStream(1024);

	public ActionRunner(){}

	/**
	 * Used by the action to write data directly to the browser.
	 * @param sData the String to write to the buffer
	 */

	public void write(String sData)
	{    
		if(sData==null) return;

		try 
		{
			byte bufData[] = Util.utf8FromString(sData);
			m_baos.write(bufData);
		}
		catch(Exception e) 
		{
			System.err.println("ActionRunner.write() " + e.toString());
		}
	}
	/*
	public void write(String sData)
	{    
		if(sData==null) return;

		byte bufData[] = Util.utf8FromString(sData);
		if(byteBuffer==null) 
		{
			int iSize = CHUNK_SIZE;
			if(bufData.length>CHUNK_SIZE) iSize = bufData.length+CHUNK_SIZE; //make bigger as we'll probably be writing more
			byteBuffer = new byte[iSize];
			m_iBufferSize = bufData.length;
			//System.out.println("bufData.length="+bufData.length + " byteBuffer.length="+byteBuffer.length);
			System.arraycopy(bufData, 0, byteBuffer, 0, m_iBufferSize);
			return;
		}

		if((byteBuffer.length-m_iBufferSize)>bufData.length) //we have enough room in the existing buffer
		{        
			System.arraycopy(bufData, 0, byteBuffer, m_iBufferSize, bufData.length);
			m_iBufferSize += bufData.length;
			return;
		}

		//if we got here, there is not enough room in the buffer
		byte bufTemp[] = new byte[m_iBufferSize];
		System.arraycopy(byteBuffer, 0, bufTemp, 0, m_iBufferSize);
		int iSize = m_iBufferSize+CHUNK_SIZE;
		if(bufData.length>CHUNK_SIZE) iSize = m_iBufferSize+ bufData.length +CHUNK_SIZE; //make bigger as we'll probably be writing more
		byteBuffer = new byte[iSize];
		//copy the temp back to the original
		System.arraycopy(bufTemp, 0, byteBuffer, 0, m_iBufferSize);
		//now add the new chunk of data
		System.arraycopy(bufData, 0, byteBuffer, m_iBufferSize, bufData.length);
		m_iBufferSize += bufData.length;
	}*/

	/**
	 * Used by the agent to write data directly to the browser with a trailing CRLF.
	 * @param sData the String to write to the buffer
	 */
	public void writeln(String sData)
	{
		if(sData==null) return;
		write(sData+"\r\n");
	}


	/**
	 * Used to replace the entire contents of the buffer
	 * @param newBuffer
	 */
	public void setBuffer(byte[] newBuffer)
	{
		try
		{
			m_baos.reset();
			if(newBuffer!=null)
			{
				m_baos.write(newBuffer);
			}
		}
		catch(Exception e)
		{
			System.err.println("ActionRunner.setBuffer() " + e.toString());
		}
	}
	/*
	public void setBuffer(byte[] newBuffer)
	{
		if(newBuffer!=null)
		{			
			//BJU 2007-04-18 don't copy the block because very large objects (eg >120MB) waste huge slabs of memory
			byteBuffer = newBuffer;
			m_iBufferSize = newBuffer.length;
		}
		else
			byteBuffer = null;
	}
	 */

	/**
	 * Used by the caller to get the contents of the buffer. If sbOut is not null
	 * then this value should be streamed straight to the browser
	 * @return the internal buffer
	 */
	public byte[] getByteBuffer()
	{
		if(m_baos.size()<1) return null;
		return m_baos.toByteArray();
	}
	/*
	public byte[] getByteBuffer()
	{
		//the OR is a safety check....
		if(byteBuffer==null || m_iBufferSize<1) return null;

		//TODO this is also inefficient for large byte buffers. Why did I create m_iBufferSize?!
		byte bufReturn[] = new byte[m_iBufferSize];
		System.arraycopy(byteBuffer, 0, bufReturn, 0, m_iBufferSize);
		return bufReturn;
		
		//return Arrays.copyOfRange(byteBuffer, 0, m_iBufferSize);
	}*/

	/**
	 * Used by the caller to get the contents of the buffer. If sbOut is not null
	 * then this value should be streamed straight to the browser.
	 * @return the internal byte buffer into a utf-8 StringBuilder
	 */
	public StringBuilder getStringBuilder()
	{	
		byte buf[] = getByteBuffer();
		StringBuilder sbOut = new StringBuilder();
		sbOut.append(Util.stringFromUTF8(buf));
		return sbOut;
	}
	/*
	public StringBuilder getStringBuilder()
	{		
		if(byteBuffer==null) return null;
		StringBuilder sbOut = new StringBuilder(byteBuffer.length*2);
		sbOut.append(Util.stringFromUTF8(byteBuffer));
		return sbOut;
	}
	*/

	/**
	 * Called by the HTTPRequestManager prior to execute(). This is used to set up the class
	 * ready to run. This method should not be called by your action code.
	 * @param paramSession the current user session
	 * @param paramDoc the current document
	 * @param paramGroup application group
	 * @param paramApp application name
	 * @see puakma.addin.http.HTTPRequestManager
	 */
	public final void init(HTTPSessionContext paramSession, HTMLDocument paramDoc, String paramGroup, String paramApp)
	{
		pSession = paramSession;
		pSystem = pSession.getSystemContext();
		ActionDocument = paramDoc;
		ApplicationGroup = paramGroup;
		Application = paramApp;        
	}

	/**
	 *  All the programmer's action code goes here. Override this method.
	 * @return the URL to redirect the client to. Use null or "" if you do not want a redirect.
	 * Supports both "/group/app.pma" and http://server/group/app.html" syntax.
	 * 
	 */
	public String execute()
	{
		return getDBURL();
	}

	/**
	 * Determines if this action has streamed data driectly to the browser
	 * @return true if the programmer has called {@link #streamToClient(byte[])} to send data directly 
	 * to the browser user. 
	 */
	public boolean hasStreamed()
	{
		return m_bHasStreamedData;
	}

	/**
	 * A simple way for the programmer to generate the URI to this application
	 * @return a String representation of the URI, eg "/group/app.pma"
	 */
	public String getDBURL()
	{
		StringBuilder sbOut = new StringBuilder(30);
		if(ApplicationGroup!=null && ApplicationGroup.length()>0)
		{        
			sbOut.append('/');
			sbOut.append(ApplicationGroup);
		}       
		sbOut.append('/');
		sbOut.append(Application);
		sbOut.append(System.getProperty(pmaSystem.PUAKMA_FILEEXT_SYSTEMKEY));

		return sbOut.toString();
	}

	/**
	 * Gets the content-type that this action has been set with
	 * @return the type of content this action created.
	 */
	public String getContentType()
	{
		if(ContentType==null) return "text/html";
		return ContentType;
	}

	/**
	 * Set the content type of the internal buffer, eg "text/xml"
	 * @param sNewType
	 */
	public synchronized void setContentType(String sNewType)
	{
		ContentType = sNewType;
	}

	/**
	 * For long running actions this method should be called periodically to determine
	 * if the action should stop.
	 * @return true if the action should stop
	 */
	public final boolean shouldQuit()
	{
		return m_bShouldQuit;
	}

	/**
	 * Called by HTTPRequestManager if the server has been quit. A programmer's action 
	 * code should not call this method directly.
	 */
	public final synchronized void requestQuit()
	{
		m_bShouldQuit=true;
	}

	/**
	 * Loads a block of bytes defining a class into the JVM classloader cache
	 * @param classData
	 * @param sClassName
	 * @param sDesignName
	 * @return true if the class was loaded
	 */
	public boolean loadClassData(byte[] classData, String sClassName, String sDesignName)
	{
		ClassLoader cl = this.getClass().getClassLoader();
		if(cl instanceof SharedActionClassLoader)
		{
			//ActionClassLoader acl = (ActionClassLoader)cl;
			SharedActionClassLoader aLoader = pSystem.getActionClassLoader(ActionDocument.rPath); //, DesignElement.DESIGN_TYPE_ACTION);
			aLoader.loadClassData(classData, sClassName, sDesignName);
			return true;
		}

		return false;
	}

	/**
	 * @return the source of the error, usually the path to the application if not overridden by
	 * the programmer
	 */
	public String getErrorSource()
	{
		if(ActionDocument==null) return this.getClass().getName();      
		return ActionDocument.rPath.getPathToApplication() + '/'+ this.getClass().getName();
	}

	/**
	 * Get the user that caused the current error. 
	 * @return the current user if the programmer has not overridden this method
	 */
	public String getErrorUser()
	{
		if(pSession==null)
			return pmaSystem.SYSTEM_ACCOUNT;
		else
			return pSession.getUserName();
	}

	/**
	 * Send bytes directly to the browser
	 * @param buf the block of bytes to send to the client now. The bytes will be flushed
	 * so to improve performance this method should be called with as full a buffer as possible
	 */
	public void streamToClient(byte buf[]) throws IOException 
	{
		m_bHasStreamedData = true;
		pSession.streamToClient(buf);
	}

	/**
	 * Get a handle to the http output stream. Just calling this method tells the server you are 
	 * taking care of the reply
	 * @return
	 */
	public OutputStream getOutputStream()
	{
		m_bHasStreamedData = true;
		return pSession.getOutputStream();
	}

}//class