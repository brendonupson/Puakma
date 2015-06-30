/** ***************************************************************
HTMLDocument.java
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
package puakma.addin.http.document;


import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import puakma.addin.http.HTTPRequestManager;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.addin.http.action.HTTPSessionContext;
import puakma.error.pmaLog;
import puakma.system.Document;
import puakma.system.DocumentItem;
import puakma.system.SystemContext;
import puakma.util.ByteStreamReader;
import puakma.util.Util;
/*
 * This is the html specific implementation of a Document
 */
public class HTMLDocument extends Document implements Cloneable
{
	//private ArrayList m_DocumentParts=new ArrayList();  
	//flag to show if this document contains file attachments etc.
	private boolean m_bHasRichItems=false;
	public DesignElement designObject=new DesignElement();
	private HTTPSessionContext m_sess;
	private Vector m_vExtraHeaders=null;  
	private boolean m_bDataPosted = false;
	private ArrayList m_arrParsedDocParts = null;


	public HTMLDocument() { }

	/**
	 * 
	 * @param paramSystem
	 * @param paramSession
	 *  @deprecated Use HTMLDocument(HTTPSessionContext paramSession) 
	 */
	public HTMLDocument(SystemContext paramSystem, HTTPSessionContext paramSession)
	{        
		super(paramSystem, paramSession.getSessionContext());    
		m_sess = paramSession;
	}

	/**
	 * For standard 'post' forms where parameter content is fred=1&john=2 etc
	 */
	public HTMLDocument(SystemContext paramSystem, HTTPSessionContext paramSession, String szPageName, ByteStreamReader is, String szContentType, int iContentLength, String sCharSet)
	{    
		super(paramSystem, paramSession.getSessionContext(), szPageName, is, szContentType, iContentLength, sCharSet);
		m_sess = paramSession;
		if(iContentLength>0) m_bDataPosted = true;
	}


	/**
	 * Mulitpart mime constructor
	 */
	public HTMLDocument(SystemContext paramSystem, HTTPSessionContext paramSession, String szPageName, ByteStreamReader is, String szContentType, String szBoundary, int iContentLength, String sCharSet)
	{    
		super(paramSystem, paramSession.getSessionContext(), szPageName, is, szContentType, szBoundary, iContentLength, sCharSet);
		if(iContentLength>0) m_bDataPosted = true;
		m_sess = paramSession;
	}

	public HTMLDocument(HTTPSessionContext paramSession) 
	{
		super(paramSession.getSystemContext(), paramSession.getSessionContext());    
		m_sess = paramSession;		
	}

	private ArrayList getParsedDocParts()
	{
		if(m_arrParsedDocParts==null)
		{
			if(designObject!=null) 
				m_arrParsedDocParts = designObject.getParsedDocumentParts(this, true);
			else
				m_arrParsedDocParts = new ArrayList();
		}

		return m_arrParsedDocParts;
	}

	/**
	 * Gets either the prepared document or the raw design data
	 */
	private byte[] getDesignData(boolean bReadMode)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getDesignData()", pSession);
		String sReturn="";
		int i;
		//int iHideLevel=0;
		HashMap hmHides = new HashMap();

		if(designObject!=null)
		{
			if(designObject.getContentType().toLowerCase().startsWith("text/")) //for xml && html
			{
				ArrayList arrDocParts = getParsedDocParts();
				StringBuilder sb = new StringBuilder(12500);
				for(i=0; i<arrDocParts.size(); i++)
				{
					Object o = arrDocParts.get(i);
					if(o instanceof String)
					{
						if(hmHides.size()==0) sb.append((String)o);
					}
					else //assume a field
					{
						HTMLControl htmlItem = (HTMLControl)o;
						String sItemName = htmlItem.getName().toLowerCase();
						if(htmlItem.getType()==HTMLControl.ITEM_TYPE_HIDESTART)
						{
							//System.out.println(sItemName+":"+getItemValue(sItemName));
							if(getItemBooleanValue(sItemName)) hmHides.put(sItemName, "");
						}
						if(htmlItem.getType()==HTMLControl.ITEM_TYPE_HIDEEND)
						{
							if(hmHides.containsKey(sItemName))
							{
								hmHides.remove(sItemName);
							}
						}
						if(hmHides.size()==0) sb.append(htmlItem.getHTML(bReadMode));
					}
				}        
				sReturn = sb.toString();
			}
			else
			{
				String sContentType = designObject.getContentType(); //"text/html; charset=utf-8"
				if(sContentType!=null && sContentType.length()>0)
				{
					String sCS = "charset=";
					int iPos = sContentType.toLowerCase().indexOf(sCS);
					if(iPos>=0)
					{
						//update the character set to use the one from the design element
						m_sCharEncoding = puakma.util.Util.trimSpaces(sContentType.substring(iPos+sCS.length()));
					}
				}
				return designObject.getContent();
			}
		}

