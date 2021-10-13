/* ***************************************************************
HTMLView.java
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

/**
 * HTMLView.java
 *
 * @created 13 April 2004, 11:41
 * <p>Used to render a list of records from a database in to an html block for 
 * display to users.</p>
 * <code>
 * HTMLView hView = new HTMLView(ActionDocument, "YourXSLResource.xsl", "SELECT * FROM ATABLE", CONNECTION_NAME, null, null); 
 * hView.setRowsPerView(100);
 * hView.setDocumentViewHTML("vwFieldName");
 * </code>
 *
 */

package puakma.addin.http.document;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.system.SystemContext;
import puakma.util.Util;

/**
 *
 * @author  bupson
 */
public class HTMLView 
{
	private static final String PARAM_JUMPTO = "JumpTo";
	private static final String SQL_ORDER_BY = " ORDER BY ";
	public static long DEFAULT_ROWS = 25;

	private HTMLDocument m_doc;
	private String m_sNextText="&gt;&gt;";
	private String m_sPrevText="&lt;&lt;";
	private String m_sConnName=null;
	private String m_sSQL=null;
	private String m_sXSLStyleSheet=null;
	private long m_lMaxRowsPerView=DEFAULT_ROWS;
	private long m_lAbsoluteMax=-1;//unlimited
	private TableManager m_tm;
	private HTTPSessionContext m_pSession;
	private int m_iNavigationStyle=0;
	private String m_sStart="0"; //the start offset
	private boolean m_bHasMoreResults=false;
	private long m_lCurrentViewRowCount=0; //counts the number of rows in the current result set	
	private String m_sJavascriptNextPrevMethod = null;

	public final static int NAVIGATION_STYLE_NONE=0;
	public final static int NAVIGATION_STYLE_ALPHA=1;

	private HTMLViewCallback m_objCallback = null;


	/** Creates a new instance of HTMLView */
	public HTMLView(HTMLDocument doc, String sXSLResource, String sSelect, String sConnection, String sNext, String sPrev) 
	{
		m_doc = doc;
		m_sNextText = sNext;
		m_sPrevText = sPrev;
		m_sConnName = sConnection;
		m_sSQL = sSelect; 
		m_sXSLStyleSheet = sXSLResource;
		m_pSession = doc.getHTTPSessionContext();
		m_tm = new TableManager(m_pSession, sConnection, "");  

		if(m_sPrevText==null || m_sPrevText.length()==0) m_sPrevText = doc.getSystemContext().getSystemMessageString("HTMLView.PreviousRecordset");
		if(m_sPrevText==null) m_sPrevText = "&lt;&lt;";
		if(m_sNextText==null || m_sNextText.length()==0) m_sNextText = doc.getSystemContext().getSystemMessageString("HTMLView.NextRecordset");
		if(m_sNextText==null) m_sNextText = "&gt;&gt;";
	}        


	/**
	 * Set the number of records to display per view
	 *
	 */
	public synchronized void setRowsPerView(long lRows)
	{
		m_lMaxRowsPerView = lRows;
		if(m_lMaxRowsPerView<1) m_lMaxRowsPerView = DEFAULT_ROWS;
	}

	/**
	 * Set the number of records to display per view
	 *
	 */
	public synchronized void setNavigationStyle(int iNewStyle)
	{
		m_iNavigationStyle = iNewStyle;
	}

	/**
	 * Set the javascript method to be used for the next, previous and jumpto links
	 * @param sJavascriptMethod
	 */
	public synchronized void setJavascriptNextPreviousMethod(String sJavascriptMethod)
	{		
		m_sJavascriptNextPrevMethod = sJavascriptMethod;
		if(m_sJavascriptNextPrevMethod!=null)
		{
			String sJS = "javascript:";
			if(m_sJavascriptNextPrevMethod.toLowerCase().startsWith(sJS)) m_sJavascriptNextPrevMethod = m_sJavascriptNextPrevMethod.substring(sJS.length());
		}
	}


	/**
	 * Set the absolute maximum number of rows to display, in case the user manually changes the start & max params
	 */
	public synchronized void setAbsoluteViewMax(long lMax)
	{
		m_lAbsoluteMax = lMax;
	}

