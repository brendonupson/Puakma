package puakma.util.math.formulacalculator.functions;

import java.math.BigDecimal;

import puakma.util.math.formulacalculator.FormulaCalculator;
import puakma.util.math.formulacalculator.InvalidFormulaException;
import puakma.util.math.formulacalculator.StringUtil;

/**
 * ROUND
Rounds a number to a certain number of decimal places.
Syntax
ROUND(Number; Count)
Returns Number rounded to Count decimal places. If Count is omitted or zero, the function rounds to the nearest integer. If Ccunt is negative, the function rounds to the nearest 10, 100, 1000, etc.
This function rounds to the nearest number. See ROUNDDOWN and ROUNDUP for alternatives.
Example
=ROUND(2.348,2) returns 2.35
=ROUND(-32.4834,3) returns -32.483. Change the cell format to see all decimals.
=ROUND(2.348,0) returns 2.
=ROUND(2.5) returns 3. 
=ROUND(987.65,-2) returns 1000. --- NOT IMPLEMENTED
 * @author bupson
 *
 */
public class ROUND extends BaseFunction 
{

	public double getResult() throws InvalidFormulaException
	{
		//System.out.println("[" + m_sBlock + "]");

		if(m_sBlock!=null && m_sBlock.length()>0)
		{
			String sFormulas[] = StringUtil.splitStringIgnoreNesting(m_sBlock, PARAMETER_SEPERATOR, FormulaCalculator.BRACKET_START, FormulaCalculator.BRACKET_END);
			//System.out.println("ROUND() = " + m_sBlock + " [" + sFormulas.length + "]");
			if(sFormulas==null || sFormulas.length!=2) throw new InvalidFormulaException("ROUND(number,decimalPlaces) requires 2 parameters");

			FormulaCalculator fc = newFormulaCalculator(sFormulas[0]);
			double dNumberToRound = fc.getResult();

			FormulaCalculator fc1 = newFormulaCalculator(sFormulas[1]);
			int iPrecision = (int)fc1.getResult();

			//System.out.println(sFormulas[0].toString() + "=" + dNumberToRound + " " + sFormulas[1].toString() + "=" + iPrecision);

			BigDecimal bd = new BigDecimal(dNumberToRound);			
			bd = bd.setScale(iPrecision, BigDecimal.ROUND_HALF_UP);
			return bd.doubleValue();
		}
		return 0;
	}

}