		try{return sReturn.getBytes(m_sCharEncoding);}catch(Exception e){}
		m_sCharEncoding = puakma.addin.http.HTTP.DEFAULT_CHAR_ENCODING;
		return Util.utf8FromString(sReturn);//szReturn.getBytes();
	}

	/**
	 * @deprecated
	 */	
	public void prepare(HTTPRequestManager pHTTPrm)
	{
		prepare();
	}

	/**
	 * Splits the <P@ ... @P> placeholders into fields
	 */
	public void prepare()
	{
		if(m_sess==null) return;

		//long lStart = System.currentTimeMillis();
		//determine if a parent exists
		if(designObject!=null && !designObject.hasParsedDocumentParts())
		{
			String sParent = designObject.getParameterValue("ParentPage");
			if(sParent!=null && sParent.length()>0) //this belongs to a parent page
			{
				//get the parent raw data
				String sParentData = "";
				TornadoServerInstance tsi = TornadoServer.getInstance(m_sess.getSystemContext());
				DesignElement des = tsi.getDesignElement(rPath.Group, rPath.Application, Util.trimSpaces(sParent), DesignElement.DESIGN_TYPE_PAGE);
				if(des!=null)
					sParentData = Util.stringFromUTF8(des.getContent()); //new String(des.getContent());
				//get the child raw data
				String sChildData = Util.stringFromUTF8(designObject.getContent()); //new String(designObject.getContent());
				String sTag = "<p@childpage @p>";
				int iPos = sParentData.toLowerCase().indexOf(sTag);
				if(iPos>=0)
				{
					String sFirst = sParentData.substring(0, iPos);
					String sLastPart = sParentData.substring(iPos+sTag.length());
					sParentData = sFirst + sChildData + sLastPart;
				}
				//insert the child raw content into the parent

				/*DesignElement d2=null;              
				try{ d2 = (DesignElement)designObject.clone(); }catch(Exception e){}
				if(d2!=null)
				{
					d2.setDesignData(Util.utf8FromString(sParentData));
					this.designObject = d2;                  
				}*/
				//avoid a clone as the cached docparts will not be set properly
				this.designObject.setDesignData(Util.utf8FromString(sParentData));

			}//if parentpage
		}
		preparePage();
		//long lDiffMS = System.currentTimeMillis()-lStart;
		//if(designObject!=null) System.out.println(this.designObject + " prepare() took: "+lDiffMS + "ms, parts="+designObject.getParsedDocumentParts(true).size());
	}


	/**
	 * Prepare an individual page, parsing it into its component parts
	 */
	private void preparePage()
	{
		String sEndMarker=null;
		//m_DocumentParts=new ArrayList(50); //default to 50 parts		
		if(designObject!=null)
		{
			//TODO probably store one copy of the array to use later
			//this way makes a couple of clones which is a waste of memory and gc time
			//ArrayList arrParts = designObject.getParsedDocumentParts(this, false);


			if(designObject.getContentType().toLowerCase().startsWith("text/"))
			{				
				if(designObject.hasParsedDocumentParts())
				{			
					ArrayList arrDocParts = getParsedDocParts();
					//System.out.println("CACHED " + this.designObject);
					for(int i=0; i<arrDocParts.size(); i++)
					{
						Object obj = arrDocParts.get(i);
						if(obj instanceof HTMLControl)
						{
							HTMLControl dElement = (HTMLControl) obj;
							createDocumentItem(dElement);
						}
					}					
				}
				else
				{
					byte[] bufContent = designObject.getContent();
					int iLen = 0;
					if(bufContent!=null && bufContent.length>0) iLen = bufContent.length;
					StringBuilder sbHTML=new StringBuilder(iLen);
					String sField="";

					String sBuffer = "";
					if(bufContent!=null) sBuffer = insertSubPages(Util.stringFromUTF8(bufContent));

					int iPos;
					sbHTML.append(sBuffer); //in case not special tags
					iPos = sBuffer.indexOf(HTMLControl.FIELD_START);
					while(iPos>=0)
					{
						sbHTML.delete(0, sbHTML.length());
						sbHTML.append(sBuffer.substring(0, iPos));
						designObject.addParsedDocumentPart(sbHTML.toString());
						addParsedDocPart(sbHTML.toString());
						sBuffer = sBuffer.substring(iPos, sBuffer.length());
						sEndMarker = HTMLControl.FIELD_END;
						iPos = sBuffer.indexOf(sEndMarker);
						if(iPos<0)
						{
							//bugfix: If there was a start tag <P@ and no end tag @P> this will
							//fall into an infinite loop and run out of memory!!!
							sEndMarker = ">";
							iPos = sBuffer.indexOf(sEndMarker);
							if(iPos<0)
							{
								//still can't find it, use the remaining string
								sEndMarker = "";
								iPos = sBuffer.length();
							}
						}
						if(iPos>0)//we found an end tag
						{
							iPos += sEndMarker.length(); //skip past the tag
							sField = sBuffer.substring(0, iPos);
							sBuffer = sBuffer.substring(iPos, sBuffer.length());
							HTMLControl dElement = new HTMLControl(this, sField);
							designObject.addParsedDocumentPart(dElement);							
							createDocumentItem(dElement);
							addParsedDocPart(dElement);
						}
						iPos = sBuffer.indexOf(HTMLControl.FIELD_START);
						if(iPos<0) //saves a double buffer copy
						{
							sbHTML.delete(0, sbHTML.length());
							sbHTML.append(sBuffer);
						}
					}//while
					designObject.addParsedDocumentPart(sbHTML.toString());	
					addParsedDocPart(sbHTML.toString());
					//long lDiffMS = System.currentTimeMillis()-lStart;
					//System.out.println(this.designObject + " preparePage() took: "+lDiffMS + "ms, parts="+designObject.getParsedDocumentParts(true).size());
				}//else
			}//if design has html content 
		}//object not null

	}

	private void addParsedDocPart(Object obj) 
	{
		if(m_arrParsedDocParts==null) m_arrParsedDocParts = new ArrayList(50);
		m_arrParsedDocParts.add(obj);		
	}

	/**
	 * Populates the html page with related subpages. MAX_LEVELS determines how
	 * many levels deep the nesting can occur. This is to stop recursive pages that
	 * include the same pages.
	 */
	private String insertSubPages(String sMainPage)
	{
		pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "insertSubPages()", pSession);
		TornadoServerInstance tsi = TornadoServer.getInstance(m_sess.getSystemContext());
		int i, iPos, MAX_LEVELS=3;
		String sHTML, sField;
		String sTagStart=HTMLControl.FIELD_START + "Page";
		StringBuilder sb = new StringBuilder(8192);

		if(sMainPage==null || sMainPage.length()==0) return "";
		for(i=0; i<MAX_LEVELS; i++) //check for recursion
		{
			//sb = new StringBuilder(4096);
			sb.delete(0, sb.length());
			sHTML = sMainPage;
			iPos = sMainPage.indexOf(sTagStart);
			while(iPos>=0)
			{
				sHTML = sMainPage.substring(0, iPos);
				sb.append(sHTML);
				sMainPage = sMainPage.substring(iPos, sMainPage.length());
				iPos = sMainPage.indexOf(HTMLControl.FIELD_END);
				if(iPos>0)//we found an end tag
				{
					iPos += HTMLControl.FIELD_END.length(); //skip past the tag
					sField = sMainPage.substring(sTagStart.length(), iPos-HTMLControl.FIELD_END.length());
					sMainPage = sMainPage.substring(iPos, sMainPage.length());
					//get page called szField

					DesignElement des = tsi.getDesignElement(rPath.Group, rPath.Application, Util.trimSpaces(sField), DesignElement.DESIGN_TYPE_PAGE);					
					if(des!=null)
						sb.append(Util.stringFromUTF8(des.getContent()));//new String(des.getContent()));
					else
						sb.append("<!-- SUBPAGE_NOT_FOUND Name=" + Util.trimSpaces(sField) + " -->");
				}
				iPos = sMainPage.indexOf(sTagStart);
				sHTML = sMainPage;
			}//while
			sb.append(sHTML);
			sMainPage = sb.toString();
		}//for
		return sb.toString();
	}

	/**
	 * Render the document into bytes
	 */
	public void renderDocument(boolean bReadMode)
	{
		renderDocument(bReadMode, false);
	}

	/**
	 * Renders the document with the page design to produce all the bytes
	 * to be sent to the client
	 */
	public void renderDocument(boolean bReadMode, boolean bDebug)
	{		
		long lDesignDataLength=0L;

		if(designObject!=null)
		{
			ContentType = designObject.getContentType();
			if(!designObject.getContentType().startsWith("text/") )
			{
				lDesignDataLength = designObject.getDesignDataLength();
				byte data[] = designObject.getDesignData();
				Content = new byte[(int)lDesignDataLength];          
				if(lDesignDataLength>0) System.arraycopy(data, 0, Content, 0, (int)lDesignDataLength);
				return;
			}
		}

		if(bDebug) checkDocumentMatch();
		Content = getDesignData(bReadMode);    
	}



	/**
	 * Check that the document items match the design items. Report any differences
	 * via the server console.
	 */
	private void checkDocumentMatch()
	{
		ArrayList arrDocParts = getParsedDocParts();
		String sDesignName = rPath.getPathToDesign();
		//pSystem.doDebug(0, "doc2", pSession);
		for(int i=0; i<arrDocParts.size(); i++)
		{
			Object o = arrDocParts.get(i);
			if(!(o instanceof String))
			{
				HTMLControl it = (HTMLControl)o;
				String sName = it.getName();
				DocumentItem di = getItem(sName);
				if(di==null && sName!=null && sName.length()>0) pSystem.doDebug(0, sName + " does not exist on this document. [" + sDesignName + "]", pSession);
			}
		}
		Enumeration en = getAllItems();
		while(en.hasMoreElements())
		{
			DocumentItem di = (DocumentItem)en.nextElement();
			String sName = di.getName();
			boolean bHasItem = false;
			for(int i=0; i<arrDocParts.size(); i++)
			{
				Object o = arrDocParts.get(i);
				if(!(o instanceof String))
				{
					HTMLControl it = (HTMLControl)o;
					if(it.getName().equalsIgnoreCase(sName))
					{
						bHasItem=true;
						break;
					}
				}
			}//for
			if(!bHasItem && !sName.startsWith("@")) pSystem.doDebug(0, sName + " does not exist in this design [" + sDesignName + "]", pSession);
		}
	}



	public HTTPSessionContext getHTTPSessionContext()
	{
		return m_sess;
	}


	/**
	 * converts date or number items into the proper types, from String
	 * if the conversion fails, just leave the items as String
	 * @param it
	 */
	private void convertDateItem(HTMLControl hdi)
	{
		if(hdi==null) return;
		String sName = hdi.getName();

		int iItemType = hdi.getType();
		if(iItemType != HTMLControl.ITEM_TYPE_DATE &&
				iItemType != HTMLControl.ITEM_TYPE_COMPUTEDDATE) return;

		DocumentItem di = getItem(sName);
		if(di==null) return;
		if(di.getType()==DocumentItem.ITEM_TYPE_DATE) return; //already the correct type

		java.util.Date dtToProcess=null;
		String sFormat = hdi.getItemAttribute("format");
		if(m_sess!=null)
		{
			//if there is a session object of the same name as the format, use it
			String sObj = (String)m_sess.getSessionObject(sFormat);
			if(sObj!=null && sObj.length()>0) sFormat = sObj;
		}
		//SimpleDateFormat sdf;

		TimeZone tz = TimeZone.getDefault();
		if(m_sess!=null && m_sess.getTimeZone()!=null) tz = m_sess.getTimeZone();

		Locale locale = Locale.getDefault();
		if(m_sess!=null && m_sess.getLocale()!=null) locale = m_sess.getLocale();


		if(sFormat==null || sFormat.length()==0) sFormat = Util.SHORT_DATE;
		/*if(sFormat==null || sFormat.length()==0)
			sdf = new SimpleDateFormat();
		else		
			sdf = new SimpleDateFormat(sFormat, locale);
		 */

		//the exception handler will kick in if anything is wrong
		//then we just use the simple text setting
		try
		{
			String sDateValue = di.getStringValue();
			boolean bProcessed=false;
			//m_sess.getSystemContext().doDebug(0, "convertDateItem(): " + sName + " start conversion ["+szFormat+"] date=["+dtToProcess+"] val=["+sValue+"] type="+di.getType(), this);

			//todo check this !!!!
			//java.util.Date dtNow = new java.util.Date();
			Calendar cal = Calendar.getInstance(tz);				
			Date dtNow = cal.getTime();

			if(sDateValue.equalsIgnoreCase("today") || sDateValue.equalsIgnoreCase("now")) { bProcessed=true; dtToProcess = dtNow;}
			if(!bProcessed && sDateValue.equalsIgnoreCase("tomorrow")) { bProcessed=true; dtToProcess=puakma.util.Util.adjustDate(dtNow, 0, 0, 1, 0, 0, 0); }
			if(!bProcessed && sDateValue.equalsIgnoreCase("yesterday")) { bProcessed=true; dtToProcess=puakma.util.Util.adjustDate(dtNow, 0, 0, -1, 0, 0, 0); }
			if(!bProcessed && sDateValue.equalsIgnoreCase("nextweek")) { bProcessed=true; dtToProcess=puakma.util.Util.adjustDate(dtNow, 0, 0, 7, 0, 0, 0); }
			if(!bProcessed && sDateValue.equalsIgnoreCase("lastweek")) { bProcessed=true; dtToProcess=puakma.util.Util.adjustDate(dtNow, 0, 0, -7, 0, 0, 0); }
			if(!bProcessed && sDateValue.equalsIgnoreCase("nextmonth")) { bProcessed=true; dtToProcess=puakma.util.Util.adjustDate(dtNow, 0, 1, 0, 0, 0, 0); }
			if(!bProcessed && sDateValue.equalsIgnoreCase("lastmonth")) { bProcessed=true; dtToProcess=puakma.util.Util.adjustDate(dtNow, 0, -1, 0, 0, 0, 0); }
			if(!bProcessed && sDateValue.equalsIgnoreCase("nextyear")) { bProcessed=true; dtToProcess=puakma.util.Util.adjustDate(dtNow, 1, 0, 0, 0, 0, 0); }
			if(!bProcessed && sDateValue.equalsIgnoreCase("lastyear")) { bProcessed=true; dtToProcess=puakma.util.Util.adjustDate(dtNow, -1, 0, 0, 0, 0, 0); }

			/*sdf.setTimeZone(tz);
			if(!bProcessed) dtToProcess = sdf.parse(sValue);
			 */
			if(!bProcessed) dtToProcess = Util.makeDate(sDateValue, sFormat, locale, tz);

			//it.setValue(sdf.format(dtToProcess));
			//di.setDateValue(dtToProcess);
			//this.setItemValue(sName, dtToProcess);
			this.replaceItem(sName, dtToProcess);
			di = this.getItem(sName);
			if(di!=null) di.setDataFormat(sFormat);      
		}
		catch(Exception e)
		{
			//do nothing  
			//m_sess.getSystemContext().doError("convertDateItem(): " + e.toString(), this); 
		}



	}


	/**
	 * gets the HTML document item so we can do some manipulation to it.
	 * @param sItemName
	 * @return null if the item is not found
	 * @deprecated
	 */
	public HTMLDocumentItem getHTMLDocumentItem(String sItemName)
	{
		HTMLControl control = getHTMLControl(sItemName);
		if(control==null) return null;
		return new HTMLDocumentItem(control);
	}

	/**
	 * Gets a handle to the first named page control.
	 * @param sItemName
	 * @return
	 */
	public HTMLControl getHTMLControl(String sItemName)
	{
		if(designObject==null) return null;
		ArrayList arrDocParts = getParsedDocParts();
		for(int i=0; i<arrDocParts.size(); i++)
		{
			Object o = arrDocParts.get(i);
			if(!(o instanceof String))
			{
				HTMLControl it = (HTMLControl)o;
				if(it.getName().equalsIgnoreCase(sItemName)) return it;
			}
		}
		return null;
	}

	public void setHTMLControl(String sItemName, HTMLControl ctrl)
	{
		if(designObject==null) return;
		/*boolean bFound = false;
		ArrayList arrDocParts = getParsedDocParts();
		for(int i=0; i<arrDocParts.size(); i++)
		{
			Object o = arrDocParts.get(i);
			if(o instanceof HTMLControl)
			{
				HTMLControl it = (HTMLControl)o;
				if(it.getName().equalsIgnoreCase(sItemName)) 
				{
					designObject.setHTMLControl(sItemName, ctrl); 
					bFound = true;
				}
			}
		}//for
		
		
		if(!bFound) designObject.setHTMLControl(sItemName, ctrl);*/
		designObject.setHTMLControl(sItemName, ctrl);
	}

	/**
	 * Gets a handle to all the page controls by this name.
	 * @param sItemName
	 * @return
	 */
	public HTMLControl[] getAllHTMLControls(String sItemName)
	{
		if(designObject==null) return null;
		//FIXME ... changes here get lost :-/
		//m_pParsedDocParts = designObject.getParsedDocumentParts(this, true);
		ArrayList arrDocParts = getParsedDocParts();
		ArrayList arr = new ArrayList();
		for(int i=0; i<arrDocParts.size(); i++)
		{
			Object o = arrDocParts.get(i);
			if(o instanceof HTMLControl)
			{
				HTMLControl it = (HTMLControl)o;
				if(it.getName().equalsIgnoreCase(sItemName)) arr.add(it);
			}
		}
		if(arr.size()==0) return null;

		HTMLControl ctrls[] = new HTMLControl[arr.size()];
		for(int i=0; i<arr.size(); i++)
		{
			ctrls[i] = (HTMLControl) arr.get(i);
		}
		return ctrls;
	}

	/**
	 * Create the item on the underlying document if it doesn't exist
	 */
	private void createDocumentItem(HTMLControl hdi)
	{
		if(!m_bHasRichItems && hdi.isRichItem()) m_bHasRichItems = true;
		String sName = hdi.getName();
		if(sName==null || sName.length()==0) return;

		if(m_bDataPosted && hdi.getType()==HTMLControl.ITEM_TYPE_COMPUTEDPAGE) buildComputedPageFieldsFromPOST(hdi);
		//System.out.println("createDocumentItem(): hasItem " + hdi.getName() + "=" + this.hasItem(hdi.getName()));
		//System.out.println("createDocumentItem(): " + hdi.getName() + " value=" + this.getItemValue(hdi.getName()) + " type="+hdi.getType());
		if(hasItem(sName))
		{                            
			if(isItemNull(hdi.getName())) 
			{
				//System.out.println("ISNULL: "+hdi.getName() + " value=" + this.getItemValue(hdi.getName()));            
				//if(!(m_bDataPosted && (hdi.getType()==HTMLControl.ITEM_TYPE_CHECK || hdi.getType()==HTMLControl.ITEM_TYPE_LIST)))
				if(!(m_bDataPosted && hdi.isMultiValued()))
				{
					String sValues[] = Util.objectArrayToStringArray(Util.splitString(hdi.getDefaultValue(), ',').toArray());
					this.setItemValue(sName, sValues);
					//System.out.println("this.setItemValue("+sName+", "+hdi.getDefaultValue() +");" + getItem(sName).getType());
				}
			}
			//System.out.println("HASITEM: "+hdi.getName() + " value=" + this.getItemValue(hdi.getName()));            
			//convertItem(hdi);
		}
		else
		{
			int iItemType = hdi.getType();
			boolean bIsMultiValued = hdi.isMultiValued(); //hdi.getType()==HTMLControl.ITEM_TYPE_CHECK || hdi.getType()==HTMLControl.ITEM_TYPE_LIST;
			//22/6/05: BJU don't add parameters or cookies as items
			if(shouldCreateAsItem(m_bDataPosted, iItemType))
			{
				//System.out.println("SHOULDCREATE name=["+hdi.getName() + "] value=[" + hdi.getDefaultValue() + "] type="+hdi.getType() + " posted="+m_bDataPosted);
				if(m_bDataPosted && (hdi.getType()==HTMLControl.ITEM_TYPE_CHECK || hdi.getType()==HTMLControl.ITEM_TYPE_LIST))
					addItem(sName, "", bIsMultiValued);
				else
				{
					addItem(sName, hdi.getDefaultValue(), bIsMultiValued);
					//System.out.println("ADDED: name=["+hdi.getName() + "] value=[" + hdi.getDefaultValue() + "] type="+hdi.getType());
				}
			}//if shouldcreate()
		}//else


		convertDateItem(hdi);
	}

	/**
	 *
	 */
	private void buildComputedPageFieldsFromPOST(HTMLControl hdi)
	{
		//System.out.println("--- buildComputedPageFields ----");
		String sValue = getItemValue(hdi.getName());
		if(sValue==null) sValue = "";
		if(sValue!=null && sValue.length()>0)
		{
			DesignElement de = m_sess.getDesignObject(sValue, DesignElement.DESIGN_TYPE_PAGE);
			if(de==null) return;
			try
			{
				HTMLDocument docTemp = new HTMLDocument(m_sess);
				this.copyAllItems(docTemp);
				docTemp.setContent((byte[])null);                          
				docTemp.designObject = de;
				docTemp.prepare();
				boolean bReadMode = false;
				docTemp.renderDocument(bReadMode, false);
				docTemp.copyAllNewItems(this); 
				/*System.out.println(docTemp.toString());
              System.out.println("----------");
              System.out.println(super.toString());
				 */
				//convert existing item types
				Enumeration en = docTemp.getAllItems();
				while(en.hasMoreElements())
				{
					DocumentItem di = (DocumentItem)en.nextElement();
					if(di.getType()==DocumentItem.ITEM_TYPE_DATE) 
					{
						java.util.Date dt = docTemp.getItemDateValue(di.getName());
						this.replaceItem(di.getName(), dt);   
						//System.out.println("Converting date item " + di.getName());
					}
				}
			}
			catch(Exception e){}

		}
	}



	/**
	 * Determine if we should create this item on the document
	 */
	private boolean shouldCreateAsItem(boolean bDataPosted, int iItemType)
	{
		/*if(iItemType==HTMLDocumentItem.ITEM_TYPE_COOKIE) return false;
      if(iItemType==HTMLDocumentItem.ITEM_TYPE_PARAMETER) return false;
      if(iItemType==HTMLDocumentItem.ITEM_TYPE_VIEW) return false; */
		switch(iItemType)
		{
		case HTMLControl.ITEM_TYPE_COOKIE:
		case HTMLControl.ITEM_TYPE_PARAMETER:
		case HTMLControl.ITEM_TYPE_VIEW:
			return false;
		};

		return true;
	}


	/**
	 * This function creates a number of items on the document as defined by the page
	 * This is so the OpenAction action will be able to use those items
	 * @deprecated
	 * BJU 26/9/05
	 */
	public void setDocumentItemChoices(String szItemName, String szChoices)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "createDocumentItems()", pSession);
		/*int i;

    for(i=0; i<listDocParts.size(); i++)
    {
      Object o = listDocParts.get(i);
      if(!(o instanceof String))
      {
        HTMLDocumentItem it = (HTMLDocumentItem)o;
        if(it.getName().equalsIgnoreCase(szItemName))
        {
          it.setChoices(szChoices);
          return;
        }
      }
    }*/

		this.setItemChoices(szItemName, szChoices);
	}

	/**
	 * This function creates a number of items on the document as defined by the page
	 * This is so the OpenAction action will be able to use those items
	 * @deprecated
	 * BJU 26/9/05
	 */
	public void setDocumentItemChoices(String szItemName, String[] szChoices)
	{
		//pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "createDocumentItems()", pSession);
		/*int i;

    for(i=0; i<listDocParts.size(); i++)
    {
      Object o = listDocParts.get(i);
      if(!(o instanceof String))
      {
        HTMLDocumentItem it = (HTMLDocumentItem)o;
        if(it.getName().equalsIgnoreCase(szItemName))
        {
          it.setChoices(szChoices);
          return;
        }
      }
    }*/
		this.setItemChoices(szItemName, szChoices);

	}

	/**
	 * Sets a number of fields to the values of the http request passed.
	 * some browsers (like opera) split stuff across 2 lines
	 * The next related line starts with a space
	 * TODO: possible make this account for multiple split lines?
	 * Does any browser do this??
	 */
	public void setHTTPFields(ArrayList v)
	{				
		for(int i=0; i<v.size(); i++)
		{
			String szLine = (String)v.get(i);
			int iOffset = szLine.indexOf(':');
			if( iOffset > 0)
			{
				//+1 to skip the space after the : ie, "Content-Length: 25"
				String szName = "@" + szLine.substring(0, iOffset);
				String szValue = szLine.substring(iOffset+2, szLine.length());
				replaceItem(szName, szValue);
			}
		}//for
	}


	/**
	 * A short blurb so clients can tell what type of server they are connected to
	 */
	public String getServerInfoHTML()
	{
		return "<!-- " + pSystem.getVersionString() + " (" + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") + ") -->";
	}

	public String toString()
	{
		return toString(false);
	}


	/**
	 * Method to optionally allow the hiding of server created fields, eg those with a name that start with an "@"
	 * @param bHideServerFields
	 * @return
	 */
	public String toString(boolean bHideServerFields)
	{
		StringBuilder sb = new StringBuilder();
		Enumeration en = getAllItems();
		while(en.hasMoreElements())
		{
			DocumentItem di = (DocumentItem) en.nextElement();
			String sName = di.getName();
			if(sName==null) sName = "";
			boolean bShow = true;
			if(sName.startsWith("@") && bHideServerFields) bShow = false;

			if(bShow)
			{
				sb.append(di.toString() + "<br/>\r\n");
			}
		}

		return sb.toString();
		//return super.toString().replaceAll("\r\n", "<br/>");
	}

	/**
	 * Make a copy of this object
	 */
	public Object clone() throws CloneNotSupportedException
	{
		//return super.clone();
		Object obj = super.clone();
		if(obj instanceof HTMLDocument)
		{
			HTMLDocument doc = (HTMLDocument)obj;
			//removed the parsed parts so it can rejig its internals
			doc.m_arrParsedDocParts = null;
		}

		return obj;
	}

	/**
	 * Clears the parsed part cache so that on the next get of this page, it is reparsed
	 */
	public void removeParsedParts()
	{
		m_arrParsedDocParts = null;
	}

	/**
	 * Determines if this document has rich items (eg files or textareas). This determines
	 * if the post in the form tag should be mime or plain
	 */
	public boolean hasRichItems()
	{
		return m_bHasRichItems;
	}

	/**
	 * Generate the form tag
	 */
	public String getFormTagHTML()
	{    
		StringBuilder sbForm=new StringBuilder(150);
		//String szTo=rPath.getPathToDesign() + "?SavePage";
		sbForm.append("<form ");
		if(m_bHasRichItems) sbForm.append("enctype=\"multipart/form-data\" ");
		sbForm.append("name=\"");
		if(designObject!=null) sbForm.append(designObject.getDesignName());
		sbForm.append("\" method=\"post\" action=\"");
		sbForm.append(rPath.getPathToDesign());
		sbForm.append("?SavePage\"");      
		sbForm.append('>');
		return sbForm.toString();
	}

	/**
	 * The rich document is used when generating the form tags. Setting the parameter
	 * to true will cause the document to be uploaded to be "multipart/form-data", ie
	 * MIME encoded.
	 * @param bOnOff
	 */
	public synchronized void setIsRichDocument(boolean bOnOff)
	{
		m_bHasRichItems = bOnOff;
	}


	/**
	 * Determines if the document is in edit mode or not.
	 * @return true if we are in edit mode
	 */
	public boolean isEditMode()
	{
		/*if(rPath.Action.equalsIgnoreCase(DesignElement.PARAMETER_OPENPAGE))
			return true;
		else
			return false;*/
		//this fixes a bug where no action is specified eg /yourapp.pma/pagename page is displayed in 
		//editmore but this method returns false.
		if(rPath.Action.equalsIgnoreCase(DesignElement.PARAMETER_READPAGE)) return false;
		return true;
	}

	/**
	 * Makes a choices string suitable for a combo or listbox.
	 * @param sKey
	 * @param bSortByValue
	 * @return
	 */
	public String makeKeywordChoices(String sKey, boolean bSortByValue)
	{
		ArrayList vReturn=null;
		Connection cxSys=null;
		StringBuilder sbReturn=null;
		String szAppGroup=rPath.Group, szApplication=rPath.Application;
		String szOrderClause=" ORDER BY KeywordOrder,Data";

		if(sKey==null || szApplication.length()==0) return null;

		try
		{
			String szConnectJoin = "AND LOWER(KEYWORD.Name)='" + sKey.toLowerCase() + "'";
			String szGroupClause="";
			if(szAppGroup.length()==0)
				szGroupClause=" AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL)";
			else
				szGroupClause=" AND (LOWER(APPLICATION.AppGroup)='" + szAppGroup.toLowerCase() + "' OR APPLICATION.AppGroup='*')";
			if(bSortByValue) szOrderClause = " ORDER BY Data";
			String szQuery = "SELECT Data FROM APPLICATION,KEYWORD,KEYWORDDATA WHERE LOWER(APPLICATION.AppName)='" + szApplication.toLowerCase() + "' " + szGroupClause + " AND APPLICATION.AppID=KEYWORD.AppID AND KEYWORD.KeywordID=KEYWORDDATA.KeywordID " + szConnectJoin + szOrderClause;
			//System.out.println("[" + szQuery + "]");
			cxSys = pSystem.getSystemConnection();
			Statement Stmt = cxSys.createStatement();
			ResultSet RS = Stmt.executeQuery(szQuery);
			while(RS.next())
			{
				if(vReturn==null) vReturn = new ArrayList();
				vReturn.add(RS.getString("Data"));
			}
			RS.close();
			Stmt.close();
		}
		catch(Exception de)
		{
			pSystem.doError("HTTPRequest.getKeywordError", new String[]{sKey, de.toString()}, pSession);
		}
		finally
		{
			pSystem.releaseSystemConnection(cxSys);
		}
		if(vReturn==null) return "";
		sbReturn = new StringBuilder(4096);
		for(int i=0; i<vReturn.size(); i++)
		{
			if(sbReturn.length()>0) sbReturn.append(',');
			sbReturn.append((String)vReturn.get(i));
		}

		if(sbReturn.length()==0) return "";
		return sbReturn.toString();
	}

	/**
	 * Makes a choices string suitable for a combo or listbox.
	 * @param sKey
	 * @param bSortByValue
	 * @return
	 */
	public String[] makeKeywordChoicesArray(String sKey, boolean bSortByValue)
	{
		ArrayList vReturn=null;
		Connection cxSys=null; 
		Statement stmt = null;
		ResultSet rs = null;
		String sAppGroup=rPath.Group, sApplication=rPath.Application;
		String sOrderClause=" ORDER BY KeywordOrder,Data";

		pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getAllKeywordValues()", this);
		if(sKey==null || sApplication.length()==0) return null;

		try
		{
			String szConnectJoin = "AND LOWER(KEYWORD.Name)='" + sKey.toLowerCase() + "'";
			String szGroupClause="";
			if(sAppGroup.length()==0)
				szGroupClause=" AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL)";
			else
				szGroupClause=" AND (LOWER(APPLICATION.AppGroup)='" + sAppGroup.toLowerCase() + "' OR APPLICATION.AppGroup='*')";
			if(bSortByValue) sOrderClause = " ORDER BY Data";
			String szQuery = "SELECT Data FROM APPLICATION,KEYWORD,KEYWORDDATA WHERE LOWER(APPLICATION.AppName)='" + sApplication.toLowerCase() + "' " + szGroupClause + " AND APPLICATION.AppID=KEYWORD.AppID AND KEYWORD.KeywordID=KEYWORDDATA.KeywordID " + szConnectJoin + sOrderClause;
			//System.out.println("[" + szQuery + "]");
			cxSys = pSystem.getSystemConnection();
			stmt = cxSys.createStatement();
			rs = stmt.executeQuery(szQuery);
			while(rs.next())
			{
				if(vReturn==null) vReturn = new ArrayList();
				vReturn.add(rs.getString("Data"));
			}
		}
		catch(Exception de)
		{
			pSystem.doError("HTTPRequest.getKeywordError", new String[]{sKey, de.toString()}, pSession);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			pSystem.releaseSystemConnection(cxSys);
		}
		if(vReturn==null) return null;

		Object obj[] = vReturn.toArray();
		String sReturn[] = new String[obj.length];
		for(int i=0; i<obj.length; i++) sReturn[i] = (String)obj[i];
		return sReturn;
	}


	public Vector getExtraHeaders() 
	{
		return m_vExtraHeaders;
	} 

	/**
	 * reset in case we don't want these headers
	 */
	public void clearAllExtraHeaders()
	{
		m_vExtraHeaders.removeAllElements();
		m_vExtraHeaders=null;
	}

	/**
	 * @deprecated
	 * @param sName
	 * @param sValue
	 */
	public void setExtraHeaderValue(String sName, String sValue)
	{
		setExtraHeaderValue(sName, sValue, true);
	}


	/**
	 * Set an extra http reply header (eg "Via: xxxx"). Pass null as the first two parameters to 
	 * clear the list of extra headers
	 * @param sName
	 * @param sValue
	 * @param bReplaceExistingHeader
	 */
	public void setExtraHeaderValue(String sName, String sValue, boolean bReplaceExistingHeader)
	{
		if(sName==null && sValue==null)
		{
			if(m_vExtraHeaders!=null) m_vExtraHeaders.clear();
			return;
		}

		String sNewEntry = sName + ": " + sValue;
		if(m_vExtraHeaders==null) 
		{
			m_vExtraHeaders = new Vector();
			m_vExtraHeaders.add(sNewEntry);
			return;
		}

		if(bReplaceExistingHeader)
		{
			//remove all existing occurrences
			String sStartsWith = sName.trim().toLowerCase() + ':';
			for(int i=0; i<m_vExtraHeaders.size(); i++)
			{
				String sLine = (String)m_vExtraHeaders.get(i);
				if(sLine!=null && sLine.length()>0 && sLine.toLowerCase().startsWith(sStartsWith))
				{
					m_vExtraHeaders.remove(i);
					//m_vExtraHeaders.add(sNewEntry);
					//return;
				}
			}
		}
		//not found, so just add it
		m_vExtraHeaders.add(sNewEntry);
	}

	public void copyControls(HTMLDocument docDestination, boolean bOverwriteExisting) 
	{
		/*Enumeration en = getAllItems();
		while(en.hasMoreElements())
		{
			DocumentItem di = (DocumentItem)en.nextElement();
			String sItemName = di.getName();
			boolean bHasItem = docDestination.hasItem(sItemName);
			if((bHasItem && bOverwriteExisting) || !bHasItem)
			{
				//HTMLControl ctrls[] = getAllHTMLControls(sItemName);
				HTMLControl ctrl = getHTMLControl(sItemName);
				if(ctrl!=null)
				{
					docDestination.setHTMLControl(sItemName, ctrl);					
				}
			}

		}	*/
		if(designObject!=null) designObject.copyControls(docDestination, bOverwriteExisting);
	}


}//class