	/**
	 * Replace a URL parameter with the value passed
	 */
	private String replaceParam(String sURL, String sParam, String sValue)
	{
		if(sURL==null || sParam==null) return sURL;
		if(sValue==null) sValue="";
		if(sURL.indexOf('?')<0) sURL += '?'; //eg may be "/app.pma/AccountView"
		int iPos = sURL.toLowerCase().indexOf(sParam.toLowerCase());
		if(iPos>0)
		{
			String sBase = sURL.substring(0, iPos);
			String sRemain = sURL.substring(iPos+sParam.length()+1);
			iPos = sRemain.indexOf('&');
			if(iPos>0)
			{
				sRemain = sRemain.substring(iPos);
			}
			else
				sRemain = "";
			sURL = sBase + sParam + sValue + sRemain;
		}
		else
		{
			sURL += sParam+sValue;
		}
		return sURL;
	}

	/**
	 * Replace a URL parameter with the value passed
	 */
	private String removeParam(String sURL, String sParam)
	{
		if(sURL==null || sParam==null) return sURL;        
		int iPos = sURL.toLowerCase().indexOf(sParam.toLowerCase());
		if(iPos>0)
		{
			String sBase = sURL.substring(0, iPos);
			String sRemain = sURL.substring(iPos+sParam.length()+1);
			iPos = sRemain.indexOf('&');
			if(iPos>0)
			{
				sRemain = sRemain.substring(iPos);
			}
			else
				sRemain = "";
			sURL = sBase + sRemain;
		}

		return sURL;
	}



	/**
	 * 
	 * @param sFieldName
	 * @return
	 */
	public String getViewHTML(String sFieldName)
	{
		if(m_lMaxRowsPerView>0)
		{
			long lStart=0;            
			StringBuilder sbJump = new StringBuilder(256);
			m_sStart = m_doc.getParameter("Start"+sFieldName);
			String sMax = m_doc.getParameter("Max"+sFieldName);  
			String sJumpTo = m_doc.getParameter(PARAM_JUMPTO+sFieldName);
			//System.out.println("jumpto="+sJumpTo);       

			if(sMax!=null && sMax.length()>=0)
			{
				try
				{
					//m_lMaxRowsPerView = Math.abs(Long.parseLong(sMax));
					setRowsPerView(Long.parseLong(sMax));
					if(m_lAbsoluteMax>0 && m_lMaxRowsPerView>m_lAbsoluteMax) m_lMaxRowsPerView=m_lAbsoluteMax;
				}
				catch(Exception me){}
			}

			if(sJumpTo!=null && sJumpTo.length()>0) //this has to be done first so we can work out the start offset for next/prev buttons
			{
				sbJump = doJumpList(sJumpTo, m_lMaxRowsPerView);          
			}

			if(m_sStart==null || m_sStart.length()==0) m_sStart = "0";

			try
			{
				lStart = Long.parseLong(m_sStart);
				if(lStart<0) lStart=0;
			}
			catch(Exception se){}

			//render the results
			StringBuilder sbViewHTML = doList(lStart, m_lMaxRowsPerView);

			long lPrev = lStart-m_lMaxRowsPerView;
			long lNext = lStart+m_lMaxRowsPerView;
			//long lMax = getMaxCount();
			String sPreviousLink=null;
			String sNextLink=null;
			String sURL = m_doc.rPath.getFullPath();
			sURL = removeParam(sURL, '&'+PARAM_JUMPTO+sFieldName+'=');
			if(lPrev<0) lPrev=0;
			if(lPrev>=0 && lStart>0) 
			{        
				if(m_sPrevText!=null)
				{       
					if(m_sJavascriptNextPrevMethod!=null && m_sJavascriptNextPrevMethod.length()>0)
					{
						String sMethod = adjustJavascriptMethod(m_sJavascriptNextPrevMethod, lPrev, m_lMaxRowsPerView, "", sFieldName);
						sPreviousLink = "<a class=\"viewPrev\" href=\"JavaScript:" + sMethod + "\">" + m_sPrevText + "</a>";
					}
					else
					{
						sURL = replaceParam(sURL, "&Max"+sFieldName+'=', String.valueOf(m_lMaxRowsPerView));
						sURL = replaceParam(sURL, "&Start"+sFieldName+'=', String.valueOf(lPrev));
						sPreviousLink = "<a class=\"viewPrev\" href=\"" + sURL + "\">" + m_sPrevText + "</a>";
					}
				}
			}

			if(m_bHasMoreResults) 
			{
				if(m_sNextText!=null)
				{        
					if(m_sJavascriptNextPrevMethod!=null && m_sJavascriptNextPrevMethod.length()>0)
					{
						String sMethod = adjustJavascriptMethod(m_sJavascriptNextPrevMethod, lNext, m_lMaxRowsPerView, "", sFieldName);
						sNextLink = "<a class=\"viewNext\" href=\"JavaScript:" + sMethod + "\">" + m_sNextText + "</a>";
					}
					else
					{
						sURL = replaceParam(sURL, "&Max"+sFieldName+'=', String.valueOf(m_lMaxRowsPerView));
						sURL = replaceParam(sURL, "&Start"+sFieldName+'=', String.valueOf(lNext));
						sNextLink = "<a class=\"viewNext\" href=\"" + sURL + "\">" + m_sNextText + "</a>";
					}
				}
			}      
			StringBuilder sbNextPrev = new StringBuilder(1024);
			if(sPreviousLink!=null || sNextLink!=null || m_iNavigationStyle!=NAVIGATION_STYLE_NONE)
			{          
				sbNextPrev.append("<tr>");
				sbNextPrev.append("<td class=\"viewPrev\" width=\"10%\">");
				if(sPreviousLink!=null) sbNextPrev.append(sPreviousLink);
				sbNextPrev.append("</td>");
				sbNextPrev.append("<td>");
				sbNextPrev.append(renderViewNavigation(sFieldName));
				sbNextPrev.append("</td>");
				sbNextPrev.append("<td class=\"viewNext\" width=\"10%\">");
				if(sNextLink!=null) sbNextPrev.append(sNextLink);
				sbNextPrev.append("</td>");
				sbNextPrev.append("</tr>\r\n");
			}
			StringBuilder sbData = new StringBuilder(2048);
			sbData.append("<table class=\"viewTable\">\r\n");

			if(sbNextPrev.length()>0) sbData.append(sbNextPrev); 
			if(sJumpTo!=null && sJumpTo.length()>0 && m_sSQL.indexOf(" ORDER BY ")>0)
				sbData.append("<tr><td colspan=\"3\">"+sbJump+"</td></tr>\r\n");
			else
				sbData.append("<tr><td colspan=\"3\">"+sbViewHTML+"</td></tr>\r\n");
			if(sbNextPrev.length()>0) sbData.append(sbNextPrev);

			sbData.append("</table>\r\n");
			return sbData.toString();
		}


		return doList(0, m_lMaxRowsPerView).toString();
	}

