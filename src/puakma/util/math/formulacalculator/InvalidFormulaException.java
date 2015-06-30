package puakma.util.math.formulacalculator;

/**
 * 
 * @author srahman
 * @version 1.0.0
 */
public class InvalidFormulaException extends Exception 
{
	private static final long serialVersionUID = 8193759836365929128L; //I got no clue what this is.
	private String m_sErrorMessage = "";

	public InvalidFormulaException(String sErrorMessage) 
	{
		m_sErrorMessage = sErrorMessage;
	}
	
	public String getMessage()
	{
		return m_sErrorMessage;
	}
	
}
