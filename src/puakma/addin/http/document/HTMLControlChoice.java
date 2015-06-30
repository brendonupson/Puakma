package puakma.addin.http.document;

import puakma.util.Util;

/**
 * This class is used in an array to manage a list of choices for a combo,list etc
 * tag.
 * @author bupson
 *
 */
public class HTMLControlChoice 
{
	private String m_sDisplayText="";
	private String m_sAliasText="";
	private boolean m_bSelected=false;

	public HTMLControlChoice(String sChoice)
	{
		if(sChoice==null) return;

		m_sDisplayText = sChoice;
		m_sAliasText = sChoice;
		int iPos = sChoice.indexOf('|');
		if(iPos>=0)
		{
			m_sDisplayText = sChoice.substring(0, iPos);
			m_sAliasText = sChoice.substring(iPos+1);
		}
	}

	/**
	 * Convert to a HTML option for a list or combo tag
	 * @param bSelected
	 * @param sAdditionalTagAttributes eg "style=\"color: #333;\""
	 * @return
	 */
	public String getAsOption(boolean bSelected, String sAdditionalTagAttributes)
	{
		/*String sSelected="";
		if(bSelected) sSelected=" selected=\"selected\"";
		if(sAdditionalTagAttributes==null) 
			sAdditionalTagAttributes = "";
		else
			sAdditionalTagAttributes = " " + sAdditionalTagAttributes);*/

		StringBuilder sb = new StringBuilder(100);
		sb.append("<option value=\"");
		sb.append(m_sAliasText);
		sb.append("\"");
		if(bSelected) sb.append(" selected=\"selected\"");
		if(sAdditionalTagAttributes!=null) sb.append(" " + Util.trimSpaces(sAdditionalTagAttributes));
		sb.append(">");
		sb.append(m_sDisplayText);
		sb.append("</option>");
		return sb.toString();
	}

	/**
	 * 
	 * @return
	 */
	public String getDisplayText()
	{
		return m_sDisplayText;
	}

	/**
	 * 
	 * @return
	 */
	public String getAliasText()
	{
		return m_sAliasText;
	}

	/**
	 * 
	 *
	 */
	public boolean isSelected()
	{
		return m_bSelected;
	}

	/**
	 * 
	 * @param bOn
	 */
	public void setSelected(boolean bOn)
	{
		m_bSelected = bOn;
	}

	/**
	 * Convert a string array of choices into an array of these objects
	 * @param sChoices
	 * @param sFirstChoice
	 * @return
	 */
	public static HTMLControlChoice[] makeChoiceArray(String sChoices[], String sFirstChoice)
	{
		if(sChoices==null) return new HTMLControlChoice[0];
		int iExtra = 0;
		if(sFirstChoice!=null && sFirstChoice.length()>0) iExtra=1;
		HTMLControlChoice choices[] = new HTMLControlChoice[sChoices.length+iExtra];

		if(iExtra>0) choices[0] = new HTMLControlChoice(sFirstChoice);
		for(int i=0; i<sChoices.length; i++)
		{
			choices[i+iExtra] = new HTMLControlChoice(sChoices[i]);
		}

		return choices;
	}
}