	/**
	 * renders the view's navigation controls based on the m_iNavigationStyle
	 */
	public StringBuilder renderViewNavigation(String sFieldName)
	{
		StringBuilder sbData = new StringBuilder(256);
		switch(m_iNavigationStyle)
		{
		case NAVIGATION_STYLE_NONE: 
			return sbData;
		case NAVIGATION_STYLE_ALPHA: 
			doAlphaViewNavigation(sFieldName, sbData);
			return sbData;
		default:
			//invalid selection.....?
		}
		return sbData;
	}

	/**
	 * prints A B C D E F G .... atop the view
	 */
	private void doAlphaViewNavigation(String sFieldName, StringBuilder sbData)
	{
		if(m_sSQL.toUpperCase().indexOf(SQL_ORDER_BY)<0) return; //we can only do a quick leap on sorted recordsets!


		String sURL = m_doc.rPath.getFullPath();
		String sJumpTo = m_doc.getParameter(PARAM_JUMPTO+sFieldName);
		if(sJumpTo==null) sJumpTo="";
		if(sJumpTo.length()>1) sJumpTo = sJumpTo.substring(0, 1);
		sURL = removeParam(sURL, "&Start"+sFieldName+'=');
		sURL = removeParam(sURL, '&'+PARAM_JUMPTO+sFieldName+'=');

		String sBaseAnchor = "<a class=\"vwNavQuickLinks\" href=\"" + sURL;
		if(sBaseAnchor.indexOf('?')<0) sBaseAnchor += '?';

		if(m_sJavascriptNextPrevMethod!=null && m_sJavascriptNextPrevMethod.length()>0)
			sBaseAnchor = "<a class=\"vwNavQuickLinks\" href=\"";

		sbData.append("<div class=\"vwNavQuickLinksContainer\">\r\n");
		char cLetter = 'A';        
		while(cLetter<='Z')
		{
			boolean bShowLink = true;
			if(sJumpTo.equals(String.valueOf(cLetter))) bShowLink = false; //don't show the jump if we'return already on that latter
			if(bShowLink) 
			{
				if(m_sJavascriptNextPrevMethod!=null && m_sJavascriptNextPrevMethod.length()>0)
				{
					String sMethod = adjustJavascriptMethod(m_sJavascriptNextPrevMethod, 1, (int)m_lMaxRowsPerView, String.valueOf(cLetter), sFieldName);
					sbData.append(sBaseAnchor + "JavaScript:" + sMethod + "\">");
					//sNextLink = "<a class=\"viewNext\" href=\"JavaScript:" + m_sJavascriptNextPrevMethod + "\">" + m_sNextText + "</a>";
				}
				else
				{
					sbData.append(sBaseAnchor + '&'+PARAM_JUMPTO+sFieldName+'='+cLetter + "\">");
				}
			}
			sbData.append(cLetter);
			if(bShowLink) 
				sbData.append("</a>");            
			sbData.append("&nbsp;&nbsp;&nbsp;");
			cLetter++;
		}
		sbData.append("</div>\r\n");
	}

