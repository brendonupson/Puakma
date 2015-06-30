package puakma.util.math.formulacalculator.functions;

import puakma.util.math.formulacalculator.FormulaCalculator;

/**
 * @author Shahedur Rahman gone crazy
 * @version 1.0.1
 */
public final class TAN extends BaseFunction {

	public double getResult() 
	{
		FormulaCalculator fc = newFormulaCalculator(m_sBlock);
		m_dExternalFunctionResult = fc.getResult();
		if(Double.isNaN(m_dExternalFunctionResult)) return m_dExternalFunctionResult; 
		return Math.tan(Math.toRadians(m_dExternalFunctionResult));
	}

}
