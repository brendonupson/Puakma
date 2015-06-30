/** ***************************************************************
HTTPHeaderProcessor.java
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
 * HTTPHeaderProcessor.java
 *
 * Created on 16 November 2003, 19:07
 *
 * We are in the util package so we can include in both booster and http branches
 */

package puakma.util;

import java.util.ArrayList;

import puakma.addin.pmaAddIn;
import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.document.HTMLDocument;
import puakma.error.ErrorDetect;
import puakma.system.Document;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;

/**
 *
 * @author  bupson
 */
public class HTTPHeaderProcessor implements ErrorDetect
{
    //make available to subclasses
    protected SessionContext m_pSession;
    protected SystemContext m_pSystem;
    protected String m_sMethod;
    protected String m_sURI;
    protected String m_sHTTPVersion;
    protected ArrayList m_alHeaders;
    protected String m_sPeerIPAddress="127.0.0.1";
    
    //for returning data to the client
    protected ArrayList m_alReturnHeaders = new ArrayList();
    protected byte[] m_bufReturn;
    protected int m_iReturnCode=-1;
    protected String m_sReturnMessage;  
    protected String m_sReturnMimeType="text/html";
    
    private boolean m_bShouldReplyToClient=false;
    private boolean m_bIsSecureConnection=false;
    private ByteStreamReader m_in;
    private pmaAddIn m_addin;
    
    
    /** 
     * Initializes the processor.
     */
   /* public final void init(SystemContext pSystem, SessionContext pSession, String sMethod, String sURI, String sHTTPVersion, ArrayList alHeaders, boolean bSecureConnection, String sPeerIP, ByteStreamReader in)
    {
        m_pSession = pSession;
        m_pSystem = pSystem;
        m_sMethod = sMethod;
        m_sURI = sURI;
        m_sHTTPVersion = sHTTPVersion;
        m_alHeaders = alHeaders;
        m_bIsSecureConnection = bSecureConnection;
        m_sPeerIPAddress = sPeerIP;
        m_in = in;
    }*/
    
    public final void init(pmaAddIn addin, SystemContext pSystem, SessionContext pSession, String sMethod, String sURI, String sHTTPVersion, ArrayList alHeaders, boolean bSecureConnection, String sPeerIP, ByteStreamReader in)
    {
    	m_addin = addin;
        m_pSession = pSession;
        m_pSystem = pSystem;
        m_sMethod = sMethod;
        m_sURI = sURI;
        m_sHTTPVersion = sHTTPVersion;
        m_alHeaders = alHeaders;
        m_bIsSecureConnection = bSecureConnection;
        m_sPeerIPAddress = sPeerIP;
        m_in = in;
    }
    
    /**
     * Enter your request processing code here.
     * @return true if you do not want any other HeaderProcessors to operate on this request
     */
    public boolean execute()
    {
        //your code here
        return false;
    }
    
    public final String getHTTPMethod()
    {
        return m_sMethod;
    }
    
    public final String getHTTPURI()
    {
        return m_sURI;
    }
    
    public final String getHTTPVersion()
    {
        return m_sHTTPVersion;
    }
    
    
    
    /**
     *
     */
    public String getErrorSource() 
    {
        return getClass().getName();
    }    
       
    /**
     *
     */
    public String getErrorUser() 
    {
        if(m_pSession==null) return pmaSystem.SYSTEM_ACCOUNT;
            
        return m_pSession.getUserName();        
    }
    
    /**
     * Get the value of an item in the header
     */
    protected String getHeaderValue(String sHeader)
    {
        return puakma.util.Util.getMIMELine(m_alHeaders, sHeader);
    }
    
    /**
     * Get the values of an item in the header, eg for Cookie: where
     * there may be multiple
     */
    protected String[] getAllHeaderValues(String sHeader)
    {        
        return puakma.util.Util.getAllMIMELines(m_alHeaders, sHeader);
    }
    /**
     * Replace an item in the header
     */
    protected void replaceHeaderValue(String sHeader, String sValue)
    {
        String sFind = sHeader.toLowerCase()+':';
        for(int i=0; i<m_alHeaders.size(); i++)
        {
            String s = (String)m_alHeaders.get(i);
            if(s.toLowerCase().startsWith(sFind))
            {
                m_alHeaders.remove(i);
                m_alHeaders.add(sHeader+": "+sValue);
                return;
            }
        }
    }
    
    
    public void createStatistic(String sStatisticKey, int iCaptureType, int iMaxPeriodsHistory, boolean bForceCreation)
	{
    	m_addin.createStatistic(sStatisticKey, iCaptureType, iMaxPeriodsHistory, bForceCreation);
	}
    
    public void incrementStatistic(String sStatisticKey, double dIncrementBy)
	{
    	m_addin.incrementStatistic(sStatisticKey, dIncrementBy);
	}

    /**
     * Determines if this connection is over https or not
     */
    public boolean isSecureConnection()    
    {
    	
        return m_bIsSecureConnection;
    }
    
    
    /**
     * Called by the HTTP server to determine if this request should respond directly to the client
     */
    public boolean shouldReplyToClient()
    {
        return m_bShouldReplyToClient;
    }
    
    
    /**
     * Set the flag to tell the HTTP server to reply to the client
     */
    public void setShouldReplyToClient(boolean bShould)
    {
        m_bShouldReplyToClient = bShould;
    }
    
    
    public int getReturnCode()
    {
        return m_iReturnCode;
    }
    
    public String getReturnMimeType()
    {
        return m_sReturnMimeType;
    }
    
    public ArrayList getReturnHeaders()
    {
        return m_alReturnHeaders;
    }
    
    public byte[] getReturnBuffer()
    {
        return m_bufReturn;
    }
    
    public String getReturnMessage()
    {
        return m_sReturnMessage;
    }
    
    /**
     * Get the http payload, programmer MUST reply to the client because the inputstream is all soaked up
     */
    public HTMLDocument getDocument(String sReadAsCharSet)
    {
        this.setShouldReplyToClient(true);
        RequestPath rPath = new RequestPath(m_sURI);
        String sBoundary="";
        int iLength=0;
        String sContentLength = getHeaderValue("Content-Length");
        try{ iLength = Integer.parseInt(sContentLength); }catch(Exception e){}
        
        HTTPSessionContext sess = null;
        if(this.m_pSession==null) 
            sess = new HTTPSessionContext(m_pSystem, m_pSystem.createSystemSession("BOOSTERHeaderProcessor"), rPath);
        else
            sess = new HTTPSessionContext(m_pSystem, m_pSession, rPath);
        HTMLDocument doc;
        String sContentType = getParsedContentType();
        if(sContentType.equals(Document.CONTENT_MULTI))
            doc = new HTMLDocument(m_pSystem, sess, rPath.DesignElementName, m_in, sContentType, sBoundary, iLength, sReadAsCharSet);
        else
            doc = new HTMLDocument(m_pSystem, sess, rPath.DesignElementName, m_in, sContentType, iLength, sReadAsCharSet);
          if(!doc.isDocumentCreatedOK()) return null;
        // = new HTMLDocument(m_pSystem, m_pSession, "pagename", m_in, "content/type", len, "charset");
        
        return doc;
    }
    
    /**
     * gets the part before the semicolon
     *eg "text/html; charset=utf-8" will return "text/html"
     */
    private String getParsedContentType()
     {
        String sContentString = getHeaderValue("Content-Type");
        int iPos = sContentString.indexOf(';');
        if(iPos>=0) return sContentString.substring(0, iPos);
        return sContentString;
     }

}
