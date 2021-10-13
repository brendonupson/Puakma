/** ***************************************************************
MailTransferThread.java
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
package puakma.addin.mail;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import puakma.addin.pmaAddInStatusLine;
import puakma.coder.CoderB64;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.SystemContext;
import puakma.system.pmaThreadInterface;
import puakma.util.MailAddress;
import puakma.util.Util;
import puakma.util.parsers.html.HTMLParser;

/**
 * This class is responsible for transferring mail to the appropriate smtp host
 * The thread processes ONE message, then dies.
 */
public class MailTransferThread implements pmaThreadInterface, ErrorDetect
{
	private long m_lMessageID;
	private String m_sMessageSerialID="";
	private SystemContext pSystem;
	private MAILER pMailer;
	private String m_sThreadID="";
	private File m_fMessageContent;
	private String m_sFrom="";
	private String m_sSmartHost="";
	private int m_iSmartHostPort=25;
	private boolean m_bSmartHostPortSecure = false;
	private int m_iSocketTimeoutSeconds=60;
	private long m_lUnique=0;

	private final static String CRLF="\r\n";
	private static final String MIME_START = "---------------6969";
	private pmaAddInStatusLine pStatus;

	MailTransferThread(SystemContext paramSystem, MAILER pParent, long paramMessageID, String paramThreadID, int mail_socket_timeout_seconds)
	{
		pSystem = paramSystem;
		pMailer = pParent;
		m_lMessageID = paramMessageID;
		m_sThreadID = paramThreadID;

		m_sSmartHost = pMailer.getSmartHost();
		m_iSmartHostPort = pMailer.getSmartHostPort();
		m_bSmartHostPortSecure = pMailer.isSmartHostPortSecure();
		if(mail_socket_timeout_seconds>1) m_iSocketTimeoutSeconds = mail_socket_timeout_seconds;

	}


	/**
	 *
	 */
	public void run()
	{
		int iDeliveryCount=0;
		pStatus = pMailer.createStatusLine(" " + getErrorSource());
		Connection cx=null;
		String sReply; //message sent flag

		//System.out.println(Thread.currentThread().getName() + " running..." );
		pStatus.setStatus("Writing to temp file");
		writeTempFile(m_lMessageID);
		//pSystem.pErr.doInformation("Transferring message: " + MessageID, this);
		try
		{
			cx = pSystem.getSystemConnection();
			String szQuery = "SELECT * FROM MAILHEADER WHERE MailBodyID=" + m_lMessageID;
			Statement Stmt = cx.createStatement();
			ResultSet RS = Stmt.executeQuery(szQuery);
			while(RS.next())
			{
				long MailHeaderID = RS.getLong("MailHeaderID");
				int iRetries = RS.getInt("RetryCount");
				if(iRetries<pMailer.getMailMaxRetryCount())
				{
					String szRecipient = RS.getString("Recipient");          
					sReply = "";
					if(MailHeaderID>0) 
					{
						sReply = transferMail(szRecipient, MailHeaderID);
						pMailer.incrementStatistic(MAILER.STATISTIC_KEY_MAILSTOTAL);
					}
					if(sReply.length()==0)
					{
						pStatus.setStatus("Removing header record");
						deleteHeader(MailHeaderID);
						iDeliveryCount++;												
					}
					else //send failure...
					{
						pMailer.incrementStatistic(MAILER.STATISTIC_KEY_MAILSTOTALSENDERRORS);

						if(sReply.equalsIgnoreCase("DEAD"))
						{
							pStatus.setStatus("Marking mail as DEAD");
							markMailDead(MailHeaderID);							
							continue;
						}

						if(sReply.charAt(0) >= '0' && sReply.charAt(0) <= '9')
						{
							pSystem.doInformation("MailTransferThread.MailFail", new String[]{sReply}, this);
							deleteHeader(MailHeaderID);
						}
						else
						{
							pStatus.setStatus("Re-Queueing mail for retransmission");
							queueMail(MailHeaderID, sReply, szRecipient);
							pMailer.incrementStatistic(MAILER.STATISTIC_KEY_MAILSTOTALREQUEUED);
						}						
					}
				}//iRetries
				else
				{
					pStatus.setStatus("Marking mail as DEAD");
					markMailDead(MailHeaderID);
				}

			}
			RS.close();
			Stmt.close();
		}
		catch (Exception sqle)
		{
			pSystem.doError("MailTransferThread.MailError", new String[]{sqle.toString()}, this);
			Util.logStackTrace(sqle, pSystem, 999);
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}
		pMailer.removeMessageID(m_lMessageID);
		m_fMessageContent.delete(); 
		pMailer.removeStatusLine(pStatus);
	}

