/** ***************************************************************
URLReader.java
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


package puakma.util;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;


/**
 * This class is used to get or post HTTP data to/from a remote server.
 * URLReader ur = new URLReader("http://yourserver/yourapp.pma");
 * byte bufReply[] = sc.send(null, false);
 *
 * By default messages will not be compressed with gzip.
 * Session ID, username/password and Ltpa token info may be set
 */
public class URLReader
{    
    public final static String PUAKMA_SESSION_ID = "_pma_sess_id";
    public final static String PUAKMA_LTPATOKEN_ID = "LtpaToken";
    private String m_sURL;
    private String m_sSessionID=null; //for authentication
    private String m_sUserName=null; //for authentication
    private String m_sPassword=null; //for authentication
    private String m_sProxyHostPort=null; //for proxy eg "host:3129"
    private String m_sProxyUserName=null; //for proxy authentication
    private String m_sProxyPassword=null; //for proxy authentication
    private long m_lCallMS=-1;
    private boolean m_bCompressMessages = false;
    private int m_iSessionTimeOutMins = 1;
    private String m_sContentType=null;
    //private String m_sMethod = "GET";
    private String m_sLtpaTokenB64 = null;
    

    /**
     * Constructor
     * @param sURL
     */
    public URLReader(String sURL)
    {
      m_sURL = sURL;      
      //m_sMethod = sMethod;
    }

    

    /**
     * Test harness
     */
    public static void main(String[] args)
    {
        URLReader u = new URLReader("http://www.puakma.net/puakma.pma");
        //u.setUserNamePassword("bupson", "xxx");
        //u.setLtpaToken("AAECAzQ0NjAzODc5NDQ2MDhDRDlDTj1CcmVuZG9uIFVwc29uL089d2ViV2lzZf4mdgw/ncnkWqU7pVaU1uOmddLk");
        try
        {
            byte buf[] = u.send(null, false);
            System.out.println(new String(buf));
        }
        catch(Exception e)
        {
            System.out.println(e.toString());
            e.printStackTrace();
        }
        
        System.out.println("sess="+u.getSessionID());
        System.out.println("ltpa="+u.getLtpaToken());
    }
    
    

