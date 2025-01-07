/** ***************************************************************
HTTPLogger.java
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

import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;

/**
 * For logging HTTP requests to file
 */
public class HTTPLogger 
{    
	private String m_sLogFormat="%h %l %u %t \\\"%r\\\" %>s %b \\\"%{Referer}i\\\" \\\"%{User-agent}i\\\""; //NCSA extended/combined log format
	//"%v %h %l \\\"%u\\\" %t \\\"%r\\\" %>s %B";
	private ArrayList<HTTPLogField> m_arrParts = new ArrayList<HTTPLogField>();

	/**
	 *  Create a new logger     
	 */
	public HTTPLogger(String sLogFormat)
	{        
		if(sLogFormat!=null && sLogFormat.length()>0) m_sLogFormat = sLogFormat;
		parseLogFormatString();
	}

	public String getLogFormat()
	{
		return m_sLogFormat;
	}

	/*public static void main(String sArgs[])
    {
        HTTPLogger hLog = new HTTPLogger(null);
        HTTPLogEntry hLE = new HTTPLogEntry();
        hLog.logRequest(null, hLE);

    }*/



	/**
	 * Break the log format into a series of objects for dealing with each piece
	 */
	public void parseLogFormatString()
	{
		//look for % signs backup to last char before 
		String sLogRemainder = m_sLogFormat;
		//todo \xhh replacements
		sLogRemainder = sLogRemainder.replaceAll("\\\\r",  "\r");
		sLogRemainder = sLogRemainder.replaceAll("\\\\n",  "\n");
		sLogRemainder = sLogRemainder.replaceAll("\\\\t",  "\t");
		sLogRemainder = sLogRemainder.replaceAll("\\\\\"",  String.valueOf('"'));
		int iPos = sLogRemainder.indexOf('%');
		while(iPos>=0)
		{
			String sChunk = "";
			if(iPos==0)
			{
				sChunk = "%";
				sLogRemainder = sLogRemainder.substring(1);
				iPos = sLogRemainder.indexOf('%');
				if(iPos>=0)
				{
					if(iPos==0) //we found %%
					{
						HTTPLogField lf = new HTTPLogField("%%");
						m_arrParts.add(lf);
						sLogRemainder = sLogRemainder.substring(iPos+1);
					}
					else
					{
						sChunk += sLogRemainder.substring(0, iPos);
						HTTPLogField lf = new HTTPLogField(sChunk);
						m_arrParts.add(lf);
						sLogRemainder = sLogRemainder.substring(iPos);
					}

				}
				else //end of string
				{
					sChunk += sLogRemainder;
					HTTPLogField lf = new HTTPLogField(sChunk);
					m_arrParts.add(lf);
				}

			}
			else //doesn't start with a %
			{
				sChunk = sLogRemainder.substring(0, iPos);                
				sLogRemainder = sLogRemainder.substring(iPos);
				HTTPLogField lf = new HTTPLogField(sChunk);
				m_arrParts.add(lf);
			}                       

			iPos = sLogRemainder.indexOf('%');
		}
	}

	/**
	 * write the request to the output stream
	 */
	public void logRequest(OutputStream fout, HTTPLogEntry le) throws Exception
	{
		if(fout==null || le==null || !le.shouldLog()) return;
		StringBuilder sb = new StringBuilder(512);

		for(int i=0; i<m_arrParts.size(); i++)
		{
			HTTPLogField hLF = (HTTPLogField)m_arrParts.get(i);            
			sb.append(hLF.getValue(le));
		}
		//write sb to log
		//System.out.println(m_sLogFormat);
		//System.out.println(sb.toString());
		fout.write((sb.toString()+"\r\n").getBytes());
	}

	/**
	 *
	 */
	public void logRequestToRDB(Connection cx, HTTPLogEntry stat) throws Exception
	{
		if(cx==null || stat==null || !stat.shouldLog()) return;
		String sSQL = "INSERT INTO HTTPSTAT(IPAddress,RequestDate,UserAgent,Request,RequestReturnCode,ContentLength,ContentType,UserName,Host,TransactionMS,Referer,ServerName) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";

		PreparedStatement prepStmt = cx.prepareStatement(sSQL);
		/*
        CREATE TABLE HTTPSTAT(
        HTTPStatID INTEGER NOT NULL PRIMARY KEY AUTO_INCREMENT,
        IPAddress VARCHAR(20),
        RequestDate DATETIME,
        UserAgent VARCHAR(80),
        Request LONGTEXT,
        RequestReturnCode INTEGER,
        ContentLength INTEGER,
        ContentType VARCHAR(20),
        UserName VARCHAR(100),
        Host VARCHAR(100));
		 */

		//damn spammers send crap to the server. Grrrr!
		String sUserAgent = stat.getRequestHeader("User-Agent");
		if(sUserAgent!=null && sUserAgent.length()>120) sUserAgent = sUserAgent.substring(0, 119);

		prepStmt.setString(1, stat.getClientIP());
		prepStmt.setTimestamp(2, new Timestamp(stat.getRequestDate().getTime()));
		prepStmt.setString(3, sUserAgent);
		prepStmt.setString(4, stat.getRequestLine());
		prepStmt.setInt(5, stat.getReturnStatus());
		prepStmt.setLong(6, stat.getResponseBytes());
		prepStmt.setString(7, stat.getReplyHeader("Content-Type"));
		prepStmt.setString(8, stat.getCanonicalUserName());
		prepStmt.setString(9, stat.getClientHostName());
		prepStmt.setLong(10, stat.getServeMS());
		prepStmt.setString(11, stat.getRequestHeader("Referer"));
		prepStmt.setString(12, stat.getServerName());

		prepStmt.execute();    
		prepStmt.close();
	}

}//class HTTPLogger





