/**
 * 
 */
package puakma.util.math.formulacalculator.functions;

import java.util.ArrayList;

import puakma.util.math.formulacalculator.FormulaCalculator;
import puakma.util.math.formulacalculator.InvalidFormulaException;
import puakma.util.math.formulacalculator.StringUtil;

/**
 * @author Shahedur Rahman gone crazy
 * @version 1.0.1
 */
public class LCM extends BaseFunction
{

	public LCM() {}

	public double getResult() 
	{
		if(m_sBlock!=null && m_sBlock.length()>0)
		{	
			String sFormulas[] = StringUtil.splitStringIgnoreNesting(m_sBlock, PARAMETER_SEPERATOR, FormulaCalculator.BRACKET_START, FormulaCalculator.BRACKET_END);
			long l[] = new long[sFormulas.length];
			try 
			{
				for(int i=0; i<sFormulas.length; i++)
				{					
					FormulaCalculator fc = newFormulaCalculator(sFormulas[i]);
					double d = fc.getResult();
					if(d==Double.NaN) throw new InvalidFormulaException("Parameters passed into LCM Resulted in a NaN.");
					long lResult = (long)d;
					if(lResult!=d) throw new InvalidFormulaException("Function LCM must have only integers passed into it.");
					l[i] = lResult;
				}
				m_dExternalFunctionResult = computeLCM(l);
			} 
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return m_dExternalFunctionResult;
	}

	/**
	 * Computes LCM of the long array passed in.
	 * @param lNums
	 * @return
	 */
	public static final double computeLCM(long[] lNums)
	{
		if(lNums==null || lNums.length==0) return 0;
		boolean bCalculate = true;
		ArrayList arrNums = new ArrayList(lNums.length);
		long lDivisor = 2; //start from 2 and move upwards
		while(bCalculate)
		{
			long[] lDivided = getDividedOROriginal(lNums, lDivisor);
			if(lDivided.length>0)
			{
				if(!isArrayEqual(lNums, lDivided)) //if the result is not equal to before
				{
					lNums = lDivided;
					arrNums.add(new Long(lDivisor));
				}
				else lDivisor++;
			}
			else bCalculate = false;
		}
		arrNums.add(new Long(lDivisor));
		return multiplyAll(arrNums);
	}
	
	/**
	 * Multiplies all the numbers in the array and returns the result.
	 * @param arrNums
	 * @return
	 */
	private static double multiplyAll(ArrayList arrNums) 
	{
		double dResult = 1;
		for(int i=0; i<arrNums.size(); i++)
			dResult *= ((Long)arrNums.get(i)).longValue();
		return dResult;
	}

	private static boolean isArrayEqual(long[] lNums, long[] lDivided) 
	{
		if(lNums.length!=lDivided.length) return false;
		for(int i=0; i<lNums.length; i++)
			if(lNums[i]!=lDivided[i]) return false;
		return true;
	}

	/**
	 * Divides all of the longs by the divisor. If divisible then replaces the original number or keeps the original.
	 * All result equaling 1 will be removed from the array.
	 * @param nums
	 * @param divisor
	 * @return
	 */
	private static long[] getDividedOROriginal(long[] lNums, long lDivisor) 
	{
		ArrayList arr = new ArrayList(lNums.length);
		for(int i=0; i<lNums.length; i++)
		{
			if((lNums[i]%lDivisor)==0) 
			{
				long lQuotient = lNums[i]/lDivisor;
				if(lQuotient!=1) arr.add(new Long(lQuotient));
			}
			else 
				arr.add(new Long(lNums[i]));
		}
		long[] lResult = new long[arr.size()];
		for(int i=0; i<lResult.length; i++)
			lResult[i] = ((Long)arr.get(i)).longValue();
		return lResult;
	}

}
