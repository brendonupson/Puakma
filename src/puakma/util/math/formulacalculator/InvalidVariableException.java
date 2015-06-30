package puakma.util.math.formulacalculator;

/**
 * 
 * @author srahman
 * @version 1.0.0
 */
public class InvalidVariableException extends Exception 
{
	private static final long serialVersionUID = 4483508885629794168L;
	private String m_sErrorMessage = "";

	public InvalidVariableException(String sErrorMessage) 
	{
		m_sErrorMessage = sErrorMessage;
	}
	
	public String getMessage()
	{
		return m_sErrorMessage;
	}
	
}
