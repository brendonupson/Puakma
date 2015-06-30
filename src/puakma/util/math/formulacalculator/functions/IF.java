package puakma.util.math.formulacalculator.functions;

import puakma.util.math.formulacalculator.FormulaCalculator;
import puakma.util.math.formulacalculator.StringUtil;

/**
 * Used as a conditional, excel style IF(3<4, 22, 17)
 * @author bupson
 *
 */
public class IF extends BaseFunction 
{

	public double getResult() 
	{
		//System.out.println("[" + m_sBlock + "]");

		if(m_sBlock!=null && m_sBlock.length()>0)
		{
			String sFormulas[] = StringUtil.splitStringIgnoreNesting(m_sBlock, PARAMETER_SEPERATOR, FormulaCalculator.BRACKET_START, FormulaCalculator.BRACKET_END);
			if(sFormulas==null || sFormulas.length!=3) return 0;

			FormulaCalculator fc = newFormulaCalculator(sFormulas[0]);
			double dCondition = fc.getResult();
			//System.out.println(sFormulas[0].toString() + "=" + dCondition);

			if(dCondition>0) 
			{
				FormulaCalculator fc1 = newFormulaCalculator(sFormulas[1]);
				return fc1.getResult();				 
			}
			else
			{
				FormulaCalculator fc2 = newFormulaCalculator(sFormulas[2]);
				return fc2.getResult();	
			}
			
		
		}
		return 0;
	}

}