	/**
	 * "someMethod(%%START%%, %%MAX%%, '%%JUMPTO%%')" will return eg someMethod(1, 50, '');
	 * @param sMethod
	 * @param iStart
	 * @param iMaxRows
	 * @param sJumpToLetter
	 * @param sViewName 
	 * @return
	 */
	private String adjustJavascriptMethod(String sMethod, long lStart, long lMaxRows, String sJumpToLetter, String sViewName) 
	{
		//TODO make the params case insensitive
		if(sJumpToLetter==null) sJumpToLetter = "";
		sMethod = sMethod.replaceAll("%%MAX%%", String.valueOf(lMaxRows));
		sMethod = sMethod.replaceAll("%%START%%", String.valueOf(lStart));
		sMethod = sMethod.replaceAll("%%JUMPTO%%", sJumpToLetter);
		sMethod = sMethod.replaceAll("%%VIEW%%", sViewName);

		return sMethod;
	}


	/**
	 * Set the view into the document's appropriate field
	 */
	public void setDocumentViewHTML(String sFieldName)
	{
		m_doc.replaceItem(sFieldName, getViewHTML(sFieldName));
	}

	/**
	 * Returns the number of rows rendered in the current view. Note that getViewHTML() must be called first to initialise
	 * the row count.
	 * @return
	 */
	public long getRowsInCurrentView()
	{
		return m_lCurrentViewRowCount;
	}

	/**
	 * 
	 * @param sSQL
	 * @param lStart
	 * @param lMaxRows
	 * @param bManualLimit
	 * @return
	 */
	private StringBuilder getViewXML(String sSQL, long lStart, long lMaxRows, boolean bManualLimit)
	{        
		TableManager t = null;
		Connection cx = null;
		m_lCurrentViewRowCount=0;
		long lLoopCount=1;
		boolean bSystemConnection = m_sConnName!=null && m_sConnName.equals(SystemContext.DBALIAS_SYSTEM);

		Statement stmt = null;
		ResultSet rs = null;
		StringBuilder sb = TableManager.getHeaderXML(m_tm.getDefaultProtocol(), m_tm.getDefaultHost(), m_pSession.getRequestPath().getPathToApplication());

		try 
		{
			if(bSystemConnection)
				cx = m_pSession.getSystemContext().getSystemConnection();
			else
				cx = m_pSession.getDataConnection(m_sConnName);
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(sSQL);
			while(rs.next())
			{                            

				if((bManualLimit && lLoopCount>=lStart) || !bManualLimit || m_objCallback!=null)
				{
					//m_lCurrentViewRowCount++;
					if(m_lMaxRowsPerView>0 && (m_lCurrentViewRowCount+1)>lMaxRows) 
					{
						m_bHasMoreResults = true;
						break;
					}
					else
					{
						if(m_objCallback!=null)
						{
							if(t==null) t = new TableManager(m_pSession, m_sConnName, "");
							t.clearDocument();
							t.populateDocumentFromResultSet(rs);
							if(m_objCallback.htmlViewCallback(t)) //if returns false should not appear in the result output, counting is therefore tricky and inefficient
							{
								if(lLoopCount>lStart)
								{
									sb.append("<row>");
									sb.append(t.getXML());
									sb.append("</row>");									
									m_lCurrentViewRowCount++;
								}
								lLoopCount++;
							}
						}
						else
						{
							sb.append("<row>");
							sb.append(TableManager.getColumnXML(rs, false, m_pSession.getSessionContext()));
							sb.append("</row>");
							lLoopCount++;
							m_lCurrentViewRowCount++;
						}
					}//else
				}
			}//while            
		}
		catch(Exception e)
		{
			m_pSession.getSystemContext().doError(e.toString(), m_pSession);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			if(bSystemConnection)
				m_pSession.getSystemContext().releaseSystemConnection(cx);
			else
				m_pSession.releaseDataConnection(cx);
		}

		TableManager.addTrailerXML(sb);
		//System.out.println(sSQL + " maxrows="+lMaxRows+ " rowcount="+lRowCount);
		//System.out.println(sb.toString());
		//m_pSession.getSystemContext().doDebug(0, sb.toString(), m_pSession);
		return sb;
	}

