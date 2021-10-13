/** ***************************************************************
PortSocket.java
Copyright (C) 2001  Mike Skillicorn 
http://www.seatechnology.com.au mike@mikeskillicorn.com

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

package puakma.addin.mail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;



import puakma.util.Util;

public class PortSocket
{  
	private static final int DEFAULT_IN_BUFF_SIZE = 4096;
	private static final int DEFAULT_OUT_BUFF_SIZE = 4096;

	private int m_iSendSize;
	private int m_iReplySize;
	private Socket sock;   // encapsulate not inherit as connection is needed after creation

	private boolean bInit = false;
	//	private byte btSend[];
	private byte btReply[];
	private int iRead = 0;
	private int iSent = 0;
	private DataInputStream is;  // have to use data in and out streams here as buffered
	private DataOutputStream os; // streams wait forever to fill a buffer tha over the net
	//	will never be filled. Data Streams read a byte at a
	//	time and fail when no more to read.
	private int m_iDefaultSocketTimeoutMS = 30000;
	private StringBuilder m_sbHostReplies = new StringBuilder();

	private boolean bMonitoring = false;

	public PortSocket( int iSendBuffSize, int iReplyBuffSize )
	{  
		m_iSendSize = iSendBuffSize;
		m_iReplySize = iReplyBuffSize;
		//		btSend  = new byte[ m_iSendSize ];
		btReply = new byte[ m_iReplySize ];
	}

	public PortSocket()
	{  
		this( DEFAULT_IN_BUFF_SIZE, DEFAULT_OUT_BUFF_SIZE );
	}

	public boolean isMonitoring() { return( bMonitoring ); }
	public void   setMonitoring( boolean bOn ) { bMonitoring = bOn; }

	public void connect( String sServer, int iPort, boolean bSecure) throws Exception
	{  
		// create a socket, connect to the server, set up streams
		//System.out.println("[" + sServer + "] " + iPort + " secure=" + bSecure);

		sock = new Socket( sServer, iPort );
		try{sock.setSoTimeout(m_iDefaultSocketTimeoutMS);}catch(Exception e){}

		// create the file streams
		is = new DataInputStream( sock.getInputStream() );
		os = new DataOutputStream( sock.getOutputStream() );

		//System.out.println(sock.toString());
		iSent = iRead = 0;
		bInit = true;
	}

	public void upgradeSocketToTLS() throws Exception
	{		
		SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(
				sock, 
				sock.getInetAddress().getHostAddress(), 
				sock.getPort(), 
				true);
		sslSocket.startHandshake(); //upgrade to secure		

		// re-create the file streams
		is = new DataInputStream( sslSocket.getInputStream() );
		os = new DataOutputStream( sslSocket.getOutputStream() );
	}

	public void setSocketTimeout(int iMilliSeconds)
	{
		m_iDefaultSocketTimeoutMS = iMilliSeconds;
		try{sock.setSoTimeout(iMilliSeconds);}catch(Exception e){}
	}

	public void disconnect() throws Exception
	{  
		is.close();
		os.close();
		sock.close();
		bInit = false;
	}

	public boolean write( String sMessage ) throws Exception
	{  
		int iOffset = 0, iToSend, iLen;
		if ( bInit )
		{  iSent = 0;
		iLen = sMessage.length();
		while ( iOffset < iLen )
		{  if ( iLen - iOffset > m_iSendSize ) iToSend = m_iSendSize;
		else iToSend = iLen - iOffset;
		if ( iToSend > 0 )
		{  //sMessage.getBytes( iOffset, iToSend, btSend, 0 );
			os.write( sMessage.substring( iOffset, iOffset + iToSend ).getBytes(), 0, iToSend );
		}
		iOffset += m_iSendSize;
		iSent += iToSend;
		}
		return( true );
		}
		return( false );
	}

	//	use this to read from the socket input stream and write to the
	//	specified output stream
	public int readToStream( OutputStream out, int iToRead ) throws Exception
	{  
		iRead = is.read( btReply, 0, iToRead );
		if ( iRead > 0 ) out.write( btReply, 0, iRead );
		return( iRead );
	}

	//	use this to write the current read buffer to a stream.
	//	This is useful for writing pop buffers that contain a response code
	//	with data content or writing reponse logs.
	public int writeBuffToStream( OutputStream out, int iOffset, int iLength ) throws Exception
	{  
		if ( iLength > 0 ) out.write( btReply, iOffset, iLength );
		return( iLength );
	}

	public boolean writefrom( String sFile ) throws Exception
	{  
		BufferedInputStream bin; //= new BufferedInputStream( sock.getInputStream(), 8096 );
		//	BufferedOutputStream bout; //= new BufferedOutputStream( new FileOutputStream( "d:\\2.txt" ), 8096 );
		int iToRead = 0, iSize;

		if ( bInit )
		{  
			iRead = 1;
			iSent = 0;
			//	bout = new BufferedOutputStream( sock.getOutputStream(), 8096 );
			bin = new BufferedInputStream( new FileInputStream( sFile ), m_iReplySize ); //8096 );
			iSize = bin.available();

			while ( iSize > 0 && iRead > 0 )
			{  if ( iSize > m_iSendSize ) iToRead = m_iSendSize;
			else iToRead = iSize;

			iRead = bin.read( btReply, 0, iToRead );

			//	if ( iRead > 0 ) bout.write( btReply, 0, iRead );
			if ( iRead > 0 ) os.write( btReply, 0, iRead );
			iSize -= iToRead;
			}
			//	dont close bout as it's just a wrapper around socket.out
			bin.close();
			return( true );
		}
		return( false );
	}

	public boolean readsize( int iSize ) throws Exception
	{  
		//int iToRead;
		if ( bInit )
		{  
			iRead = 0;
			while ( iSize > 0 )
			{  
				/*if ( iSize > m_iReplySize ) iToRead = m_iReplySize;
			else iToRead = iSize;*/
				iRead = is.read( btReply, 0, iSize );
				btReply[ iRead ] = 0; //'\0';
				iSize -= iRead;
			}
			return( true );
		}
		return( false );
	}

	public int read() throws Exception
	{  
		iRead = 0;
		if ( bInit )
		{  // uses a read instead of a readln here in case it's a multiline response
			// make sure your reply buffer can handle the max response!
			iRead = is.read( btReply, 0, m_iReplySize );
		}
		return( iRead );
	}

	public boolean readinto( String sFile, int iSize ) throws Exception
	{  //BufferedInputStream bin; //= new BufferedInputStream( sock.getInputStream(), 8096 );
		BufferedOutputStream bout; //= new BufferedOutputStream( new FileOutputStream( "d:\\2.txt" ), 8096 );
		int iToRead = 0;

		if ( bInit )
		{  iRead = 1;
		//		bin = new BufferedInputStream( sock.getInputStream(), 8096 );
		bout = new BufferedOutputStream( new FileOutputStream( sFile ), m_iSendSize ); //8096 );
		while ( iSize > 0 && iRead > 0 )
		{  if ( iSize > m_iReplySize ) iToRead = m_iReplySize;
		else iToRead = iSize;

		//		iRead = bin.read( btReply, 0, iToRead );
		iRead = is.read( btReply, 0, iToRead );
		if ( iRead > 0 ) bout.write( btReply, 0, iRead );
		iSize -= iToRead;
		}
		// dont close bin as it's just a wrapper around socket.in
		bout.close();
		return( true );
		}
		return( false );
	}

	public String getReply()	
	{  
		if(iRead<=0) return "";
		return( new String( btReply, 0, iRead ) ); //new String( btReply, 0, 0, 5) ); //iRead ) );
	}

	public int getReplyBufferSize() {  return( m_iReplySize ); }
	public int getSendBufferSize() {  return( m_iSendSize ); }

	public int getNumberSent() {  return( iSent ); }
	public int getNumberRead() {  return( iRead ); }


	public String send( String sMessage ) throws Exception
	{  
		int iRead;
		if ( bInit )
		{  
			if ( write( sMessage ) )
			{  
				os.flush(); //force the data to be sent
				iRead = read();
				if ( iRead >= 0 ) return( new String( btReply, 0, iRead ) );
				else return( "" );
			}
		}
		return( "" );
	}

	public void sendOnly( String sMessage ) throws Exception
	{  		
		if ( bInit )
		{  
			if ( write( sMessage ) )
			{  
				os.flush(); //force the data to be sent			
			}
		}
	}

	/**
	 * return true if we received a 2xx or 3xx reply from the server.
	 */
	public boolean zzreadOK() throws Exception
	{  		
		if ( bInit )
		{  
			int iRead = read();
			if ( iRead >= 0 ) 
			{
				String sReply = new String( btReply, 0, iRead );
				if(sReply.charAt( 0 ) == '2' || sReply.charAt( 0 ) == '3') return true;
			}        
		}
		return false;
	}

	public boolean readOK() throws Exception
	{  		
		if ( bInit )
		{  
			boolean bWaiting = true;
			while(bWaiting)
			{
				int iRead = read();
				//System.out.println("read: " + iRead);
				if ( iRead >= 0 ) 
				{
					String sReply = new String( btReply, 0, iRead );
					m_sbHostReplies.append(sReply);
					String sAllLines[] = sReply.split("\r\n"); 
					String sLastReply = sAllLines[sAllLines.length-1];
					if(bMonitoring) System.out.print(sReply);
					int pos = sLastReply.indexOf(' ');
					int posDash = sLastReply.indexOf('-');
					if(pos>0 && (posDash<0 || posDash>pos) )
					{
						String sNum = sLastReply.substring(0, pos);
						long lNum = Util.toInteger(sNum);
						if(lNum>0)
						{
							if(lNum>=200 && lNum<400) return true;
							return false; // a valid number but not success, so return false
						}
						//if(sReply.charAt( 0 ) == '2' || sReply.charAt( 0 ) == '3') return true;
						//wait....
					}
				}
				else //read <0
					return false;
			}//while
		}
		return false;
	}

	public String getHostResponses()
	{
		return m_sbHostReplies.toString();
	}

}






