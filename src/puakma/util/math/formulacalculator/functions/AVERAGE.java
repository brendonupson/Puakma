package puakma.util.math.formulacalculator.functions;

import puakma.util.math.formulacalculator.FormulaCalculator;
import puakma.util.math.formulacalculator.StringUtil;

/**
 * @author Shahedur Rahman gone crazy
 * @version 1.0.1
 */

public class AVERAGE extends BaseFunction
{	
	public double getResult() 
	{
		if(m_sBlock!=null && m_sBlock.length()>0)
		{	
			String sFormulas[] = StringUtil.splitStringIgnoreNesting(m_sBlock, PARAMETER_SEPERATOR, FormulaCalculator.BRACKET_START, FormulaCalculator.BRACKET_END);
			double d[] = new double[sFormulas.length];
			for(int i=0; i<sFormulas.length; i++)
			{					
				FormulaCalculator fc = newFormulaCalculator(sFormulas[i]);
				d[i] = fc.getResult();
			}
			m_dExternalFunctionResult = getAverage(d);
		}
		return m_dExternalFunctionResult;
	}

	/**
	 * Gets the average of an array of numbers. If any of them is NaN or the array is null then it returns NaN.
	 * @param d
	 * @return
	 */
	public static double getAverage(double d[])
	{
		if(d!=null && d.length>0)
		{
			double dResult = 0;
			for(int i=0; i<d.length; i++)
			{
				if(Double.isNaN(d[i])) return Double.NaN;
				dResult += d[i]; 
			}
			return dResult/d.length;
		}
		return Double.NaN;
	}

}