	/**
	 * Sets the object to be called on each iteration of the resultset loop
	 * @param paramCallback
	 */
	public void setCallback(HTMLViewCallback paramCallback)
	{
		m_objCallback = paramCallback;
	}


	/**
	 *
	 */
	private StringBuilder doList(long lStart, long lMaxRows)
	{
		boolean bManualLimit = false;
		String sSQL = getLimitClause(m_sSQL, lStart, lMaxRows+1);
		if(sSQL==null || m_objCallback!=null) //if there is a callback function, do manual limiting 
		{
			bManualLimit = true;
			sSQL = m_sSQL;
		}        

		StringBuilder sb = getViewXML(sSQL, lStart, lMaxRows, bManualLimit);
		if(m_sXSLStyleSheet==null || m_sXSLStyleSheet.length()==0) 
		{
			StringBuilder sbNoXSL = new StringBuilder(50);
			sbNoXSL.append("<b>No XSL stylesheet is set!</b>");
			return sbNoXSL;
		}
		StringBuilder sbResult = m_pSession.xmlTransform(sb, m_sXSLStyleSheet);
		if(sbResult==null) 
		{
			//sbResult may be null if the transform fails. What should we do in that case??
			m_pSession.getSystemContext().doError("HTMLView.TransformFailure", this);
			sbResult = new StringBuilder(); //avoid a NPE
		}
		//sbResult.append(sSQL);
		//sbResult.append(" m_bHasMoreResults=" + m_bHasMoreResults);

		return sbResult;       
	}

