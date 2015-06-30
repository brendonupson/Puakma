package puakma.util.math.formulacalculator.functions;

import java.math.BigDecimal;

import puakma.util.math.formulacalculator.FormulaCalculator;
import puakma.util.math.formulacalculator.InvalidFormulaException;
import puakma.util.math.formulacalculator.StringUtil;

/**
 * ROUNDDOWN
Rounds a number down, toward zero, to a certain precision.
Syntax
ROUNDDOWN(Number; Count)
Returns Number rounded down (towards zero) to Count decimal places. If Count is omitted or zero, the function rounds down to an integer. If Count is negative, the function rounds down to the next 10, 100, 1000, etc.
This function rounds towards zero. See ROUNDUP and ROUND for alternatives.
Example
=ROUNDDOWN(1.234;2) returns 1.23.
=ROUNDDOWN(45.67;0) returns 45.
=ROUNDDOWN(-45.67) returns -45.
=ROUNDDOWN(987.65;-2) returns 900. ---not implemented
 * @author bupson
 *
 */
public class ROUNDDOWN extends BaseFunction 
{

	public double getResult() throws InvalidFormulaException
	{
		//System.out.println("[" + m_sBlock + "]");

		if(m_sBlock!=null && m_sBlock.length()>0)
		{
			String sFormulas[] = StringUtil.splitStringIgnoreNesting(m_sBlock, PARAMETER_SEPERATOR, FormulaCalculator.BRACKET_START, FormulaCalculator.BRACKET_END);
			//System.out.println("ROUND() = " + m_sBlock + " [" + sFormulas.length + "]");
			if(sFormulas==null || sFormulas.length!=2) throw new InvalidFormulaException("ROUNDDOWN(number,decimalPlaces) requires 2 parameters");

			FormulaCalculator fc = newFormulaCalculator(sFormulas[0]);
			double dNumberToRound = fc.getResult();

			FormulaCalculator fc1 = newFormulaCalculator(sFormulas[1]);
			int iPrecision = (int)fc1.getResult();

			//System.out.println(sFormulas[0].toString() + "=" + dNumberToRound + " " + sFormulas[1].toString() + "=" + iPrecision);

			BigDecimal bd = new BigDecimal(dNumberToRound);			
			bd = bd.setScale(iPrecision, BigDecimal.ROUND_FLOOR);
			return bd.doubleValue();
		}
		return 0;
	}

}
