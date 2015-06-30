/** ***************************************************************
SMTPSocket.java
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


public class SMTPSocket extends PortSocket
{  
	private static final int DEFAULT_PORT = 25;
	private String m_sReply;   
	private String m_sUserName=null;
	private String m_sPassword=null;
	private static final String m_sHELO="HELO"; //we don't need EHLO for anything specific


	public SMTPSocket( int iSendBuffSize, int iReplyBuffSize)
	{  
		super( iSendBuffSize, iReplyBuffSize );      
	}

	public SMTPSocket()
	{  
		super();       
	}

	public void connect( String sServer, String sClientName) throws Exception
	{  
		super.connect( sServer, DEFAULT_PORT );
		if(!readOK()) throw new Exception("Could not connect, received invalid response");
		sendOK( m_sHELO + " " + sClientName + "\r\n" );
	}

	public void connect( String sServer, int iPort, String sClientName ) throws Exception
	{  
		super.connect( sServer, iPort );
		if(!readOK()) throw new Exception("Could not connect, received invalid response");
		sendOK( m_sHELO + " " + sClientName + "\r\n" );
		if(!logon()) throw new Exception("MAILER.LoginFail");
	}


	private boolean sendOK( String sMessage )throws Exception
	{  
		m_sReply = send( sMessage );
		if ( isMonitoring() )
		{  System.out.print( sMessage );
		System.out.print( m_sReply );
		}
		if(m_sReply.charAt( 0 ) == '2' || m_sReply.charAt( 0 ) == '3') return true;

		throw new Exception(m_sReply + " [" + sMessage + "]");
//		return false;
	}



	/**
	 * For SMTP authorized transfers
	 * IN: 220 SMTP Ready
OUT: HELO client.name (or EHLO client.name)
IN: 250 OK (or a multiline response if EHLO was used)
OUT: AUTH LOGIN
IN: 334 VXNlcm5hbWU6 ("Username:" BASE64 encoded)
OUT: dGVzdA== ("test" BASE64 encoded)
IN: 334 UGFzc3dvcmQ6 ("Password:" BASE64 encoded)
OUT: dGVzdA== ("test" BASE64 encoded)
IN: 235 Ok
OUT: MAIL FROM: <username@domain.com> 
	 */
	public synchronized void setUserNamePassword(String sUserName, String sPassword)
	{       
		m_sUserName = sUserName;
		m_sPassword = sPassword;            
	}

	/**
	 *
	 */
	public boolean logon() throws Exception
	{  
		if(m_sUserName==null) return true;
		if(!sendOK("AUTH LOGIN\r\n")) return false;
		//send username, reply should be: 334 VXNlcm5hbWU6 ("Username:" BASE64 encoded)
		if(!sendOK(puakma.util.Util.base64Encode(m_sUserName.getBytes()) + "\r\n")) return false;
		String sPassword="";
		if(m_sPassword!=null) sPassword = m_sPassword;
		if(!sendOK(puakma.util.Util.base64Encode(sPassword.getBytes()) + "\r\n")) return false;
		return true ;
	}

	public boolean logoff() throws Exception
	{  m_sReply = send( "QUIT\r\n" );
	if ( isMonitoring() )
	{  System.out.print( "QUIT\r\n" );
	System.out.print( m_sReply );
	}
	return( true );
	}

	public int getReplyCode() throws Exception
	{  String sReply = getReply();
	if ( sReply.length() >= 3 ) return( Integer.parseInt( sReply.substring( 0, 3 ) ) );
	else return( -1 );
	}

	public boolean sendFrom( String sEmailAddressFrom ) throws Exception
	{  return( sendOK( "MAIL FROM:<" + sEmailAddressFrom + ">\r\n" ) );
	}

	// you can do this more than once
	public boolean sendTo( String sEmailAddressTo ) throws Exception
	{  return( sendOK( "RCPT TO:<" + sEmailAddressTo + ">\r\n" ) );
	}

	public boolean sendMessage( String sData )throws Exception
	{  if ( sendOK( "DATA\r\n" ) )
	{  return( sendOK( sData + "\r\n.\r\n"  ) );
	}
	return( false );
	}

	public boolean sendFile( String sFile )throws Exception
	{  if ( sendOK( "DATA\r\n" ) )
	{  writefrom( sFile );
	return( sendOK( "\r\n.\r\n"  ) );
	}
	return( false );
	}
}