	/**
	 * Puts the mail back into the queue, and increments the failcount
	 */
	private void queueMail(long MailHeaderID, String szFailReason, String szRecipient)
	{
		Connection cx=null;
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, pMailer.getMailRetryMinutes()); //try again in x minutes

		pSystem.doInformation("MailTransferThread.MessageQueued", new String[]{""+MailHeaderID, cal.getTime().toString(), szRecipient}, this);

		try
		{
			cx = pSystem.getSystemConnection();
			String szQuery = "UPDATE MAILHEADER SET RetryAfter=?,FailReason=?,RetryCount=RetryCount+1 WHERE MailHeaderID=" + MailHeaderID;
			PreparedStatement prepStmt = cx.prepareStatement(szQuery);
			prepStmt.setTimestamp(1, new Timestamp(cal.getTime().getTime()) );
			prepStmt.setString(2, szFailReason);
			prepStmt.execute();
			prepStmt.close();
		}
		catch (Exception sqle)
		{
			pSystem.doError("MailTransferThread.QueueError", new String[]{sqle.toString()}, this);
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}
	}

	/**
	 * Puts the mail back into the queue, and increments the failcount
	 */
	private void markMailDead(long MailHeaderID)
	{

		pSystem.doInformation("MailTransferThread.MessageDead", new String[]{""+MailHeaderID}, this);
		Connection cx=null;
		try
		{
			cx = pSystem.getSystemConnection();
			String szQuery = "UPDATE MAILHEADER SET FailReason='Max. retries exceeded. Message is Dead', MessageStatus='D' WHERE MailHeaderID=" + MailHeaderID;
			Statement Stmt = cx.createStatement();
			Stmt.execute(szQuery);
			Stmt.close();
		}
		catch (Exception sqle)
		{
			pSystem.doError("MailTransferThread.MarkDeadError", new String[]{sqle.toString()}, this);
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}
	}

	/**
	 * Perform the smtp dialog...
	 * @return true if the message can be sent
	 */
	private String transferMail(String sRecipient, long lMailHeaderID)
	{
		SMTPSocket smtp = new SMTPSocket();
		//TODO make this dynamic
		smtp.setSocketTimeout(m_iSocketTimeoutSeconds*1000); //30 seconds
		String sTransferHost="";
		MailAddress maTo = new MailAddress(sRecipient);
		MailAddress maFrom = new MailAddress(m_sFrom);


		try
		{
			if(m_sSmartHost==null || m_sSmartHost.length()==0)
			{            
				sTransferHost = Util.getMXAddress(maTo.getHost());
				pStatus.setStatus("Connecting to SMTP server "+sTransferHost +" for " + maTo.getBasicEmailAddress());
				smtp.connect( sTransferHost, pMailer.getHostName() ); 
			}
			else
			{
				sTransferHost = m_sSmartHost;
				String sUserName = pSystem.getSystemProperty("MAILERSmartHostUserName");
				String sPassword = pSystem.getSystemProperty("MAILERSmartHostPassword");

				if(sUserName!=null) smtp.setUserNamePassword(sUserName, sPassword);
				pStatus.setStatus("Connecting to smarthost SMTP server "+sTransferHost +" for " + maTo.getBasicEmailAddress());
				smtp.connect( m_sSmartHost, m_iSmartHostPort, m_bSmartHostPortSecure, pMailer.getHostName() );
			}
			pSystem.doInformation("MailTransferThread.MailStart", new String[]{m_sMessageSerialID, sTransferHost, maTo.getBasicEmailAddress()}, this);        
			pStatus.setStatus("MAIL FROM: " + maTo.getBasicEmailAddress());
			smtp.sendFrom( maFrom.getBasicEmailAddress() );
			pStatus.setStatus("RCPT TO: " + maTo.getBasicEmailAddress());
			smtp.sendTo( maTo.getBasicEmailAddress() );
			double dLen = ((double)m_fMessageContent.length()/(double)1024);
			NumberFormat nf = NumberFormat.getInstance();
			nf.setMaximumFractionDigits(0);
			pStatus.setStatus("Transferring mail for " + maTo.getBasicEmailAddress() + ", " + nf.format(dLen) +"Kb");
			if(smtp.sendFile(m_fMessageContent.getAbsolutePath()))
			{
				pSystem.doInformation("MailTransferThread.MailOK", new String[]{m_sMessageSerialID, sTransferHost, maTo.getBasicEmailAddress()}, this);
			}
			else
			{				
				pSystem.doError("MailTransferThread.MailError", new String[]{m_sMessageSerialID, sTransferHost, maTo.getBasicEmailAddress(), maFrom.getBasicEmailAddress(), smtp.getHostResponses()}, this);				
			}
			pStatus.setStatus("Disconnecting...");
			smtp.logoff();
			smtp.disconnect();
		}
		catch(Exception smtp_ex)
		{

			try{ smtp.disconnect(); } catch(Exception e){}
			pSystem.doError("MailTransferThread.SMTPError", new String[]{sTransferHost, maTo.getBasicEmailAddress(), smtp_ex.getMessage(), smtp.getHostResponses()}, this);
			smtp_ex.printStackTrace();
			pMailer.incrementStatistic(MAILER.STATISTIC_KEY_MAILSTOTALSENDERRORS);
			try 
			{
				int iReplyCode = smtp.getReplyCode();
				//allow any 400 error, treat as a temporary error
				if(iReplyCode>=400 && iReplyCode<500) 
				{
					pMailer.incrementStatistic(MAILER.STATISTIC_KEY_MAILSTOTALREQUEUED);
					return "REQUEUE"; //reply with anything except a number!
				}
				if(iReplyCode>=500 && iReplyCode<600) //permanent error
				{
					return "DEAD";
				}
			} 
			catch (Exception e) {} //temporary error.

			return smtp_ex.getMessage();
		}

		return "";
	}


	/**
	 * Deletes the row describing the header. MailCleaner will get the rest later
	 */
	private void deleteHeader(long MailHeaderID)
	{
		Connection cx=null;
		try
		{
			cx = pSystem.getSystemConnection();
			String szQuery = "DELETE FROM MAILHEADER WHERE MailHeaderID=" + MailHeaderID;
			Statement Stmt = cx.createStatement();
			Stmt.execute(szQuery);
			Stmt.close();
		}
		catch (Exception sqle)
		{
			pSystem.doError("MailTransferThread.DeleteHeaderError", new String[]{sqle.toString()}, this);
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}
	}

	/**
	 * Write a temp file that contains the message content that will go to all recipients
	 */
	private void writeTempFile(long MailBodyID)
	{
		Connection cx=null;    
		String szQuery;
		ResultSet RS;
		//long lAttachments=0;


		try
		{
			cx = pSystem.getSystemConnection();
			m_fMessageContent = File.createTempFile(String.valueOf(pSystem.getUniqueNumber())+"_mailout_", null, pSystem.getTempDir());
			FileOutputStream fout = new FileOutputStream(m_fMessageContent);
			Statement Stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);


			String sFlags=null;
			szQuery = "SELECT Flags FROM MAILBODY WHERE MailBodyID=" + MailBodyID;
			RS = Stmt.executeQuery(szQuery);
			if(RS.next()) sFlags = RS.getString(1);
			RS.close();
			Stmt.close();
			if(sFlags!=null && Util.trimSpaces(sFlags).equalsIgnoreCase("RAWMIME"))
			{
				composeRawMimeMail(MailBodyID, fout, cx);
			}
			else
			{
				composeStandardMail(MailBodyID, fout, cx, sFlags);
			}


		}
		catch (Exception sqle)
		{
			pSystem.doError("MailTransferThread.TransferError", new String[]{sqle.getMessage()}, this);
			//Util.logStackTrace(sqle, pSystem, 999);
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}
	}

	/**
	 * Assumes a the content stored is already in mime format and ready to send
	 */
	private void composeRawMimeMail(long MailBodyID, FileOutputStream fout, Connection cx) throws Exception
	{
		byte buf[]=new byte[8192];

		String szQuery = "SELECT * FROM MAILBODY WHERE MailBodyID=" + MailBodyID;
		Statement Stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		ResultSet RS = Stmt.executeQuery(szQuery);
		if(RS.next())
		{
			InputStream is = RS.getBinaryStream("Body");
			if(is!=null)
			{
				while(is.available()>0)
				{
					int iRead = is.read(buf);
					if(iRead>0) fout.write(buf, 0, iRead);
				}
				fout.flush();
				fout.close();
			}
		}
		RS.close();
		Stmt.close();

	}

	/**
	 *
	 * @TODO: i18n on email? maybe we need more fields in the mailbody table? charset? content-type?
	 */
	private void composeStandardMail(long MailBodyID, FileOutputStream fout, Connection cx, String sFlags) throws Exception
	{
		boolean bFirstRow=true;
		SimpleDateFormat sdf = new SimpleDateFormat( "EEE, d MMM yyyy HH:mm:ss Z" );
		//String szNote="", szContent="";
		// Format the current time.

		//int x=0, i=0;//, iReadLen=256;
		CoderB64 b64 = new CoderB64();
		BufferedInputStream bis;
		MailAddress maFrom = new MailAddress("");
		String MimeBoundary = getNextMimeBoundary();
		String MimeAlternateBoundary = getNextMimeBoundary()+ (int)(Math.random()*10000);
		byte MimeBoundaryBytes[] = String.valueOf("--" + MimeBoundary + CRLF).getBytes();
		byte MimeEndBoundaryBytes[] = String.valueOf("--" + MimeBoundary + "--").getBytes();
		byte MimeAlternateBoundaryBytes[] = String.valueOf("--" + MimeAlternateBoundary + CRLF).getBytes();
		byte MimeAlternateEndBoundaryBytes[] = String.valueOf("--" + MimeAlternateBoundary + "--").getBytes();

		String szQuery = "SELECT * FROM MAILBODY LEFT JOIN MAILATTACHMENT ON MAILBODY.MailBodyID=MAILATTACHMENT.MailBodyID WHERE MAILBODY.MailBodyID=" + MailBodyID;
		Statement Stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		ResultSet RS = Stmt.executeQuery(szQuery);
		while(RS.next())
		{
			if(bFirstRow)
			{
				String szBody = fixBareLineFeeds(RS.getString("Body")); //FIXME inject CR for bare LFs
				String szSubject = RS.getString("Subject");
				m_sFrom = RS.getString("Sender");
				maFrom = new MailAddress(m_sFrom);
				String szTo = RS.getString("SendTo");
				String szCopyTo = RS.getString("CopyTo");
				m_sMessageSerialID = RS.getString("SerialID");

				//This is the header parcel
				fout.write(formatLine("Date: ", sdf.format(new java.util.Date())));
				//fout.write(formatLine("Received: ", "Puakma build" + pSystem.getBuild() + "; " + sdf.format(new java.util.Date())));
				fout.write(formatLine("X-Mailer: ", pSystem.getVersionString()));
				fout.write(formatLine("From: ", maFrom.getFullParsedEmailAddress()));
				String sRand = String.valueOf((long)(Math.random()*999999));
				fout.write(formatLine("Message-ID: ", '<' + m_sMessageSerialID + '.' + sRand + '@' + maFrom.getHost()+'>'));
				fout.write(formatLine("To: ", szTo));
				if(szCopyTo!=null && szCopyTo.length()>0) fout.write(formatLine("cc: ", szCopyTo));
				szSubject = szSubject.replaceAll("\r", "");
				szSubject = szSubject.replaceAll("\n", "");
				fout.write(formatLine("Subject: ", szSubject));
				fout.write(formatLine("Reply-To: ", maFrom.getFullParsedEmailAddress()));
				if(sFlags!=null && sFlags.indexOf('R')>=0) //return receipt
					fout.write(formatLine("Disposition-Notification-To: ", maFrom.getBasicEmailAddress()));
				if(sFlags!=null && sFlags.indexOf('H')>=0) //high importance
					fout.write(formatLine("Importance: ", "High"));
				//fout.write(formatLine("Errors-To: ", maFrom.getFullParsedEmailAddress()));
				fout.write(formatLine("MIME-Version: ", "1.0"));         

				fout.write(formatLine("Content-Type: ", "multipart/mixed; boundary=\"" + MimeBoundary + '\"'));
				//fout.write(formatLine("Status: ", "RO"));
				fout.write(formatLine("", "\r\nThis is a multi-part message in MIME format."));
				if(szBody!=null)
				{
					//this is for non Mime compatible systems
					fout.write(MimeBoundaryBytes);
					fout.write(formatLine("Content-Type: ", "multipart/alternative; boundary=\"" + MimeAlternateBoundary + '\"'));
					fout.write(CRLF.getBytes());

					//do plain messagebody
					fout.write(MimeAlternateBoundaryBytes);
					fout.write(formatLine("Content-Transfer-Encoding: ", "8bit"));
					fout.write(formatLine("Content-Type: ", "text/plain; charset=utf-8; format=flowed"));
					//fout.write(formatLine("Content-Transfer-Encoding: ", "7bit"));
					fout.write(CRLF.getBytes());            
					if(szBody.indexOf('<')>=0 && szBody.indexOf('>')>=0)
					{                
						HTMLParser parser = new HTMLParser();
						parser.setExcludeTag("style");
						parser.setExcludeTag("script");
						puakma.util.parsers.html.HTMLDocument docParsed = parser.parse(szBody);
						//System.out.println(docParsed.toTextOnly());
						fout.write(docParsed.toTextOnly().getBytes("UTF-8"));
					}
					else //not html text
						fout.write(szBody.getBytes("UTF-8")); 
					fout.write(CRLF.getBytes());
					fout.write( CRLF.getBytes() ); //blank line

					//do html messagebody
					fout.write(MimeAlternateBoundaryBytes);
					fout.write(formatLine("Content-Transfer-Encoding: ", "8bit"));            
					fout.write(formatLine("Content-Type: ", "text/html; charset=utf-8"));
					//fout.write(formatLine("Content-Transfer-Encoding: ", "7bit"));            
					fout.write( CRLF.getBytes() );
					fout.write(szBody.getBytes("UTF-8")); 
					fout.write(CRLF.getBytes());                                    
					fout.write( CRLF.getBytes() ); //blank line

					fout.write(MimeAlternateEndBoundaryBytes);            
					fout.write( CRLF.getBytes() );
				}

				bFirstRow = false;
			}
			//attachment data may be null! check first
			RS.getLong("MailAttachmentID");
			if(!RS.wasNull())
			{
				String szType = RS.getString("ContentType");
				if(szType==null || szType.length()==0) szType = "application/octet-stream";          
				String szName = cleanFileName(RS.getString("FileName"));

				//START mime message attachments
				fout.write( CRLF.getBytes() );
				fout.write(MimeBoundaryBytes);
				fout.write(formatLine("Content-Type: ", szType + "; name=\"" + szName + "\""));
				fout.write(formatLine("Content-Transfer-Encoding: ", "base64"));
				//fout.write(formatLine("Content-Description: ", szName));
				//fout.write(formatLine("Content-Disposition: inline; ", "filename=\"" + szName + '\"'));
				fout.write(formatLine("Content-Disposition: ", "attachment; filename=\"" + szName + "\""));

				byte bIn[]  = new byte[57];
				byte bOut[] = new byte[90];
				int iTotalRead=0;
				int iTotalWrite=0;
				bis = new BufferedInputStream( RS.getBinaryStream("Attachment") ); //, 256 );
				while ( bis.available() > 0 )
				{
					int iRead = bis.read( bIn);
					iTotalRead += iRead;
					//this.pSystem.doInformation("read attachment " + iTotalRead + "bytes", this);            
					int iEncodedLen = b64.encode( bIn, bOut, iRead, bOut.length );
					if(iEncodedLen>0)
					{
						iTotalWrite+=iEncodedLen;
						//this.pSystem.doInformation("wrote base64 " + iTotalWrite + "bytes", this);              
						fout.write( CRLF.getBytes() );
						fout.write( bOut, 0, iEncodedLen );              
					}
				}
				//pSystem.doInformation("COMPLETED read attachment " + iTotalRead + "bytes", this);
				//pSystem.doInformation("COMPLETED write attachment " + iTotalWrite + "bytes", this);
				bis.close();
			}
		}

		fout.write( CRLF.getBytes() );
		fout.write(MimeEndBoundaryBytes);
		fout.write( CRLF.getBytes() );
		fout.flush();
		fout.close();
		RS.close();
		Stmt.close();
	}

	/**
	 * https://searchwindowsserver.techtarget.com/tip/Beware-of-bare-linefeeds-in-Exchange-Server-email
	 * RFC 822bis does not allow bare LF (\n) in email body. These must all be converted to CRLF (\r\n)
	 * @param sBody
	 * @return
	 */
	private String fixBareLineFeeds(String sBody) 
	{
		if(sBody==null) return "";

		char CR = '\r';
		char LF = '\n';
		int iFixCount = 0;
		boolean bLastCharWasCR = false;
		StringBuilder sb = new StringBuilder(sBody.length()+50);
		for(int i=0; i<sBody.length(); i++)
		{
			char c = sBody.charAt(i);
			if(c==LF && !bLastCharWasCR) 
			{
				sb.append(CR);
				iFixCount++;
			}
			sb.append(c);

			bLastCharWasCR = (c==CR);
		}

		if(iFixCount>0) pSystem.doDebug(pmaLog.DEBUGLEVEL_DETAILED, "fixBareLineFeeds() - inserted " + iFixCount + " CRs", this);
		return sb.toString();
	}


	/**
	 * Code to make the next mime boundary
	 */
	private synchronized String getNextMimeBoundary()
	{
		SimpleDateFormat partcodedf = new SimpleDateFormat( "yyyyMMddHHmmssSS" );

		m_lUnique++;
		return MIME_START + partcodedf.format(new java.util.Date()) + m_lUnique;
	}

	/**
	 * Returns a string suitable for output to a stream
	 */
	private byte[] formatLine(String szTitle, String szContent)
	{
		String szReturn = szTitle + szContent + CRLF;
		byte bufReturn[] = null;
		try{ bufReturn = szReturn.getBytes("US-ASCII"); }catch(Exception e){}
		return bufReturn;

	}

	public String getErrorSource()
	{
		return "MailTransferThread";
	}

	public String getErrorUser()
	{
		//return pmaSystem.SYSTEM_ACCOUNT;
		return m_sThreadID;
	}

	public void destroy()
	{
	}


	public String getThreadDetail() 
	{
		return "MailTransfer";
	}

	/**
	 * Strip the leading path off the file to return just the name
	 */
	public String cleanFileName(String sFile)
	{
		if(sFile==null || sFile.length()==0) return "unknown.bin";

		int iPos = sFile.lastIndexOf('/');
		if(iPos>0) return sFile.substring(iPos+1);

		iPos = sFile.lastIndexOf('\\');
		if(iPos>0) return sFile.substring(iPos+1);

		return sFile;
	}

}