    /**
     * Calls the web service. The reply is cached in memory, so we assume that
     * there is enough room for the content-length
     * @param bufSend a block of bytes to POST to the remote host
     * @param bUseCache use cached responses if available
     * @return the result of the web service
     */
    public byte[] send(byte[] bufSend, boolean bUseCache) throws IOException
    {
        long lStart = System.currentTimeMillis();
        //int MAX_CHUNK = 20428;//20K
        URLConnection connection=null;
                
        //do proxy setup
        if(m_sProxyHostPort!=null)
        {                                    
            System.setProperty("http.proxyHost", m_sProxyHostPort) ;
            System.setProperty("http.proxyPort", "80");
            int iPos = m_sProxyHostPort.indexOf(':');
            if(iPos>0)
            {
                String sHost = m_sProxyHostPort.substring(0, iPos);
                String sPort = m_sProxyHostPort.substring(iPos+1);
                if(sPort.length()==0) sPort = "80";
                System.setProperty("http.proxyHost", sHost) ;
                System.setProperty("http.proxyPort", sPort);                
            }            
        }

        URL url = new URL(m_sURL);
        if(url.getProtocol().equalsIgnoreCase("https"))
        {                                
            try
            {
                SSLContext ctx = SSLContext.getInstance("SSL");            
                TrustManager tmArray[] = new TrustManager[1];
                tmArray[0] = new puakma.util.RelaxedTrustManager();          
                ctx.init(null, tmArray, null);

                SSLSocketFactory sf = ctx.getSocketFactory();
                HttpsURLConnection.setDefaultSSLSocketFactory(sf); 
                HttpsURLConnection.setDefaultHostnameVerifier(new puakma.util.RelaxedHostnameVerifier());
                HttpsURLConnection shurl = (HttpsURLConnection)url.openConnection();
                connection = (URLConnection)shurl; 
            }
            catch(Exception e)
            {
                throw new IOException("SSL Setup Failed: " + e.getMessage());
            }
        }
        else
        {
            connection = url.openConnection();            
        }                 
        
        //connection.setDoInput( true );
        //connection.setDoOutput( true );
        connection.setUseCaches(bUseCache);
        if(m_sProxyUserName!=null) connection.setRequestProperty( "Proxy-Authorization", "Basic "+hashUserNamePassword(m_sProxyUserName, m_sProxyPassword) );
        if(m_sUserName!=null) connection.setRequestProperty( "Authorization", hashUserNamePassword(m_sUserName, m_sPassword));
        if(m_sSessionID!=null) connection.setRequestProperty( "Cookie", PUAKMA_SESSION_ID+'='+ m_sSessionID);
        if(m_sLtpaTokenB64!=null) connection.setRequestProperty( "Cookie", PUAKMA_LTPATOKEN_ID + '='+ m_sLtpaTokenB64);
        //if(bufSend!=null) connection.setRequestProperty( "Content-Type", m_sContentType );        
        if(bufSend!=null) 
        {
            if(m_sContentType==null) m_sContentType = "www/unknown";
            connection.setRequestProperty( "Content-Type", m_sContentType );        
        }
        if(m_iSessionTimeOutMins>=0) connection.setRequestProperty( "Session-Timeout", ""+m_iSessionTimeOutMins );
        connection.setRequestProperty( "User-Agent", "Puakma/URLReader" );
        //connection.setRequestProperty( "SOAPAction", '\"' + m_sURL + '\"' );
        if(m_bCompressMessages)
        {
            connection.setRequestProperty( "Content-Encoding", "gzip" );
            connection.setRequestProperty( "Accept-Encoding", "gzip" );
        }
        
        
        
        if(bufSend!=null)
        {
            DataOutputStream output = new DataOutputStream(connection.getOutputStream() );
            if(m_bCompressMessages)
            {            
                output.write(puakma.util.Util.gzipBuffer(bufSend));
                //System.out.println("----------------> sent compressed"); //for debug
            }
            else
            {
                output.write(bufSend);
                //System.out.println("----------------> sent uncompressed"); //debug
            }
            output.close();
        }
        
        HttpURLConnection hc = (HttpURLConnection)connection;
        int iReplyCode = hc.getResponseCode();
        //System.out.println(iReplyCode + " "+hc.getResponseMessage() + "  Using proxy:"+hc.usingProxy());
        if(iReplyCode!=200)//(iReplyCode<200 || iReplyCode>=300)
        {            
            throw new IOException(iReplyCode + " " + hc.getResponseMessage());
        }

        //System.out.println(connection.getHeaderField(0));
        //CHECK FOR "HTTP/1.1 200 OK"
        DataInputStream input = new DataInputStream(connection.getInputStream() );
        //this piece of shenanigans is so we can interrogate the raw xml return if we need to
        int iLen = connection.getContentLength();
        String sEncoding = connection.getHeaderField("Content-Encoding"); 
        processSessionInfo(connection);
        m_sContentType = connection.getHeaderField("Content-Type");
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream(iLen);
        int iTotalRead=0;
        byte buf[] = new byte[8192]; //8K?
        while(iTotalRead<iLen)
        {
            int iRead = input.read(buf);
            iTotalRead += iRead;
            if(iRead>0)
            {
                baos.write(buf, 0, iRead);
            }   
            else
                try{Thread.sleep(10);}catch(Exception f){}
        }//while 
        input.close();
        byte bufReply[] = baos.toByteArray();         
        if(sEncoding!=null && sEncoding.equalsIgnoreCase("gzip"))
        {
            //System.out.println(iLen+" ----------------> gzip reply from server"); //debug
            bufReply = puakma.util.Util.ungzipBuffer(bufReply);
        }  
        m_lCallMS = System.currentTimeMillis() - lStart;
        return bufReply;
    }
    
