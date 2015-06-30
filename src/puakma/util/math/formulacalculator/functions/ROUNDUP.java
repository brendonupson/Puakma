package puakma.util.math.formulacalculator.functions;

import java.math.BigDecimal;

import puakma.util.math.formulacalculator.FormulaCalculator;
import puakma.util.math.formulacalculator.InvalidFormulaException;
import puakma.util.math.formulacalculator.StringUtil;

/**
 * ROUNDUP
Rounds a number up, away from zero, to a certain precision.
Syntax
ROUNDUP(Number; Count)
Returns Number rounded up (away from zero) to Count decimal places. If Count is omitted or zero, the function rounds up to an integer. If Count is negative, the function rounds up to the next 10, 100, 1000, etc.
This function rounds away from zero. See ROUNDDOWN and ROUND for alternatives.
Example
=ROUNDUP(1.1111, 2) returns 1.12.
=ROUNDUP(1.2345, 1) returns 1.3.
=ROUNDUP(45.67, 0) returns 46.
=ROUNDUP(-45.67) returns -46.
=ROUNDUP(987.65, -2) returns 1000. --- not implemented
 * @author bupson
 *
 */
public class ROUNDUP extends BaseFunction 
{

	public double getResult() throws InvalidFormulaException
	{
		//System.out.println("[" + m_sBlock + "]");

		if(m_sBlock!=null && m_sBlock.length()>0)
		{
			String sFormulas[] = StringUtil.splitStringIgnoreNesting(m_sBlock, PARAMETER_SEPERATOR, FormulaCalculator.BRACKET_START, FormulaCalculator.BRACKET_END);
			//System.out.println("ROUND() = " + m_sBlock + " [" + sFormulas.length + "]");
			if(sFormulas==null || sFormulas.length!=2) throw new InvalidFormulaException("ROUNDUP(number,decimalPlaces) requires 2 parameters");

			FormulaCalculator fc = newFormulaCalculator(sFormulas[0]);
			double dNumberToRound = fc.getResult();

			FormulaCalculator fc1 = newFormulaCalculator(sFormulas[1]);
			int iPrecision = (int)fc1.getResult();

			//System.out.println(sFormulas[0].toString() + "=" + dNumberToRound + " " + sFormulas[1].toString() + "=" + iPrecision);

			BigDecimal bd = new BigDecimal(dNumberToRound);			
			bd = bd.setScale(iPrecision, BigDecimal.ROUND_CEILING);
			return bd.doubleValue();
		}
		return 0;
	}

}
