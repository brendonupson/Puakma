package puakma.addin.http.document;

/**
 * @deprecated This class always had a bad name :-( 4.0.1 12.Oct.2007 this class was deprecated
 * @author bupson
 *
 */
public class HTMLDocumentItem
{
	private HTMLControl m_control;
	
	public final static int ITEM_TYPE_UNKNOWN=-1;
	public final static int ITEM_TYPE_VOID=0; //no output at all
	public final static int ITEM_TYPE_TEXT=1;
	public final static int ITEM_TYPE_HIDDEN=2;
	public final static int ITEM_TYPE_RADIO=3;
	public final static int ITEM_TYPE_CHECK=4;
	public final static int ITEM_TYPE_LIST=5;
	public final static int ITEM_TYPE_COMBO=6;
	public final static int ITEM_TYPE_TEXTAREA=7;
	public final static int ITEM_TYPE_COMPUTED=8;
	public final static int ITEM_TYPE_PATH=9;
	public final static int ITEM_TYPE_FILE=10;
	public final static int ITEM_TYPE_PASSWORD=11;
	public final static int ITEM_TYPE_BUTTON=12;
	public final static int ITEM_TYPE_FORM=13;
	public final static int ITEM_TYPE_VERSION=14;
	public final static int ITEM_TYPE_PARAMETER=15;
	public final static int ITEM_TYPE_COOKIE=16;
	public final static int ITEM_TYPE_DATE=17;
	public final static int ITEM_TYPE_COMPUTEDDATE=18;
	public final static int ITEM_TYPE_VIEW=19;
	public final static int ITEM_TYPE_COMPUTEDPAGE=20;
	public final static int ITEM_TYPE_CHILDPAGE=21;
	public final static int ITEM_TYPE_HIDESTART=22;
	public final static int ITEM_TYPE_HIDEEND=23;
	public final static int ITEM_TYPE_USERNAME=24;
	
	public HTMLDocumentItem(HTMLControl control) 
	{
		m_control = control;
	}
	
	public HTMLDocumentItem(HTMLDocument paramDocument, String sTagHTML)
	{
		m_control = new HTMLControl(paramDocument, sTagHTML);
	}
	
	
	public void dropItemOption(String sOptionName)
	{
		m_control.dropItemOption(sOptionName);
	}
	
	public String[] getChoices()
	{
		return m_control.getChoices();
	}
	
	public String getDefaultValue()
	{
		return m_control.getDefaultValue();
	}
	
	public String getDocumentCookie(String szName)
	{
		return m_control.getDocumentCookie(szName);
	}
	
	public String getDocumentParameter(String szName)
	{
		return m_control.getDocumentParameter(szName);
	}
	
	public StringBuilder getFormTagHTML()
	{
		return m_control.getFormTagHTML();
	}
	
	public StringBuilder getHTML(boolean bReadMode)
	{
		return m_control.getHTML(bReadMode);
	}
	
	public String getItemOption(String sOptionName)
	{
		return m_control.getItemOption(sOptionName);
	}
	
	public String getName()
	{
		return m_control.getName();
	}
	
	public int getType()
	{
		return m_control.getType();
	}
	
	public int getTypeFromString(String sType)
	{
		return m_control.getTypeFromString(sType);
	}
	
	public boolean isRichItem()
	{
		return m_control.isRichItem();
	}
	
	public void setChoices(String sChoices)
	{
		m_control.setChoices(sChoices);
	}
	
	public void setChoices(String sChoices[])
	{
		m_control.setChoices(sChoices);
	}
	
	public void setName(String szNewName)
	{
		m_control.setName(szNewName);
	}
	
	public void setValue(String newValue)
	{
		m_control.setValue(newValue);
	}
	
	public void setSize(String szNewSize)
	{
		m_control.setSize(szNewSize);
	}
	
	public void setType(int iNewType)
	{
		m_control.setType(iNewType);
	}
	
	public boolean tagSupportsMultipleSelections()
	{
		return m_control.tagSupportsMultipleSelections();
	}
	
}
