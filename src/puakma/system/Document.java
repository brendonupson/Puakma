/** ***************************************************************
Document.java
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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import puakma.addin.http.HTTP;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.util.ByteStreamReader;
import puakma.util.MailAddress;
import puakma.util.Util;

public class Document implements ErrorDetect,Cloneable
{
	private Hashtable<String, DocumentItem> m_htItems = new Hashtable<String, DocumentItem>(); //keys are stored in lowercase
	private Hashtable<String, Parameter> m_htParams = new Hashtable<String, Parameter>(); //keys are stored in lowercase
	private Hashtable<String, Cookie> m_htCookies= new Hashtable<String, Cookie>();

	public String PageName;
	public RequestPath rPath = new RequestPath("/");
	protected byte Content[]; //the sum of the processed designObject AND document
	protected String ContentType="text/html";
	public SystemContext pSystem;
	public SessionContext pSession;

	public static final String CONTENT_PLAIN = "application/x-www-form-urlencoded";
	public static final String CONTENT_MULTI = "multipart/form-data";

	public static final String APPPARAM_LOGINPAGE="loginpage"; //name of the default login page
	public static final String APPPARAM_DEFAULTOPEN="defaultopen"; //what URL to open if 'GET /xxx.pma' only is requested
	public static final String APPPARAM_DEFAULTCHARSET="defaultcharset"; //default characterset for this application. i18n etc
	public static final String APPPARAM_DISABLEAPP="disableapp"; //'1' if the application is disabled
	public static final String APPPARAM_FORCESECURECONNECTION = "forcesecureconn";
	
	public static final String PAGE_LOGIN_ITEM="$LoginPage";
	public static final String PAGE_LOGIN_BYPASS="$BypassAuthenticators";
	public static final String PAGE_TITLE_ITEM="$PageTitle";
	public static final String PAGE_USERNAME_ITEM="UserName";
	public static final String PAGE_PASSWORD_ITEM="Password";
	public static final String PAGE_REDIRECT_ITEM="$RedirectTo";
	public static final String PAGE_MESSAGESTRING_ITEM="$MessageString";

	public final static String ERROR_FIELD_NAME = "$ErrText";

	public static final int READ_CHUNK_SIZE=24576; //40Kb 40960
	public static final int UPLOAD_CHUNK_SIZE=24576; //256k

	//for sending mail
	public static final String MAIL_SUBJECT_ITEM="Subject";
	public static final String MAIL_FROM_ITEM="From";
	public static final String MAIL_BODY_ITEM="Body";
	public static final String MAIL_TO_ITEM="SendTo";
	public static final String MAIL_CC_ITEM="CopyTo";
	public static final String MAIL_BCC_ITEM="BlindCopyTo";
	public static final String MAIL_FLAGS_ITEM = "$MailFlags";
	public static final String MAIL_RETURNRECEIPT_ITEM = "ReturnReceipt";
	public static final String MAIL_IMPORTANCE_ITEM = "Importance";

	public static final String DATA_ITEM_NAME = "Data";
	

	private String m_sSkipUntil=""; //for mime uploads
	private String m_sCreateItemName=null;
	private String m_sCreateMimeType=null;
	private BufferedOutputStream m_dosCreate=null;
	private File m_fCreateFile=null;
	private String m_sCreateFileName=null;
	protected String m_sCharEncoding=puakma.addin.http.HTTP.DEFAULT_CHAR_ENCODING;
	private boolean m_bDocumentCreatedOK=true;
	private String m_sCreateTransferEncoding=null;

	public Document() { }

	public Document(SystemContext paramSystem, SessionContext paramSession)
	{
		pSystem = paramSystem;
		pSession = paramSession;
	}


	/**
	 * Convenience method should you need to do anything with the design object
	 * as in the HTMLDocument subclass
	 */
	public void prepare()
	{
	}

	public String getCharacterEncoding()
	{
		return m_sCharEncoding;
	}

	public synchronized void setCharacterEncoding(String sNewEncoding)
	{
		if(sNewEncoding!=null && sNewEncoding.length()>0) m_sCharEncoding = sNewEncoding;
	}


	/**
	 * Function call used to render the document with the page design
	 */
	public void renderDocument(boolean bReadMode)
	{
		Content=null;
	}

	/**
	 * Sets the value of the Content
	 */
	public synchronized void setContent(StringBuilder sb)
	{
		if(sb==null) 
			Content=null;
		else
			Content = sb.toString().getBytes();
	}

	/**
	 * Sets the value of the Content
	 */
	public synchronized void setContentType(String szContentType)
	{
		ContentType = szContentType;
	}

	/**
	 * Sets the value of the Content
	 */
	public synchronized void setContent(byte[] sb)
	{
		Content = sb;
	}

	/**
	 * For parsing html POST which will have the format
	 * "Field=Value&F2=V2&f3=v3"
	 */
	public Document(SystemContext paramSystem, SessionContext paramSession, String szPageName, ByteStreamReader is, String szContentType, long lContentLength, String sCharSet)
	{
		pSystem = paramSystem;
		pSession = paramSession;
		pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "new Document() &", pSystem);

		if(szContentType.equalsIgnoreCase(Document.CONTENT_PLAIN)) readStandardPageSubmit(szPageName, is, szContentType, lContentLength, sCharSet);
		else readIntoFile(szPageName, is, szContentType, lContentLength);
	}

	/**
	 * Don't know what the content type is so just read the entire payload into a file attachment "Data"
	 * Used for SOAP etc.
	 * @param szPageName
	 * @param is
	 * @param szContentType
	 * @param lContentLength
	 */
	private void readIntoFile(String szPageName, ByteStreamReader is, String szContentType, long lContentLength)
	{    
		char charBuf[]=new char[UPLOAD_CHUNK_SIZE];
		File fData=null;
		OutputStream fout=null;
		boolean bIsMem=false;    

		try
		{
			if(lContentLength>524288) //0.5MB - may need to tweak later
			{
				fData = File.createTempFile(String.valueOf(pSystem.getSystemUniqueNumber())+"_doc_", null, pSystem.getTempDir());
				fout = (FileOutputStream )(new FileOutputStream(fData));
			}
			else //short request, so cache it in memory
			{
				bIsMem=true;
				fout = new ByteArrayOutputStream((int)lContentLength);
			}
			long lTotalRead = 0;
			int iRead=0;
			while(lTotalRead<lContentLength)
			{
				iRead = is.read(charBuf);
				lTotalRead += iRead;
				byte buf[]=new byte[iRead];
				for(int p=0; p<iRead; p++) buf[p] = (byte)(charBuf[p]);        
				fout.write(buf);        
				buf=null;
			}
			fout.flush();
			fout.close();
		}
		catch(Exception e){}

		//now add the item to the document
		if(bIsMem)
		{
			ByteArrayOutputStream bos = (ByteArrayOutputStream)fout;
			replaceItem(DATA_ITEM_NAME, bos.toByteArray());
		}
		else //isfile
			replaceItem(DATA_ITEM_NAME, fData.getName(), fData, szContentType, null);
	}

	/**
	 * Creates the items on a document from a standard page submit
	 * @param szPageName
	 * @param is
	 * @param szContentType
	 * @param lContentLength
	 */
	private void readStandardPageSubmit(String szPageName, ByteStreamReader is, String szContentType, long lContentLength, String sCharset)
	{
		byte charBuf[]=new byte[(int)lContentLength];
		//byte bufToProcess[]=null;
		//StringBuilder sbData = new StringBuilder(iContentLength);
		ByteArrayOutputStream baos = new ByteArrayOutputStream((int)lContentLength);
		try
		{      
			long lTotalRead = 0;
			int iRead=0;
			while(lTotalRead<lContentLength)
			{
				iRead = is.read(charBuf);
				lTotalRead += iRead;
				baos.write(charBuf, 0 , iRead);
			}
		}
		catch(Exception e){}

		PageName=szPageName;
		String sb=null;
		try{ sb = new String(baos.toByteArray(), sCharset); }
		catch(Exception e){ try{ sb = new String(baos.toByteArray(), puakma.addin.http.HTTP.DEFAULT_CHAR_ENCODING); }catch(Exception t){} }

		String szDataChunk;
		String szItemName, szItemValue;
		sb = sb.replace('+', ' ');

		//System.out.println(sb);
		//String sCharEncoding = puakma.addin.http.HTTP.DEFAULT_CHAR_ENCODING;
		//if(sCharset!=null) sCharEncoding = sCharset;

		StringTokenizer stk= new StringTokenizer(sb, "&", false);
		while (stk.hasMoreTokens())
		{
			szItemName="";
			szItemValue="";
			szDataChunk = stk.nextToken();
			StringTokenizer tk2= new StringTokenizer(szDataChunk, "=", false);
			if(tk2.hasMoreTokens()) szItemName = tk2.nextToken();
			if(tk2.hasMoreTokens()) szItemValue = tk2.nextToken();
			//System.out.println("a name="+szItemName + " value="+szItemValue);
			try{
				szItemName=URLDecoder.decode(szItemName, sCharset); //decode the string, remove %20 etc
			}catch(Exception e){ try{ szItemName=URLDecoder.decode(szItemName, puakma.addin.http.HTTP.DEFAULT_CHAR_ENCODING); }catch(Exception t){} }
			try{
				szItemValue=URLDecoder.decode(szItemValue, sCharset); //decode the string, remove %20 etc
			}catch(Exception e){ try{ szItemValue=URLDecoder.decode(szItemValue, puakma.addin.http.HTTP.DEFAULT_CHAR_ENCODING); }catch(Exception t){} }

			//System.out.println("b name="+szItemName + " value="+szItemValue);
			addItemOnCreate(szItemName, szItemValue);
		}
	}

	/*
  public static void main(String args[])
  {
    String szBoundary = "-----------------------------7d22ea1c7e018c";
    int iContentLength=2487;

    try
    {
      BufferedReader is = new BufferedReader( new FileReader("/httpdata.dat"));
      //Document doc = new Document("PageName", is, "text/html", szBoundary, iContentLength);
    }catch(Exception e){}

  }
	 **/

	/**
	 * For creating documents from mutlipart/mime posts
	 */
	public Document(SystemContext paramSystem, SessionContext paramSession, String sPageName, ByteStreamReader is, String szContentType, String szBoundary, long lContentLength, String sCharset)
	{
		if(sCharset==null) sCharset = HTTP.DEFAULT_CHAR_ENCODING;
		byte charBuf[]=new byte[READ_CHUNK_SIZE];
		byte bufToProcess[]=null;
		//String szLine; 
		//long lTotalWritten = 0;
		//long lStart = System.currentTimeMillis();
		File fDebug=null;
		FileOutputStream fout=null;

		pSystem = paramSystem;
		pSession = paramSession;
		pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "new Document() Mime", pSystem);

		try
		{
			if(pSystem.getDebugLevel()==pmaLog.DEBUGLEVEL_FULL) 
			{
				fDebug = File.createTempFile("debugmime_"+String.valueOf(pSystem.getSystemUniqueNumber()), null, pSystem.getTempDir());            
				fout = (FileOutputStream )(new FileOutputStream(fDebug));
			}
			PageName = sPageName;
			int iRead=0, iTotalRead=0;
			m_sSkipUntil = szBoundary;
			while(iTotalRead<lContentLength)
			{
				//long lStartLoop = System.currentTimeMillis();
				iRead = is.read(charBuf);
				if(iRead<=0) throw new IOException("End of input reached prior to content length. Read " + iTotalRead + " of " + lContentLength + " bytes");

				if(fout!=null) fout.write(charBuf, 0, iRead);
				iTotalRead += iRead;
				//System.out.println("------ read: " + iRead + " " + (System.currentTimeMillis()-lStartLoop) );
				byte buf[] = new byte[iRead];
				System.arraycopy(charBuf, 0, buf, 0, iRead);                

				if(bufToProcess==null)
					bufToProcess=buf;
				else
					bufToProcess = puakma.util.Util.appendBytes(bufToProcess, buf);

				//System.out.println("appended: " + (System.currentTimeMillis()-lStartLoop) );
				int iPos = puakma.util.Util.indexOf(bufToProcess, m_sSkipUntil.getBytes());
				while(iPos>=0)
				{
					bufToProcess = processBuffer(bufToProcess, iPos, szBoundary, sCharset);
					iPos = puakma.util.Util.indexOf(bufToProcess, m_sSkipUntil.getBytes());
				}//while
				//System.out.println("indexOf: " + (System.currentTimeMillis()-lStartLoop) );
				if(iPos<0 && m_dosCreate!=null && bufToProcess!=null && bufToProcess.length>m_sSkipUntil.length())
				{
					int iLen = bufToProcess.length-m_sSkipUntil.length();
					//System.out.println("Writing: " + iLen + " bytes skip length="+m_sSkipUntil.length());
					//System.out.println(new String(bufToProcess));
					byte bufRight[] = new byte[m_sSkipUntil.length()];
					//System.out.println("arrayCopy: " + (System.currentTimeMillis()-lStartLoop) );
					System.arraycopy(bufToProcess, iLen, bufRight, 0, bufRight.length);
					//System.out.println("copied: " + (System.currentTimeMillis()-lStartLoop) );
					m_dosCreate.write(bufToProcess, 0, iLen);
					//lTotalWritten += iLen;
					/*double dDiffSec = (System.currentTimeMillis() - lStart)/60;
            double dKBPerSec = (lTotalWritten/dDiffSec)/1024;
            System.out.println("Writing at " + dKBPerSec + "/s");
					 */
					//System.out.println("written: " + (System.currentTimeMillis()-lStartLoop) );
					bufToProcess = bufRight;
				}
				//System.out.println(" end: " + (System.currentTimeMillis()-lStartLoop) );
			}//while

		}//try
		catch(Exception e)
		{      
			pSystem.doError("Document.Construct", new String[]{sPageName, e.toString()}, pSystem);
			e.printStackTrace();
			this.addItemOnCreate(Document.ERROR_FIELD_NAME, e.toString());
			m_bDocumentCreatedOK = false;
			if(m_fCreateFile!=null) m_fCreateFile.delete();
			this.removeAllItems();
		}

		if(fout!=null)
		{
			try
			{
				fout.flush();
				fout.close();
			}catch(IOException ioe){}
		}

		//System.out.println("Constructed");
	}

	/**
	 * Compares the items on two documents and determines if they are the same
	 */
	public boolean equals(Document docToCompare)
	{      
		if(docToCompare==null) return false;
		if(getItemCount() != docToCompare.getItemCount()) return false; //columns don't match
		//maybe exclude this check...
		//if(!m_sTableName.equalsIgnoreCase(rToCompare.getTableName())) return false; //table name doesn't match

		Enumeration<DocumentItem> en = m_htItems.elements();
		while(en.hasMoreElements())
		{
			DocumentItem diThis = en.nextElement();
			DocumentItem diThat = docToCompare.getItem(diThis.getName());
			if(diThat==null) return false;
			if(!diThis.equals(diThat)) return false;
		}

		return true;
	}

	/**
	 * 
	 * @param buffer
	 * @param iPos
	 * @param sBoundary
	 * @param sCharset
	 * @return
	 * @throws Exception
	 */
	private byte[] processBuffer(byte buffer[], int iPos, String sBoundary, String sCharset) throws Exception
	{
		if(sCharset==null) sCharset = HTTP.DEFAULT_CHAR_ENCODING;
		byte bufLeftOver[]=null;
		String BODY_START = "\r\n\r\n";
		//String s,b;

		bufLeftOver = new byte[buffer.length-(iPos+m_sSkipUntil.length())];
		System.arraycopy(buffer, (iPos+m_sSkipUntil.length()), bufLeftOver, 0, bufLeftOver.length);

		//System.out.println("processBuffer() SKIP UNTIL: " + m_sSkipUntil);
		if(m_sSkipUntil.equals(sBoundary)) //start of next chunk so dissect
		{
			m_sSkipUntil = BODY_START;
			if(iPos==0) return bufLeftOver;
			/*int iCopySize = iPos-2; //-2 to cater for \r\n
			if(iCopySize<0) iCopySize = 0;
			byte bufToProcess[] = new byte[iCopySize]; 
			System.arraycopy(buffer, 0, bufToProcess, 0, iCopySize);
			*/
			
			if(iPos-2<0)
			{
				System.err.println("Document.java 465: processBuffer() iPos="+iPos + " NextBytes=" + Integer.toHexString(buffer[0]) + " " + Integer.toHexString(buffer[1])  + " " + Integer.toHexString(buffer[2]));
			}
			//FIXME safari causes a negative arraysize error when uploading a 55,594 byte file
			byte bufToProcess[] = new byte[iPos-2]; //-2 to cater for \r\n
			System.arraycopy(buffer, 0, bufToProcess, 0, iPos-2);

			if(m_dosCreate==null)
			{        
				String sItemValue = new String(bufToProcess, sCharset);
				//System.out.println(m_sCreateItemName + "=[" + sItemValue + "] " + sCharset);
				addItemOnCreate(m_sCreateItemName, sItemValue);
			}
			else
			{
				m_dosCreate.write(bufToProcess);
				m_dosCreate.flush();
				m_dosCreate.close();
				m_dosCreate=null;
				if(hasItem(m_sCreateItemName))
					appendFileItem(m_sCreateItemName, m_sCreateFileName, m_fCreateFile, m_sCreateMimeType, m_sCreateTransferEncoding);
				else
					replaceItem(m_sCreateItemName, m_sCreateFileName, m_fCreateFile, m_sCreateMimeType, m_sCreateTransferEncoding);
				m_sCreateItemName=null;
				m_sCreateFileName=null;
				m_fCreateFile=null;
				m_sCreateMimeType=null;
				m_sCreateTransferEncoding=null;
			}

			return bufLeftOver;
		}

		if(m_sSkipUntil.equals(BODY_START)) //must be a header
		{
			m_sSkipUntil = sBoundary;
			ArrayList vHeader = new ArrayList();
			String szLine;
			byte bufToProcess[] = new byte[iPos];
			System.arraycopy(buffer, 0, bufToProcess, 0, iPos);			
			BufferedReader br = new BufferedReader( new StringReader(new String(bufToProcess, sCharset)));

			while(true)
			{
				try{ szLine = br.readLine(); }
				catch(Exception e){szLine=null;}
				if(szLine==null) break;
				//System.out.println(szLine);
				if(szLine.length()!=0) vHeader.add(szLine);
			}
			String szContentDisposition = Util.getMIMELine(vHeader, "Content-Disposition");
			String szItemName = Util.getMIMELineValue(szContentDisposition, "name");
			if(szItemName==null) m_sCreateItemName="";  else m_sCreateItemName=szItemName;
			if(isMIMEFile(vHeader))
			{
				//System.out.println("File: " + Util.getMIMELineValue(szContentDisposition, "filename"));
				m_fCreateFile = File.createTempFile(String.valueOf(pSystem.getSystemUniqueNumber())+"_doc_", null, pSystem.getTempDir());
				m_dosCreate = new BufferedOutputStream(new FileOutputStream(m_fCreateFile), 102400);
				m_sCreateMimeType = Util.getMIMELine(vHeader, "Content-Type");
				m_sCreateTransferEncoding = Util.getMIMELine(vHeader, "Content-Transfer-Encoding");
				m_sCreateFileName = Util.getMIMELineValue(szContentDisposition, "filename");
				//fix for safari3 BJU 31/10/2007. Safari 3 is not reporting content-type for file attachments :-( Grrrrrr!
				if(m_sCreateMimeType==null) m_sCreateMimeType = "application/octet-stream";
			}
			else
			{
				//System.out.println("Item: " + szItemName);
				m_dosCreate = null; //just in case it's still open
				m_fCreateFile = null;
				m_sCreateFileName=null;
				m_fCreateFile=null;
				m_sCreateMimeType=null;
			}
			//System.out.println( new String(bufToProcess) );
			return bufLeftOver;
		}

		return buffer;
	}

	/**
	 * Determines if there were any creation errors (eg I/O errors) during document creation
	 * This is particularly likely in a large multipart mime document
	 */
	public boolean isDocumentCreatedOK()
	{
		return m_bDocumentCreatedOK;
	}

	/**
	 * Assume that if we have a content-type
	 */
	private boolean isMIMEFile(ArrayList v)
	{
		//if(Util.getMIMELine(v, "Content-Type")==null)

		String sDisposition = Util.getMIMELine(v, "Content-Disposition");
		//System.out.println("sDisposition=" + sDisposition);
		if(sDisposition==null) return false;

		//if a filename is supplied, use it. Fix for Safari 3
		String sFileName = Util.getMIMELineValue(sDisposition, "filename");
		//System.out.println("filename=" + sFileName);
		if(sFileName!=null) return true;

		return false;
	}

	/**
	 * Used internally to add an item if it doesn't exist.
	 * @param sItemName
	 * @param sItemValue
	 */
	protected void addItem(String sItemName, String sItemValue, boolean bIsMultiValued)
	{
		if(sItemName==null || sItemName.length()==0) return;
		//System.out.println("adding: "+szItemName + " value: " + szItemValue);
		if(!hasItem(sItemName)) 
		{
			if(bIsMultiValued)
			{
				replaceItem(sItemName, new String[]{sItemValue});
				//ArrayList arr = Util.splitString(sItemValue, ',');
				//replaceItem(sItemName, Util.objectArrayToStringArray(arr.toArray()));
			}
			else
				replaceItem(sItemName, sItemValue);
		}
	}

	/**
	 * Used internally to add an item if it doesn't exist. If the item exists, then assume we
	 * are dealing with a multi-valued item.
	 * @param sItemName
	 * @param sItemValue
	 */
	private void addItemOnCreate(String sItemName, String sItemValue)
	{
		if(sItemName==null || sItemName.length()==0) return;


		if(!hasItem(sItemName))
		{
			replaceItem(sItemName, sItemValue);
		}
		else
		{
			DocumentMultiItem dmi;
			DocumentItem di = getItem(sItemName);
			if(di.getType()!=DocumentItem.ITEM_TYPE_MULTI)
			{
				String sOldValue = di.getStringValue();
				removeItem(sItemName);
				//dmi = new DocumentMultiItem(this, sItemName, sOldValue);
				dmi = new DocumentMultiItem(this, sItemName, new String[] {sOldValue});
				dmi.appendValue(sItemValue);
			}
			else
			{
				dmi = (DocumentMultiItem)di;
				dmi.appendValue(sItemValue);
			}
		}

		/*String s = this.getItemValue(szItemName);
     System.out.println(szItemName);
     for(int i=0; i<s.length(); i++) System.out.print(" " + Integer.toHexString(s.charAt(i)));
     System.out.println(""); */

	}

	/**
	 *
	 */
	public DocumentItem replaceItem(String paramItemName, boolean bNewValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);

		if(paramItemName.length()==0) return null;
		DocumentItem item;// = new DocumentItem(this, paramItemName, paramItemValue);

		item = getItem(paramItemName);
		if(item==null)
		{
			item = new DocumentItem(this, paramItemName, bNewValue);
		}
		else
		{
			if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			{
				DocumentMultiItem dmi = (DocumentMultiItem)item;
				dmi.setMultiStringValue(String.valueOf(bNewValue));
			}
			else
				item.setStringValue(String.valueOf(bNewValue));
		}

		return item;
	}


	/**
	 * Adds an item to the document. Only one item of each name per document! Note we should just
	 * remove the item because the item may contain choices that we want to keep!
	 */
	public DocumentItem replaceItem(String paramItemName, String paramItemValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);

		if(paramItemName.length()==0) return null;
		DocumentItem item;// = new DocumentItem(this, paramItemName, paramItemValue);

		item = getItem(paramItemName);
		if(item==null)
		{
			item = new DocumentItem(this, paramItemName, paramItemValue);
		}
		else
		{
			if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			{
				DocumentMultiItem dmi = (DocumentMultiItem)item;
				dmi.setMultiStringValue(paramItemValue);
			}
			else
				item.setStringValue(paramItemValue);
		}

		return item;
	}


	/**
	 * Adds an item to the document. Only one item of each name per document!
	 */
	public DocumentItem replaceItem(String paramItemName, java.util.Date paramItemValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);
		DocumentItem item;
		if(paramItemName.length()==0) return null;
		item = getItem(paramItemName);
		if(item==null)
		{
			item = new DocumentItem(this, paramItemName, paramItemValue);
		}
		else
		{
			item.setDateValue(paramItemValue);
		}
		return item;
	}


	/**
	 * Adds an item to the document. Only one item of each name per document!
	 */
	public DocumentItem replaceItem(String sItemName, long lValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);
		DocumentItem item;
		if(sItemName.length()==0) return null;
		item = getItem(sItemName);
		if(item==null)
		{
			//System.out.println("replaceItem("+paramItemName+",l) - new");
			item = new DocumentItem(this, sItemName, lValue);
		}
		else
		{
			if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			{
				((DocumentMultiItem)item).setValues(new String[]{String.valueOf(lValue)});
			}
			else
				//System.out.println("replaceItem("+paramItemName+",l) - exists");
				item.setIntegerValue(lValue);
		}
		return item;
	}

	/**
	 * Adds an item to the document. Only one item of each name per document!
	 */
	public DocumentItem replaceItem(String paramItemName, double paramItemValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);
		DocumentItem item;
		if(paramItemName.length()==0) return null;
		item = getItem(paramItemName);
		if(item==null)
		{
			item = new DocumentItem(this, paramItemName, paramItemValue);
		}
		else
		{
			item.setNumericValue(paramItemValue);
		}
		return item;
	}

	/**
	 * Adds an item to the document. Only one item of each name per document!
	 */
	public DocumentItem replaceItem(String paramItemName, byte[] paramItemValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);
		DocumentItem item;
		if(paramItemName.length()==0) return null;
		item = getItem(paramItemName);
		if(item==null)
		{
			item = new DocumentItem(this, paramItemName, paramItemValue);
		}
		else
		{
			item.setBufferValue(paramItemValue);
		}
		return item;
	}

	/**
	 * Adds an item to the document. Only one item of each name per document!
	 */
	public DocumentItem replaceItem(String paramItemName, Object paramItemValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);		
		DocumentItem item;
		if(paramItemName.length()==0) return null;
		item = getItem(paramItemName);
		if(item==null)
		{
			item = new DocumentObjectItem(this, paramItemName, paramItemValue);
		}
		else
		{
			if(item.getType()==DocumentItem.ITEM_TYPE_OBJECT)
			{
				((DocumentObjectItem)item).setObject(paramItemValue);
			}
			else
			{
				this.removeItem(paramItemName);
				item = new DocumentObjectItem(this, paramItemName, paramItemValue);
			}
		}
		return item;
	}


	public DocumentItem replaceItem(String paramItemName, String[] paramItemValues)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);
		DocumentItem item;
		if(paramItemName.length()==0) return null;
		item = getItem(paramItemName);
		if(item==null)
		{
			item = new DocumentMultiItem(this, paramItemName, paramItemValues);
		}
		else
		{
			if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			{
				((DocumentMultiItem)item).setValues(paramItemValues);
			}
			else
			{
				this.removeItem(paramItemName);
				item = new DocumentMultiItem(this, paramItemName, paramItemValues);
			}
		}
		return item;
	}


	/**
	 * Adds a null item to the document. Only one item of each name per document!
	 */
	public DocumentItem setItemNull(String paramItemName)
	{
		String sNull=null;
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);
		DocumentItem item;
		if(paramItemName.length()==0) return null;
		item = getItem(paramItemName);
		if(item==null)
		{
			item = new DocumentItem(this, paramItemName, sNull);
		}
		else
		{
			//maintain the item type!!
			item.setValue(null);
		}
		return item;
	}

	/**
	 * Adds an item to the document, replacing any by the same name that already exist
	 * @param diNewItem
	 */
	public void replaceItem(DocumentItem diNewItem)
	{
		String szKey = diNewItem.getName().toLowerCase();
		if(m_htItems.containsKey(szKey)) m_htItems.remove(szKey);
		m_htItems.put(szKey, diNewItem);
	}

	/**
	 * Sets the parameters to the document. "&ID=3&Name=John%20Smith&do=9"
	 * Also decodes the param values from the &Name=Value pairs
	 */
	public synchronized void setParameters(String szParams)
	{
		String szParamName="";
		String szParamValue="";
		String szDataChunk;

		StringTokenizer stk= new StringTokenizer(szParams, "&", false);
		while (stk.hasMoreTokens())
		{
			szParamName="";
			szParamValue="";
			szDataChunk = stk.nextToken();
			StringTokenizer tk2= new StringTokenizer(szDataChunk, "=", false);
			if(tk2.hasMoreTokens()) szParamName = tk2.nextToken();
			if(tk2.hasMoreTokens()) szParamValue = tk2.nextToken();
			try{
				szParamName=URLDecoder.decode(szParamName, "UTF-8"); //decode the string, remove %20 etc
			}catch(Exception e){}
			try{
				szParamValue=URLDecoder.decode(szParamValue, "UTF-8"); //decode the string, remove %20 etc
			}catch(Exception e){}

			addParameter(szParamName, szParamValue);
		}//while
	}

	/**
	 * Adds a parameter to the document. Only one param of each name per document!
	 */
	public Parameter addParameter(String paramParamName, String paramParamValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addParameter(%s, %s)", new String[]{paramParamName, paramParamValue}, pSystem);
		Parameter p=null;
		if(paramParamName.length()==0) return null;
		p = (Parameter)m_htParams.get(paramParamName.toLowerCase());
		if(p==null)
		{
			p = new Parameter(paramParamName, paramParamValue);
			m_htParams.put(paramParamName.toLowerCase(), p);
		}
		else//replace the found parameter with the new value.
		{
			p.Value = paramParamValue;
		}
		return p;
	}

	/**
	 * Removes a parameter from the document.
	 */
	public void removeParameter(String paramParamName)
	{
		if(paramParamName.length()==0) return;
		if(m_htParams.containsKey(paramParamName.toLowerCase()))
		{
			m_htParams.remove(paramParamName.toLowerCase());
		}
	}

	/**
	 * Gets a parameter from the document. Only one param of each name per document!
	 * A parameter is a part of the URL string &param1=value&param2=value etc
	 */
	public String getParameter(String paramParamName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getParameter(%s)", new String[]{paramParamName}, pSystem);
		Parameter p = (Parameter)m_htParams.get(paramParamName.toLowerCase());
		if(p!=null) return p.Value;
		return null;
	}

	/**
	 * Gets a URL parameter as a long value. This is a convenience method so programmers
	 * do not have to continually convert Strings to longs in a clumsy try/catch.
	 */
	public long getParameterInteger(String paramParamName)
	{      
		String sValue = getParameter(paramParamName);
		if(sValue==null) return 0;

		try
		{     
			//first try to parse with the servers number settings
			NumberFormat nf = NumberFormat.getInstance();
			Number n = nf.parse(sValue);        
			return n.longValue();
		}
		catch(Exception pe)
		{
			//ok, that failed so now try just a straight convert
			try
			{
				return (long)Double.parseDouble(sValue);
			}
			catch(NumberFormatException e)
			{
				//that bombed too, so return zero
			}
		}
		return 0;

		/*
      try
      {
          return Long.parseLong(getParameter(paramParamName));
      }
      catch(Exception e){}
      return 0;   
		 */   
	}


	/**
	 * Get the system context associated with this document. Caution: may be null.
	 */
	public SystemContext getSystemContext()
	{
		return pSystem;
	}

	/**
	 * Get the session context associated with this document. Caution: may be null.
	 */
	public SessionContext getSessionContext()
	{
		return pSession;
	}

	/**
	 * Adds an item to the document. Only one item of each name per document!
	 * @param sTransferEncoding 
	 */
	public DocumentItem replaceItem(String paramItemName, String paramItemValue, File obj, String paramMimeType, String sTransferEncoding)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s, FILE)", new String[]{paramItemName, paramItemValue}, pSystem);

		//System.out.println("replaceItem: " + paramItemName + " " + paramItemValue + " type:"+ paramMimeType);
		if(paramItemName.length()==0) return null;
		removeItem(paramItemName);
		DocumentItem item = new DocumentFileItem(this, paramItemName, paramItemValue, obj, paramMimeType, sTransferEncoding);

		return item;
	}

	public DocumentItem appendFileItem(String paramItemName, String sFileName, File obj, String paramMimeType, String sTransferEncoding)
	{
		DocumentItem di = getItem(paramItemName);
		if(!(di instanceof DocumentFileItem))
		{
			return replaceItem(paramItemName, sFileName, obj, paramMimeType, sTransferEncoding);			
		}

		DocumentFileItem dfi = (DocumentFileItem)di;
		dfi.appendFile(sFileName, obj, paramMimeType, sTransferEncoding);

		return dfi;
	}


	/**
	 * Adds an item to the document. Only one item of each name per document!
	 */
	public DocumentMultiItem replaceMultiItem(String paramItemName, String paramItemValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addMulitItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);
		DocumentMultiItem item;
		if(paramItemName.length()==0) return null;
		item = getMultiItem(paramItemName);
		if(item==null)
		{
			item = new DocumentMultiItem(this, paramItemName, paramItemValue);
		}
		else
		{
			if(item.getType()!=DocumentItem.ITEM_TYPE_MULTI) item = null;
		}
		return item;
	}


	/**
	 * Removes the given item from the document and sets the item to null
	 * @return true if an item was removed
	 */
	public boolean removeItem(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "removeItem(%s)", new String[]{paramItemName}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item!=null)
		{
			m_htItems.remove(item.getName().toLowerCase());
			item=null;
			return true;
		}
		return false;
	}

	/**
	 * Associate an item with this document. Used for copying/moving items between documents.
	 * Will only add the reference if the item does not already exist.
	 */
	public boolean addItemReference(DocumentItem item)
	{
		if(item!=null)
		{
			String sKey = item.getName().toLowerCase();
			if(!m_htItems.containsKey(sKey)) 
			{
				m_htItems.put(sKey, item);      
				return true;
			}
		}
		return false;
	}

	/**
	 * Removes the reference to the given item from the document, don't destroy the 
	 * documentitem. 
	 * @return true if an item reference was removed
	 */
	public boolean removeItemReference(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "removeItem(%s)", new String[]{paramItemName}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item!=null)
		{
			m_htItems.remove(item.getName().toLowerCase());      
			return true;
		}
		return false;
	}

	/**
	 * Removes all items from the document
	 */
	public void removeAllItems()
	{
		m_htItems.clear();
	}

	/**
	 * Copies all items to the destination document
	 * @param docDestination
	 */
	public void copyAllItems(Document docDestination)
	{
		Enumeration<DocumentItem> en = m_htItems.elements();
		while(en.hasMoreElements())
		{
			DocumentItem di = en.nextElement();
			if(di!=null)
			{
				DocumentItem diNew = (DocumentItem)di.clone();
				diNew.m_docParent = docDestination;
				docDestination.replaceItem(diNew);
			}
		}
	}

	/**
	 * Copies all items to the destination document that don't already exist
	 * on the destination
	 * @param docDestination
	 */
	public void copyAllNewItems(Document docDestination)
	{
		Enumeration<DocumentItem> en = m_htItems.elements();
		while(en.hasMoreElements())
		{
			DocumentItem di = en.nextElement();
			if(di!=null)
			{
				if(!docDestination.hasItem(di.getName()))
				{
					DocumentItem diNew = (DocumentItem)di.clone();
					diNew.m_docParent = docDestination;
					docDestination.replaceItem(diNew);            
				}
			}
		}
	}


	/**
	 * Get an item from the document. An Item is a field (eg text, radio, checkbox) usually specified 
	 * as part of the design template, but can also be added straight onto the document.
	 */
	public DocumentItem getItem(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getItem(%s)", new String[]{paramItemName}, pSystem);
		DocumentItem item = (DocumentItem)m_htItems.get(paramItemName.toLowerCase());
		return item;
	}

	/**
	 *
	 *
	 */
	public Object getItemObject(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getItem(%s)", new String[]{paramItemName}, pSystem);
		DocumentItem item = getItem(paramItemName);//(DocumentItem)htItems.get(paramItemName.toLowerCase());
		if(item!=null)
		{
			if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			{
				DocumentMultiItem dmi = (DocumentMultiItem)item;
				return dmi.getObject();
			}
			if(item.getType()==DocumentItem.ITEM_TYPE_FILE)
			{
				DocumentFileItem dfi = (DocumentFileItem)item;
				return dfi.getObject();
			}
			return item.getObject();
		}

		return null;
	}


	/**
	 * Gets a multivalued item. If the item is not multivalued, it will return null.   
	 */
	public DocumentMultiItem getMultiItem(String paramItemName)
	{
		/* DocumentMultiItem item = null;    
    DocumentItem di = (DocumentItem)htItems.get(paramItemName.toLowerCase());
    if(di!=null && di.getType()==DocumentItem.ITEM_TYPE_MULTI) item = (DocumentMultiItem)htItems.get(paramItemName.toLowerCase());    
    return item;
		 */

		DocumentItem di = getItem(paramItemName);
		if(di!=null && di.getType()==DocumentItem.ITEM_TYPE_MULTI) return (DocumentMultiItem)di;

		return null;
	}

	/**
	 * Gets a file item. If the item is not a file, it will return null.   
	 */
	public DocumentFileItem getFileItem(String paramItemName)
	{
		DocumentItem di = getItem(paramItemName);
		//System.out.println("type="+di.getType() + " instanceof=" + (di instanceof DocumentFileItem));
		if(di!=null && di.getType()==DocumentItem.ITEM_TYPE_FILE) return (DocumentFileItem)di;

		return null;
	}

	/**
	 * Gets a file item. If the item is not a file, it will return null.   
	 */
	public DocumentObjectItem getObjectItem(String paramItemName)
	{
		DocumentItem di = getItem(paramItemName);
		if(di!=null && di.getType()==DocumentItem.ITEM_TYPE_OBJECT) return (DocumentObjectItem)di;

		return null;
	}

	/**
	 * Gets the string value stored in the item.
	 * @return null if item not found
	 */
	public String getItemValue(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getItemValue(%s)", new String[]{paramItemName}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item==null) return null;
		return item.getStringValue();
	}

	/**
	 * Determines if the named item is null. Will also return true if the item does not exist
	 */
	public boolean isItemNull(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getItemValue(%s)", new String[]{paramItemName}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item==null) return true;
		return item.isNull();
	}

	/**
	 *
	 */
	public String[] getItemChoices(String sItemName)
	{
		DocumentItem item = getItem(sItemName);
		if(item==null) return null;
		return item.getChoices();
	}

	/**
	 * Gets the string value stored in the item.
	 * @return null if item not found
	 */
	public java.util.Date getItemDateValue(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getItemValue(%s)", new String[]{paramItemName}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item==null) return null;
		return item.getDateValue();
	}

	/**
	 * Gets the string value stored in the item.
	 * @return null if item not found
	 */
	public long getItemIntegerValue(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getItemValue(%s)", new String[]{paramItemName}, pSystem);
		/*DocumentItem item = (DocumentItem)htItems.get(paramItemName.toLowerCase());
    if(item==null) return 0;
    return item.getIntegerValue();
		 */
		return (long)getItemNumericValue(paramItemName);
	}

	/**
	 * null or zero will return false. "true", "yes" or nonzero will return true
	 */
	public boolean getItemBooleanValue(String paramItemName)
	{
		if(isItemNull(paramItemName)) return false;


		String sVal = getItemValue(paramItemName);
		if(sVal.equalsIgnoreCase("true")) return true;
		if(sVal.equalsIgnoreCase("yes")) return true;
		long lNum = getItemIntegerValue(paramItemName);
		if(lNum!=0) return true;

		return false;
	}

	/**
	 * Sets the string value stored in the item.
	 * @return true if item contents was modified
	 */
	public boolean setItemValue(String paramItemName, String NewValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "setItemValue(%s, %s)", new String[]{paramItemName, NewValue}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item!=null)
		{
			//System.out.println("1. setItemValue(s,s): "+item.getName() + " [" + NewValue + "]");
			if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			{
				DocumentMultiItem dmi = (DocumentMultiItem)item;				
				dmi.setMultiStringValue(NewValue);
				//System.out.println("2. setItemValue(s,s): "+dmi.getName() + " [" + dmi.getStringValue() + "]");
			}
			else
				item.setStringValue(NewValue);
			//item.setStringValue(NewValue);
			return true;
		}
		return false;
	}


	/**
	 * 
	 * @param paramItemName
	 * @param newValues
	 * @return
	 */
	public boolean setItemValue(String paramItemName, String[] newValues)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "setItemValue(%s, %s)", new String[]{paramItemName, NewValue}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item!=null)
		{
			//System.out.println("setItemValue("+paramItemName+",s[])");
			if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			{
				//System.out.println("setItemValue("+paramItemName+",s[]) MULTI");
				DocumentMultiItem dmi = (DocumentMultiItem)item;
				dmi.setValues(newValues);
			}
			else
			{

				String sChoices[] = getItemChoices(paramItemName);
				//System.out.println("setItemValue("+paramItemName+",s[]) SINGLE " + Util.implode(sChoices, ", "));

				item = new DocumentMultiItem(this, paramItemName, newValues);
				item.setChoices(sChoices);	
				//System.out.println("setItemValue("+item.getName()+",s[]) SINGLE " + Util.implode(item.getChoices(), ", ") + " val="+item.getStringValue());

			}
			return true;
		}
		return false;
	}

	/**
	 *
	 */
	public boolean setItemValue(String paramItemName, boolean bNewValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "setItemValue(%s, %s)", new String[]{paramItemName, NewValue}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item!=null)
		{
			item.setStringValue(String.valueOf(bNewValue));
			return true;
		}
		return false;
	}

	/**
	 * Sets the string value stored in the item.
	 * @return true if item contents was modified
	 */
	public boolean setItemValue(String paramItemName, java.util.Date NewValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "setItemValue(%s, %s)", new String[]{paramItemName, NewValue}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item!=null)
		{
			item.setDateValue(NewValue);  
			return true;
		}

		return false;
	}

	/**
	 * Sets the string value stored in the item.
	 * @return true if item contents was modified
	 */
	public boolean setItemValue(String paramItemName, double NewValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "setItemValue(%s, %s)", new String[]{paramItemName, NewValue}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item!=null)
		{
			item.setNumericValue(NewValue);  
			return true;
		}

		return false;
	}

	/**
	 * Sets the string value stored in the item.
	 * @return true if item contents was modified
	 */
	public boolean setItemValue(String sItemName, long lValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "setItemValue(%s, %s)", new String[]{paramItemName, NewValue}, pSystem);
		DocumentItem item = getItem(sItemName);
		if(item!=null)
		{
			if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			{
				((DocumentMultiItem)item).setValues(new String[]{String.valueOf(lValue)});
			}
			else
				item.setIntegerValue(lValue);
			return true;
		}

		return false;
	}

	/**
	 * Adds an item to the document. Only one item of each name per document!
	 */
	public boolean setItemValue(String sItemName, Object objValue)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "addItem(%s, %s)", new String[]{paramItemName, paramItemValue}, pSystem);
		DocumentItem item = getItem(sItemName);
		if(item!=null)
		{
			if(item.getType()==DocumentItem.ITEM_TYPE_OBJECT)
			{
				((DocumentObjectItem)item).setObject(objValue);
			}
			else
			{
				this.removeItem(sItemName);
				item = new DocumentObjectItem(this, sItemName, objValue);
			}
			return true;
		}
		return false;
	}

	/**
	 * Convenience method for setting choices from a String "1,2,3|a,4|b" etc
	 */
	public boolean setItemChoices(String paramItemName, String sNewChoices)
	{
		String sChoices[]=null;
		ArrayList arr = puakma.util.Util.splitString(sNewChoices, ',');
		if(arr!=null) sChoices = puakma.util.Util.objectArrayToStringArray(arr.toArray());
		return setItemChoices(paramItemName, sChoices);
	}

	/**
	 * Set the choices associated with this item
	 */
	public boolean setItemChoices(String sItemName, String sNewChoices[])
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "setItemValue(%s, %s)", new String[]{paramItemName, NewValue}, pSystem);
		DocumentItem item = getItem(sItemName);
		if(item==null) 
		{			
			//replacing the item with an array makes this a multivalued array which may not be correct...
			replaceItem(sItemName, ""); //new String[]{""});
			setItemNull(sItemName);
			item = getItem(sItemName);
		}
		if(item==null) return false;

		item.setChoices(sNewChoices);  
		return true;

	}



	/**
	 * Gets the string value stored in the item.
	 * @return null if item not found
	 */
	public double getItemNumericValue(String paramItemName)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getNumericValue(%s)", new String[]{paramItemName}, pSystem);
		DocumentItem item = getItem(paramItemName);
		if(item==null) return 0;
		return item.getNumericValue();
	}

	/**
	 * Gets the string value stored in the item.
	 * @return null if item not found
	 */
	/*public Object getItemObject(String paramItemName)
  {
    //pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getItemObject(%s)", new String[]{paramItemName}, pSystem);
    DocumentItem item = (DocumentItem)htItems.get(paramItemName.toLowerCase());
    if(item==null) return null;
    return (Object)item.getData();
  }*/

	/**
	 * Determines if the item exists on the document
	 */
	public boolean hasItem(String paramItemName)
	{
		if(paramItemName==null) return false;
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "hasItem(%s)", new String[]{paramItemName}, pSystem);
		return m_htItems.containsKey(paramItemName.toLowerCase());
	}

	/**
	 * @return the int number of items attached to the document
	 */
	public int getItemCount()
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getItemCount()", pSystem);
		return m_htItems.size();
	}

	/**
	 * Gets all the items on this document
	 * @return an Enumeration of DocumentItem objects
	 */
	public Enumeration getAllItems()
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getAllItems()", pSystem);
		return m_htItems.elements();
	}

	/**
	 * @return the number of parameters attached to the document
	 */
	public int getParameterCount()
	{    
		return m_htParams.size();
	}

	/**
	 * Gets all the parameters on this document
	 * @return an Enumeration of Parameter objects
	 */
	public Enumeration getAllParameters()
	{
		return m_htParams.elements();
	}

	/**
	 * Returns the length of the data to be sent to the client
	 */
	public int getContentLength()
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getContentLength()", pSystem);
		if(Content==null) return 0;

		return Content.length;
	}

	/**
	 * Returns the length of the data to be sent to the client
	 */
	public String getContentType()
	{
		return ContentType;
	}

	/**
	 * Returns the actual content to be sent to the client
	 */
	public byte[] getContent()
	{
		return Content;
	}

	/**
	 * Return an XML representation of the document
	 * @return
	 */
	public StringBuilder toXML()
	{
		StringBuilder sbReturn = new StringBuilder(256);
		Enumeration en = m_htItems.elements();
		while(en.hasMoreElements())
		{
			DocumentItem item = (DocumentItem)en.nextElement();
			sbReturn.append(item.toXMLFragment());			
		}

		return sbReturn;
	}

	/**
	 * For debug purposes, returns a list of all the elements....
	 */
	public String toString()
	{
		StringBuilder sbReturn = new StringBuilder(64);
		Enumeration<DocumentItem> en = m_htItems.elements();
		sbReturn.append(m_htItems.size() + " items");
		while(en.hasMoreElements())
		{
			DocumentItem item = en.nextElement();
			sbReturn.append("\r\nName='" + item.getName() + "' Type=" + item.getType() + " Value='" + item.getStringValue() + "'");
		}
		return sbReturn.toString();
	}

	/**
	 * Gets the item off a document for sending.
	 */
	private DocumentMultiItem getRecipientItem(String szItemName)
	{
		DocumentItem item = getItem(szItemName);

		if(item==null) return null; //not found
		if(item.getType()==DocumentItem.ITEM_TYPE_MULTI)
			return getMultiItem(szItemName);

		String szValue = item.getStringValue();
		removeItem(szItemName);
		DocumentMultiItem itemMulti = replaceMultiItem(szItemName, null);
		MailAddress maList[] = MailAddress.parseMailAddresses(szValue, null);
		if(maList==null || maList.length==0) return itemMulti;

		for(int i=0; i<maList.length; i++)
		{
			if(maList[i].isValidAddressSyntax()) itemMulti.appendValue(maList[i].getFullOriginalAddress());
		}    

		return itemMulti;
	}

	public boolean send()
	{
		return send(true);
	}
	/**
	 * This function sends the document via email. You must add the following fields:
	 * 'From' - who the email is from
	 * 'Subject' - the subject of the email
	 * 'SendTo' - a multivalued item of who will be in the 'to:'
	 * 'CopyTo' - a multivalued item of who will be in the 'cc:'
	 * 'BlindCopyTo' - a multivalued item of who will be in the 'bcc:'
	 * 'Body' - The text of the message (html format??)
	 * Any FILE items on the document will (optionally) be sent as attachments
	 *
	 * Note: This function does not actually 'send' the document. It is written to a relational table
	 * and will be sent by the mailer addin task.
	 */
	public boolean send(boolean bSendAllFiles)
	{
		Connection cx = null;
		long CurrentTime = System.currentTimeMillis();
		long lMailBodyID=-1;
		DocumentMultiItem itSendTo = getRecipientItem(MAIL_TO_ITEM);
		DocumentMultiItem itCopyTo = getRecipientItem(MAIL_CC_ITEM);
		DocumentMultiItem itBlindCopyTo = getRecipientItem(MAIL_BCC_ITEM);
		String szSerialID=Long.toHexString(CurrentTime).toUpperCase();
		String sSubject = getItemValue(MAIL_SUBJECT_ITEM);
		if(sSubject==null) sSubject = "";

		//System.out.println(szSerialID);

		if(itSendTo==null) return false; //no recipients
		if(pSystem==null) return false;        

		try
		{
			Timestamp ts = new Timestamp(CurrentTime);
			cx = pSystem.getSystemConnection();
			String szQuery = "INSERT INTO MAILBODY(Subject,Sender,SentDate,Body,Flags,SendTo,CopyTo,SerialID) VALUES(?,?,?,?,?,?,?,?)";
			PreparedStatement prepStmt = cx.prepareStatement(szQuery);
			prepStmt.setString(1, sSubject);
			prepStmt.setString(2, getItemValue(MAIL_FROM_ITEM));
			prepStmt.setTimestamp(3, ts ); //updated
			prepStmt.setString(4, getItemValue(MAIL_BODY_ITEM));
			String sFlags = getItemValue(MAIL_FLAGS_ITEM);
			if(sFlags==null)
			{
				sFlags = "";
				String sRR = getItemValue(MAIL_RETURNRECEIPT_ITEM);
				if(sRR!=null && sRR.equals("1")) sFlags += "R";
				String sImportance = getItemValue(MAIL_IMPORTANCE_ITEM);
				if(sImportance!=null && sImportance.equals("1")) sFlags += "H";
			}
			if(sFlags.length()>10) sFlags=""; //check we'return not too big for the field
			prepStmt.setString(5, sFlags); //flags importance?
			prepStmt.setString(6, itSendTo.getStringValue(", "));
			if(itCopyTo==null)
				prepStmt.setString(7, "");
			else
				prepStmt.setString(7, itCopyTo.getStringValue(", "));
			prepStmt.setString(8, szSerialID);
			prepStmt.execute();
			prepStmt.close();

			//now find the body ID so we can link it to the header
			szQuery = "SELECT MailBodyID FROM MAILBODY WHERE Sender=? AND Subject=? AND SerialID=?";
			//System.out.println("from:["+getItemValue(MAIL_FROM_ITEM)+"]");
			//System.out.println("subject:["+sSubject+"]");
			//System.out.println("serialid:["+szSerialID+"]");
			//NOTE: serialid field def was only 10 chars on some systems! drop column and re-add.
			prepStmt = cx.prepareStatement(szQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			prepStmt.setString(1, getItemValue(MAIL_FROM_ITEM));
			prepStmt.setString(2, sSubject);
			prepStmt.setString(3, szSerialID);
			ResultSet RS = prepStmt.executeQuery();
			if(RS.next()) lMailBodyID = RS.getLong(1);
			RS.close();
			prepStmt.close();
		}
		catch (Exception sqle)
		{
			pSystem.doError("Document.MailSendError", new String[]{sqle.toString()}, this);
			pSystem.releaseSystemConnection(cx);
			sqle.printStackTrace();
			return false;
		}
		finally
		{
			pSystem.releaseSystemConnection(cx);
		}

		if(lMailBodyID==-1) 
		{
			//System.out.println("no mailbodyid!!");
			pSystem.releaseSystemConnection(cx);
			return false;
		}
		//now write the header...
		try
		{
			Hashtable<String, String> htUniqueRecipients = new Hashtable<String, String>();
			if(itSendTo!=null) addMailHeaderRecipients(cx, lMailBodyID, itSendTo.getValues(), htUniqueRecipients);
			if(itCopyTo!=null) addMailHeaderRecipients(cx, lMailBodyID, itCopyTo.getValues(), htUniqueRecipients);
			if(itBlindCopyTo!=null) addMailHeaderRecipients(cx, lMailBodyID, itBlindCopyTo.getValues(), htUniqueRecipients);
		}
		catch (Exception sqle2)
		{
			pSystem.doError("Document.MailSendError", new String[]{sqle2.toString()}, this);
			pSystem.releaseSystemConnection(cx);
			return false;
		}
		//finally write the attachments
		Enumeration en = m_htItems.elements();
		while(en.hasMoreElements())
		{
			DocumentItem item = (DocumentItem)en.nextElement();
			if(item instanceof DocumentFileItem)//item.getType()==DocumentItem.ITEM_TYPE_FILE && item.getStringValue().length()>0)
			{
				DocumentFileItem dfi = (DocumentFileItem)item;//this.getFileItem(item.getName());
				int iFiles = dfi.getFileCount();
				for(int i=0; i<iFiles; i++)
				{
					FileData fd = dfi.getFileData(i);
					File f = fd.file;
					String sMimeType = fd.mimeType;
					String sFileName = fd.fileName;
					/*File f = (File)dfi.getData();
					String sMimeType = dfi.getMimeType();
					String sFileName = item.getStringValue();*/
					try
					{
						String szQuery = "INSERT INTO MAILATTACHMENT(MailBodyID,FileName,Attachment,ContentType) VALUES(?,?,?,?)";
						PreparedStatement prepStmt = cx.prepareStatement(szQuery);
						prepStmt.setLong(1, lMailBodyID);
						prepStmt.setString(2, sFileName);
						
						//pSystem.doInformation("sending file: "+f.getName() + " " + f.length() + "bytes", this);
						FileInputStream fis = new FileInputStream(f);
						prepStmt.setBinaryStream(3, fis, (int)f.length() );
						prepStmt.setString(4, sMimeType);
						prepStmt.execute();
						prepStmt.close();
					}
					catch (Exception sqle3)
					{
						pSystem.doError("Document.MailSendError", new String[]{sqle3.getMessage()}, this);
						pSystem.releaseSystemConnection(cx);
						return false;
					}
				}//for
			}//if file item
		}//while

		return true;
	}


	/**
	 * Called for sendto, copyto, blindcopyto
	 */
	private void addMailHeaderRecipients(Connection cx, long lMailBodyID, Vector<String> vSendTo, Hashtable<String, String> htUniqueRecipients) throws Exception
	{    
		//System.out.println("addMailHeaderRecipients: " + vSendTo.size() + " " + vSendTo.toString());
		String szQuery = "INSERT INTO MAILHEADER(MailBodyID,Recipient,MessageStatus) VALUES(?,?,?)";
		for(int i=0; i<vSendTo.size(); i++)
		{
			MailAddress ma = new MailAddress(vSendTo.elementAt(i));
			if(ma.isValidAddressSyntax())
			{    
				String sAddress = ma.getFullParsedEmailAddress();
				if(!htUniqueRecipients.containsKey(sAddress.toLowerCase()))
				{
					htUniqueRecipients.put(sAddress.toLowerCase(), sAddress);
					PreparedStatement prepStmt = cx.prepareStatement(szQuery);
					prepStmt.setLong(1, lMailBodyID);
					prepStmt.setString(2, sAddress);
					prepStmt.setString(3, "P"); //pending - TODO: make a constant?
					prepStmt.execute();
					prepStmt.close();
				}
			}
			else
			{
				String sAddr = ma.getFullOriginalAddress();
				if(sAddr!=null && sAddr.length()>0) pSystem.doError("addMailHeaderRecipients(); Invalid address: [" + sAddr + "]", this);
			}
		}
	}

	public String getErrorSource()
	{
		return getClass().getName();
	}

	public String getErrorUser()
	{
		if(pSession==null)
			return pmaSystem.SYSTEM_ACCOUNT;
		else
			return pSession.getUserName();
	}


	/**
	 * Adds a cookie to this document
	 * Existing cookies are replaced!
	 */
	public void addCookie(Cookie ckNew)
	{
		String szKey = ckNew.getName().toLowerCase();
		if( m_htCookies.containsKey(szKey) )
		{
			m_htCookies.remove(szKey);
		}

		m_htCookies.put(szKey, (Cookie)ckNew.clone());
	}

	/**
	 * Removes a cookie from this document
	 *
	 */
	public void removeCookie(String szName)
	{
		String szKey = szName.toLowerCase();
		m_htCookies.remove(szKey);
	}

	/**
	 * Gets a cookie from this document
	 * @return null if the cookie does not exist
	 */
	public Cookie getCookie(String szName)
	{
		String szKey = szName.toLowerCase();
		if( m_htCookies.containsKey(szKey) )
		{
			return m_htCookies.get(szKey);
		}

		return null;
	}

	/**
	 * Determines if a cookie exists on this document
	 * @return false if the cookie does not exist
	 */
	public boolean hasCookie(String szName)
	{
		String szKey = szName.toLowerCase();
		if( m_htCookies.containsKey(szKey) )
		{
			return true;
		}

		return false;
	}


	/**
	 * returns all cookies stored in a vector
	 */
	public Vector getAllCookies()
	{
		Vector<Cookie> vReturn = new Vector<Cookie>();
		Enumeration<Cookie> en = m_htCookies.elements();
		while(en.hasMoreElements())
		{
			Cookie c = en.nextElement();
			vReturn.add(c);
		}

		return vReturn;
	}

	/**
	 * Make a copy of this object
	 */
	public Object clone() throws CloneNotSupportedException
	{
		return super.clone();
	}

	/**
	 * Adds each cookie line to the HTTP header
	 */
	public void setCookiesInHTTPHeader(ArrayList vHTTPHeader)
	{
		Enumeration<Cookie> en = m_htCookies.elements();
		while(en.hasMoreElements())
		{
			Cookie c = en.nextElement();
			String sCookie = c.getCookieString();
			if(sCookie!=null) vHTTPHeader.add(sCookie);
		}
	}
}


