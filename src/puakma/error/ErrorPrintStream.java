package puakma.error;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Date;

import puakma.util.Util;

/**
 * The class was created so we could get a timestamp in the err.log for each error.
 * @author bupson
 *
 */
public class ErrorPrintStream extends PrintStream 
{

	/**
	 * Default constructor
	 * @param out
	 */
	public ErrorPrintStream(OutputStream out) 
	{
		super(out);		
	}

	/**
	 * 
	 */
	public void print(String s)
	{
		super.print(Util.formatDate(new Date(), "yyyy-MM-dd:HH:mm:ss") + ' ' + s);
	}
	
}
