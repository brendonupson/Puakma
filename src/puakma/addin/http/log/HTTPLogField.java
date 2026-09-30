/** ***************************************************************
HTTPLogField.java
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

package puakma.addin.http.log;

import java.text.SimpleDateFormat;


public class HTTPLogField 
{
    private final static int TYPE_TEXT = 0;
    private final static int TYPE_OTHER = -1;
    private final static String DEFAULT_DATE_FORMAT = "dd/MMM/yyyy:HH:mm:ss ZZZ"; //01/Jan/2004:00:23:15 +2300
    private String m_sSpecifier="";
    private int m_iType=TYPE_TEXT;
    
    //pre-computed from the specifier, so getValue() does no parsing for each request
    private char m_cType = 0x00;
    private String m_sMiddle = "";
    private String m_sTrailer = "";
    private String m_sVariable = ""; //the {xxx} part
    private SimpleDateFormat m_sdf = null; //for %t, guarded by synchronized(this)
    
    
    /** Creates a new instance of HTTPLogField */
    public HTTPLogField(String sSpecifier) 
    {        
        if(sSpecifier!=null) m_sSpecifier = sSpecifier;
        parseSpecifier();
    }
    
    private void parseSpecifier()
    {
        int iPercentPos = m_sSpecifier.indexOf('%');
        int iDoublePercentPos = m_sSpecifier.indexOf("%%");
        if(iPercentPos>=0 && !(iPercentPos==iDoublePercentPos)) m_iType=TYPE_OTHER;
        m_sSpecifier = m_sSpecifier.replace("%%", "%");
        if(m_iType!=TYPE_OTHER) return;
        
        //m_sSpecifier will start with a % eg "%v ". Find the type letter, skipping any {xxx} and modifiers like > 
        int iPos = getEndOfSpecifier();
        String sSpecifier = m_sSpecifier;
        if(iPos>=0)
        {            
            sSpecifier = m_sSpecifier.substring(0, iPos+1);
            m_sTrailer = m_sSpecifier.substring(iPos+1);
        }
        int iLen = sSpecifier.length();
        m_sMiddle = sSpecifier;
        if(iLen>1)
        {
            m_cType = sSpecifier.charAt(iLen-1);
            m_sMiddle = sSpecifier.substring(1, iLen-1);
        }
        
        int iStart = m_sSpecifier.indexOf('{');
        int iEnd = m_sSpecifier.indexOf('}');
        if(iStart>=0 && iEnd>iStart) m_sVariable = m_sSpecifier.substring(iStart+1, iEnd);
        
        if(m_cType=='t')
        {
            String sFormat = m_sVariable.length()>0 ? m_sVariable : DEFAULT_DATE_FORMAT;
            try{ m_sdf = new SimpleDateFormat(sFormat); }
            catch(IllegalArgumentException e){ m_sdf = new SimpleDateFormat(DEFAULT_DATE_FORMAT); }
        }
    }
    
    /**
     * Returns the text for this field. Safe to call from any thread.
     */
    public String getValue(HTTPLogEntry le)
    {
        if(!le.shouldLog()) return "";
        
        switch(m_iType)
        {
            case TYPE_TEXT:
                return m_sSpecifier;
            case TYPE_OTHER:
                return getOtherValue(le);
        }
        
        return "";
    }
    
    
    
    private String getOtherValue(HTTPLogEntry le)
    {
        String sReturn="";
        long lBytes = 0;
        switch(m_cType)
        {
            case 'a':
                sReturn = le.getClientIP();
                break;
            case 'A':
                sReturn = le.getLocalIP();
                break;
            case 'h':
                sReturn = le.getClientHostName();
                break;
            case 'f':
                sReturn = le.getFileName();
                break;
            case 'l':
                sReturn = "-"; //not implemented
                break;
            case 'u': //Hmmm we support canonival names :-(
                sReturn = le.getUserNameNoSpaces();
                break;
            case 'B':
                lBytes = le.getResponseBytes();
                sReturn = String.valueOf(lBytes);
                break;
            case 'b':
                lBytes = le.getResponseBytes();
                if(lBytes==0) sReturn="-";
                else sReturn = String.valueOf(lBytes);
                break;
            case 'D':
                sReturn = String.valueOf(le.getServeMS());
                break;
            case 'T': 
                sReturn = String.valueOf(le.getServeMS()/1000);//in seconds
                break;
            case 'v':
            case 'V':
                sReturn = le.getRequestedServerName();
                break;
            case 's':
                sReturn = String.valueOf(le.getReturnStatus());
                break;
            case 'r':
                sReturn = le.getRequestLine();
                break;
            case 'H':
                sReturn = le.getRequestProtocol();
                break;
            case 'm':
                sReturn = le.getRequestMethod();
                break;
            case 'i':
                sReturn = le.getRequestHeader(m_sVariable);
                break;
            case 'o':
                sReturn = le.getReplyHeader(m_sVariable);
                break;
            case 'e':
                sReturn = getEnvironmentVar(m_sVariable);
                break;
            case 'U':
                sReturn = le.getPathToDesign();
                break;
            case 'q':
                sReturn = le.getQueryString();
                break;
            case 'C':
                sReturn = le.getCookieValue(m_sVariable);
                break;
            case 'P':
                sReturn = Thread.currentThread().getName();//supposed to return a pid... not in Java!
                break;
            case 't':
                synchronized(this)
                {
                    sReturn = "["+m_sdf.format(le.getRequestDate()) + "]";
                }
                break;                
            case 'X':
                String sConn = le.getConnectionState();
                sReturn = "-";//close Use X for aborted                
                if(sConn!=null && sConn.equalsIgnoreCase("keep-alive")) sReturn = "+";
                break;
            case 'p':
                sReturn = String.valueOf(le.getServerPort());
                break;
            default:
                sReturn = m_sMiddle;
        };
        
        if(sReturn==null) sReturn="";
        return sanitize(sReturn) + m_sTrailer;
    }
    
    /**
     * Client supplied values (request line, headers, cookies...) must not be able to add 
     * lines to the log, so any control characters are replaced with a space
     */
    private static String sanitize(String s)
    {
        int iLen = s.length();
        for(int i=0; i<iLen; i++)
        {
            char c = s.charAt(i);
            if(c<32 && c!='\t' || c==127)
            {
                char[] ca = s.toCharArray();
                for(int k=i; k<iLen; k++)
                {
                    if(ca[k]<32 && ca[k]!='\t' || ca[k]==127) ca[k] = ' ';
                }
                return new String(ca);
            }
        }
        return s;
    }
    
    /**
     * There's no way in 1.4 to get an OS env var. without some pain
     */
    private String getEnvironmentVar(String sEnvName)
    {
        if(sEnvName==null || sEnvName.length()==0) return null;
        return System.getProperty(sEnvName);
    }
    
    /**
     * The index of the type letter of the specifier, eg "%>s " is 2, "%{Referer}i\" " is 10. 
     * Skips over any {name}. Returns -1 if there isn't one
     */
    private int getEndOfSpecifier()
    {
        int iLen = m_sSpecifier.length();
        int i = 1; //skip the %
        while(i<iLen)
        {
            char c = m_sSpecifier.charAt(i);
            if(c=='{')
            {
                int iClose = m_sSpecifier.indexOf('}', i);
                if(iClose<0) return -1;
                i = iClose;
            }
            else if((c>='a'&&c<='z') || (c>='A'&&c<='Z')) return i;
            i++;
        }
        return -1;
    }
    
    
}//class
