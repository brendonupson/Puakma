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


public class HTTPLogField 
{
    private final static int TYPE_TEXT = 0;
    private final static int TYPE_OTHER = -1;
    private String m_sSpecifier="";
    private int m_iType=TYPE_TEXT;
    
    
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
        m_sSpecifier = m_sSpecifier.replaceAll("%%", "%");
    }
    
    /**
     *
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
    
    
    
    /**
     * m_sSpecifier will start with a % eg "%v "
     */
    private String getOtherValue(HTTPLogEntry le)
    {
        //System.out.println("|"+m_sSpecifier+"|");
        int iPos = getEndOfSpecifier();//m_sSpecifier.indexOf(' ');
        String sSpecifier = m_sSpecifier;
        String sTrailer="";
        if(iPos>=0)
        {            
            sSpecifier = m_sSpecifier.substring(0, iPos+1);
            sTrailer = m_sSpecifier.substring(iPos+1);
        }
        int iLen = sSpecifier.length();
        char cType = 0x00;
        String sMiddle = sSpecifier;
        if(iLen>1)
        {
            cType = sSpecifier.charAt(sSpecifier.length()-1);
            if(iLen>1) sMiddle = sSpecifier.substring(1, sSpecifier.length()-1);
        }
        String sReturn="";
        long lBytes = 0;
        switch(cType)
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
                long lMS = le.getServeMS();
                sReturn = String.valueOf(lMS);
                break;
            case 'T': 
                lMS = le.getServeMS();
                sReturn = String.valueOf(lMS/1000);//in seconds
                break;
            case 'v':
            case 'V':
                sReturn = le.getRequestedServerName();
                break;
            case 's':
                int iReturn = le.getReturnStatus();
                sReturn = String.valueOf(iReturn);
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
                String sHeaderName = getVariable("");                
                sReturn = le.getRequestHeader(sHeaderName);
                //System.out.println("header="+sHeaderName + " return=["+sReturn+"]");
                break;
            case 'o':
                String sReplyHeaderName = getVariable("");
                sReturn = le.getReplyHeader(sReplyHeaderName);
                break;
            case 'e':
                String sEnvName = getVariable("");
                sReturn = getEnvironmentVar(sEnvName);
                break;
            case 'U':
                sReturn = le.getPathToDesign();
                break;
            case 'q':
                sReturn = le.getQueryString();
                break;
            case 'C':
                String sCookieName = getVariable("");
                sReturn = le.getCookieValue(sCookieName);
                break;
            case 'P':
                sReturn = Thread.currentThread().getName();//supposed to return a pid... not in Java!
                break;
            case 't':
                java.util.Date dt = le.getRequestDate();
                String sDateFormat = getVariable("dd/MMM/yyyy:HH:mm:ss ZZZ"); //01/Jan/2004:00:23:15 +2300
                sReturn = "["+puakma.util.Util.formatDate(dt, sDateFormat) + "]";
                break;                
            case 'X':
                String sConn = le.getConnectionState();
                sReturn = "-";//close Use X for aborted                
                if(sConn.equalsIgnoreCase("keep-alive")) sReturn = "+";
                break;
            case 'p':
                int iPort = le.getServerPort();                
                sReturn = String.valueOf(iPort);
                break;
            default:
                sReturn = sMiddle;
        };
        
        if(sReturn==null) sReturn="";
        return sReturn+sTrailer;
    }
    
    /**
     * returns the {variable} from the format string, eg: "%{dd/MM/yyyy}t" will return "dd/MM/yyyy"
     */
    private String getVariable(String sDefault)
    {
        int iStart = m_sSpecifier.indexOf('{');
        int iEnd = m_sSpecifier.indexOf('}');
        if(iStart<0 || iEnd<0 || iEnd<=iStart) return sDefault;
        iStart++;
        return m_sSpecifier.substring(iStart, iEnd);
    }
    
    /**
     * There's no way in 1.4 to get an OS env var. without some pain
     */
    private String getEnvironmentVar(String sEnvName)
    {
        return System.getProperty(sEnvName);
    }
    
    /**
     *
     */
    private int getEndOfSpecifier()
    {
        int iEnd = m_sSpecifier.length()-1;
        if(iEnd<=0) return -1;
        boolean bLooking=true;
        while(bLooking)
        {
            if(iEnd<0) break;
            char c = m_sSpecifier.charAt(iEnd);            
            if((c>='a'&&c<='z') || (c>='A'&&c<='Z')) bLooking=false;
            else iEnd--;
        }        
        return iEnd;
    }
    
    
}//class