	/**
	 * Display the control atop the view to jump to a certain point
	 * @param sJumpTo
	 * @param lMaxRows
	 * @return
	 */
	private StringBuilder doJumpList(String sJumpTo, long lMaxRows)
	{
		long lStartRow=0;
		sJumpTo = sJumpTo.toUpperCase();
		StringBuilder sb = TableManager.getHeaderXML(m_tm.getDefaultProtocol(), m_tm.getDefaultHost(), m_doc.rPath.getPathToApplication());
		Connection cx = null;
		long lOutput=0;
		int iPos = m_sSQL.toUpperCase().indexOf(SQL_ORDER_BY);
		String sSortField = "";
		if(iPos>0) sSortField = m_sSQL.substring(iPos+SQL_ORDER_BY.length());
		//now sSortField will be terminated with a " " or ","
		iPos = sSortField.indexOf(' ');
		if(iPos>0) sSortField = sSortField.substring(0, iPos);
		iPos = sSortField.indexOf(',');
		if(iPos>0) sSortField = sSortField.substring(0, iPos);
		ResultSet rs = null;
		Statement stmt = null;
		TableManager t = null;
		//System.out.println("sort="+sSortField);
		try
		{
			cx = m_pSession.getDataConnection(m_sConnName);
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(m_sSQL);
			while(rs.next())
			{
				String sVal = rs.getString(sSortField);
				if(sVal!=null && sVal.length()>0)
				{
					sVal = sVal.toUpperCase();
					if(sVal.startsWith(sJumpTo) || lOutput>0) //output the row, if it matches or if we've already started output
					{
						if(m_objCallback!=null)
						{
							if(t==null) t = new TableManager(m_pSession, m_sConnName, "");
							t.clearDocument();
							t.populateDocumentFromResultSet(rs);
							if(m_objCallback.htmlViewCallback(t)) //if returns false should not appear in the result output, counting is therefore tricky and inefficient
							{

								sb.append("<row>");
								sb.append(t.getXML());
								sb.append("</row>");									
								lOutput++;
							}
						}
						else
						{
							sb.append("<row>");
							sb.append(TableManager.getColumnXML(rs, false, m_pSession.getSessionContext()));
							sb.append("</row>");
							//System.out.println(sVal);
							lOutput++;
						}
					}
					else
					{
						if(lOutput==0) //check we haven't gone past, eg we're looking for J and we've hit M
						{
							char cVal = sVal.charAt(0);
							char cJump = sJumpTo.charAt(0);
							if(cVal>cJump) break;
						}
						lStartRow++;
					}
				}
				if(lOutput>=lMaxRows) break;
			}            
		}
		catch(Exception e)
		{
			m_pSession.getSystemContext().doError(e.toString(), m_pSession);
			puakma.util.Util.logStackTrace(e, m_pSession.getSystemContext(), this, 999);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSession.releaseDataConnection(cx);
		}

		TableManager.addTrailerXML(sb);
		StringBuilder sbResult=null;
		if(lOutput==0) //no records were found
		{
			//System.out.println("lStartRow="+lStartRow+", maxperview="+m_lMaxRowsPerView);            
			long lBegin = lStartRow - m_lMaxRowsPerView;
			if(lBegin<0) lBegin=0;
			//System.out.println("lBegin="+lBegin+" max="+lMaxRows);
			sbResult = doList(lBegin, lMaxRows);
			m_sStart = String.valueOf(lBegin);
		}
		else
		{
			m_sStart = String.valueOf(lStartRow);        
			sbResult = m_pSession.xmlTransform(sb, m_sXSLStyleSheet);
		}

		//System.out.println(sb.toString());
		//System.out.println(m_sSQL);
		return sbResult; 
	}

	/**
	 * 
	 * @param sJumpTo
	 * @param lMaxRows
	 * @return
	 * @deprecated
	 */
	private StringBuilder zz_doJumpList(String sJumpTo, long lMaxRows)
	{
		long lStartRow=0;
		sJumpTo = sJumpTo.toUpperCase();
		StringBuilder sb = TableManager.getHeaderXML(m_tm.getDefaultProtocol(), m_tm.getDefaultHost(), m_doc.rPath.getPathToApplication());
		Connection cx = null;
		long lOutput=0;
		int iPos = m_sSQL.toUpperCase().indexOf(SQL_ORDER_BY);
		String sSortField = "";
		if(iPos>0) sSortField = m_sSQL.substring(iPos+SQL_ORDER_BY.length());
		//now sSortField will be terminated with a " " or ","
		iPos = sSortField.indexOf(' ');
		if(iPos>0) sSortField = sSortField.substring(0, iPos);
		iPos = sSortField.indexOf(',');
		if(iPos>0) sSortField = sSortField.substring(0, iPos);
		ResultSet rs = null;
		Statement stmt = null;
		//System.out.println("sort="+sSortField);
		try
		{
			cx = m_pSession.getDataConnection(m_sConnName);
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(m_sSQL);
			while(rs.next())
			{
				String sVal = rs.getString(sSortField);
				if(sVal!=null && sVal.length()>0)
				{
					sVal = sVal.toUpperCase();
					if(sVal.startsWith(sJumpTo) || lOutput>0) //output the row, if it matches or if we've already started output
					{
						sb.append("<row>");
						sb.append(TableManager.getColumnXML(rs, false, m_pSession.getSessionContext()));
						sb.append("</row>");
						//System.out.println(sVal);
						lOutput++;
					}
					else
					{
						if(lOutput==0) //check we haven't gone past, eg we're looking for J and we've hit M
						{
							char cVal = sVal.charAt(0);
							char cJump = sJumpTo.charAt(0);
							if(cVal>cJump) break;
						}
						lStartRow++;
					}
				}
				if(lOutput>=lMaxRows) break;
			}            
		}
		catch(Exception e)
		{
			m_pSession.getSystemContext().doError(e.toString(), m_pSession);
			puakma.util.Util.logStackTrace(e, m_pSession.getSystemContext(), this, 999);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSession.releaseDataConnection(cx);
		}

		TableManager.addTrailerXML(sb);
		StringBuilder sbResult=null;
		if(lOutput==0) //no records were found
		{
			//System.out.println("lStartRow="+lStartRow+", maxperview="+m_lMaxRowsPerView);            
			long lBegin = lStartRow - m_lMaxRowsPerView;
			if(lBegin<0) lBegin=0;
			//System.out.println("lBegin="+lBegin+" max="+lMaxRows);
			sbResult = doList(lBegin, lMaxRows);
			m_sStart = String.valueOf(lBegin);
		}
		else
		{
			m_sStart = String.valueOf(lStartRow);        
			sbResult = m_pSession.xmlTransform(sb, m_sXSLStyleSheet);
		}

		//System.out.println(sb.toString());
		//System.out.println(m_sSQL);
		return sbResult; 
	}

