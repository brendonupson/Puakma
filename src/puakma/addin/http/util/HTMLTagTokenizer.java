package puakma.addin.http.util;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/** ***************************************************************
HTMLTagTokenizer.java
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
 * The purpose of this class is to slice up an html tag, ie
 * <INPUT NAME="FRED" VALUE="3" TYPE="TEXT">
 *
 * This function assumes < & > have been removed before being called
 */
public class HTMLTagTokenizer
{
	private String m_sRemainder;
	private int m_iPos = 0;
	private List m_arrStringTokens;
	private ListIterator m_iterTokens;

	/**
	 * 
	 * @param sTag
	 */
	public HTMLTagTokenizer(String sTag)
	{
		m_sRemainder = sTag;

		m_arrStringTokens = new ArrayList();

		populateTokenList();

		m_iterTokens = m_arrStringTokens.listIterator();
	}

	/**
	 * 
	 */
	public void populateTokenList()
	{
		while(m_iPos < m_sRemainder.length())
		{
			String sToken = getNextElement();

			if(sToken.length() != 0)
				m_arrStringTokens.add(sToken);
		}
	}

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args)
	{
		//HTMLTagTokenizer st = new HTMLTagTokenizer("att1='3' att2=\"4\"");
		//String string = " bob   = 4 	blah=val 	att2		blah2=\"    v === al2\"   multiple fred='seven'";
		//String string = "INPUT NAME=\" FR ED\" onclick= 	blahFunc()  test  VALUE= \"3\" TYPE =	\"TEXT\"";
		//String string = "attr1 = 45 attr2 =\"smith\'s\"";
		String sSelectedTab2 = "smith's";
		String sName = "aname";
		String sSelectedL1Tab = "l1tab";
		String sEscTab = "smith\\'s";
		//String string =  " name=\""+ sName + "\" id=\""+ sName + "\" value=\""+sSelectedTab2+"\" onchange=\"doSelectTab2('" + sSelectedL1Tab +"','"+ sEscTab+"','W');\"";

		//		String string = "INPUT = $9 NAME = \"FRED\" VALUE=\"3= 9 \" TYPE=\'TE XT\' val";
		String string =  "something=\"aaa\" \r\n another=\"b\" \t\nmore=\"c\"\t\n\tr";
		long l = System.currentTimeMillis();

		for(int i=0; i<1; i++)
		{
			HTMLTagTokenizer st = new HTMLTagTokenizer(string);

			while(st.hasMoreTokens())
			{	
				String s = 
						st.nextElement();
				System.out.println(s);
			}
		}

		System.out.println("Tokenizer : " + (System.currentTimeMillis()-l));

	}

	/**
	 * 
	 * @return
	 */
	public boolean hasMoreTokens()
	{
		if(m_iterTokens.hasNext())
			return true;

		return false;
	}

	/**
	 * 
	 * @return
	 */
	public String nextElement()
	{
		return (String)m_iterTokens.next();
	}

	/**
	 * 
	 * @return
	 */
	private String getNextElement()
	{
		StringBuilder sb = new StringBuilder(m_sRemainder.length());
		boolean bHasEquals = false;
		boolean bEndQuoteRequired = false;
		char cEndQuote=' ';

		for(; m_iPos<m_sRemainder.length(); m_iPos++)
		{
			char cChar = m_sRemainder.charAt(m_iPos);

			if(bEndQuoteRequired)
			{
				//if(cChar == '\'' || cChar == '\"')
				if(cChar == cEndQuote)
					bEndQuoteRequired = false;

				sb.append(cChar);
				continue;
			}

			if(!Character.isWhitespace(cChar))
			{
				if(cChar == '=')
				{
					bHasEquals = true;
				}
				else if(cChar == '"' || cChar == '\'')
				{
					bEndQuoteRequired = true;
					cEndQuote = cChar;
				}

				sb.append(cChar);
			}
			else 
			{
				if(Character.isWhitespace(cChar))
				{
					if(bHasEquals)
					{
						if(sb.lastIndexOf("=") != sb.length()-1)
						{
							break;	
						}
					}
					else //if(!bHasEquals)
					{
						if(m_iPos+1<m_sRemainder.length())
						{
							char character = m_sRemainder.charAt(m_iPos+1);

							if(!(Character.isWhitespace(character) || character == '='))
							{
								m_iPos++;
								break;
							}
						}//bounds check
					}
				}//if whitespace
			}//else
		}

		return sb.toString();
	}
} 