    /**
     * Gets the Puakma session ID and ltpa token from the HTTP reply
     */
    private void processSessionInfo(URLConnection connection)
    {        
        int i=0;
        String sHeader = connection.getHeaderField(i);
        while(sHeader!=null)
        {
            
            String sKey = connection.getHeaderFieldKey(i);
            if(sKey!=null && sKey.equalsIgnoreCase("Set-Cookie"))
            {
                //this might be a session cookie....
                int iPos = sHeader.indexOf(PUAKMA_SESSION_ID);
                if(iPos>=0)
                {
                    //System.out.println(i + " " + sHeader + " --> " + sKey);                
                    sHeader = sHeader.substring(iPos + PUAKMA_SESSION_ID.length()+1);
                    iPos = sHeader.indexOf(';');
                    if(iPos>0) m_sSessionID = sHeader.substring(0, iPos);                    
                }
                else
                {
                    //check for ltpa token
                    iPos = sHeader.indexOf(PUAKMA_LTPATOKEN_ID);
                    if(iPos>=0)
                    {
                        //System.out.println(i + " " + sHeader + " --> " + sKey);                
                        sHeader = sHeader.substring(iPos + PUAKMA_LTPATOKEN_ID.length()+1);
                        iPos = sHeader.indexOf(';');
                        if(iPos>0) m_sLtpaTokenB64 = sHeader.substring(0, iPos);                        
                    }
                }
            }
            i++;
            sHeader = connection.getHeaderField(i);
        }        
        //System.out.println(" --> " + m_sSessionID);
        //m_sSessionID
    }

    /**
   * Sets the username and password in the http header
   * @param sUserName
   * @param sPassword
   */
  public String hashUserNamePassword(String sUserName, String sPassword)
  {
    if(sUserName==null || sPassword==null) return "";
    String sUserNamePassword = sUserName + ':' + sPassword;
    return Util.base64Encode(sUserNamePassword.getBytes());
  }


  
    /**
     * Set the session ID you want to use to access the service
     * @param paramSessID
     */
    public void setSessionID(String paramSessID)
    {
      m_sSessionID = paramSessID;
    }
    
    /**
     * Get the session ID that this object is using. May return null
     */
    public String getSessionID()
    {
      return m_sSessionID;
    }
    
    public String getLtpaToken()
    {
      return m_sLtpaTokenB64;
    }


    public void setLtpaToken(String sLtpaToken)
    {
        m_sLtpaTokenB64 = sLtpaToken;
    }
    /**
     *
     * @param sUserName
     * @param sPassword
     */
    public void setUserNamePassword(String sUserName, String sPassword)
    {
      if(sUserName==null || sPassword==null) return;
      m_sPassword = sPassword;
      m_sUserName = sUserName;
    }

    public void setProxyServer(String sHost)
    {
        m_sProxyHostPort = sHost;
    }
    
    /**
     * The account to use for proxy connections
     * @param sUserName
     * @param sPassword
     */
    public void setProxyUserNamePassword(String sUserName, String sPassword)
    {
      if(sUserName==null || sPassword==null) return;
      m_sProxyPassword = sPassword;
      m_sProxyUserName = sUserName;
    }

   

    
    /**
     * Set the method we will be invoking on the host
     * @param sNewURL
     */
    public synchronized void setURL(String sNewURL)
    {
        m_sURL = sNewURL;
     //   if(m_sService==null) m_sService = getServiceNameFromURL(m_sURL);        
    }
    
    public String getContentType()
    {
        return m_sContentType;
    }
    
    public synchronized void setContentType(String sContentType)
    {
        m_sContentType = sContentType;
    }



    /**
     * Gets the number of milliseconds the execute() function took to complete
     * @return -1 if it did not yet complete, otherwise a value greater than 0
     */
    public long getCallTimeMS()
    {
      return m_lCallMS;
    }

    public synchronized void setCompressMessages(boolean bNewVal)
    {
        m_bCompressMessages = bNewVal;
    }

}//end class