	/**
	 * limit style 1 (default) = LIMIT 86,25
	 * limit style 2           = LIMIT 25 OFFSET 86
	 * @return the full sql statement with the limit clause inserted
	 */
	private String getLimitClause(String sSQL, long lStart, long lMaxRows)
	{
		//maybe we return null when LIMIT is not supported? then we could manually
		//troll through the resultset
		if(m_lMaxRowsPerView<=0) return sSQL; //no limit on row count

		String sMax="";
		String sReturn = null;
		int iLimitStyle = 1;

		String sDriver = m_tm.getJDBCDriver();        
		if(sDriver!=null && sDriver.indexOf("postgres")>0) iLimitStyle = 2;
		if(sDriver!=null && sDriver.indexOf("hsqldb")>0) iLimitStyle = 3;
		if(sDriver!=null && sDriver.indexOf(".jtds.")>0) iLimitStyle = 4;
		if(sDriver!=null && sDriver.indexOf("oracle")>=0) iLimitStyle = 5;

		switch(iLimitStyle)
		{
		case 5://oracle select * from x where rownum<99 
			//http://www.experts-exchange.com/Programming/Programming_Languages/Delphi/Q_21619737.html
			sReturn = null; //TODO.... need to insert the rownum in the where if there is one
			//See /system/admin.pma/GetLog?OpenAction for syntax as needs a nested nested query
			break;
		case 4://no limiting, ms sql server http://www.planet-source-code.com/vb/scripts/ShowCode.asp?txtCodeId=850&lngWId=5
			sReturn = null; //use "SELECT TOP 99 ..."
			break;
		case 3://hsqldb
			if(lStart<=0) 
				sMax = " LIMIT " + lStart + " " + lMaxRows; //start is 0 based
			else
				sMax = " LIMIT " + lStart + " " + lMaxRows;
			//now we want select LIMIT 10 fields FROM TABLE ...
			if(sSQL.length()>6) 
				sReturn = "SELECT" + sMax + sSQL.substring(6);
			else
				sReturn = sSQL; //no limiting....
			break;
		case 2:   //postgres     
			if(lStart<=0) 
				sMax = " LIMIT " + lMaxRows;
			else
				sMax = " LIMIT " + lMaxRows + " OFFSET " + lStart; 
			sReturn = sSQL + sMax;
			break;
		case 1:   //mysql    
			if(lStart<=0) 
				sMax = " LIMIT " + lMaxRows;
			else
				sMax = " LIMIT " + lStart + "," + lMaxRows;
			sReturn = sSQL + sMax;
			break;
		default:
			sReturn = null; //default is manual limiting

		};

		//System.out.println(iLimitStyle + ">"+sReturn+"<");
		return sReturn;
	}


	public void setDefaultProtocol(String sProto)
	{
		if(m_tm!=null) m_tm.setDefaultProtocol(sProto);
	}

	public void setDefaultHost(String sHost)
	{
		if(m_tm!=null) m_tm.setDefaultHost(sHost);
	}

}
