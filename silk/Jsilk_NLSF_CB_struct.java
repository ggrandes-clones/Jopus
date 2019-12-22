package silk;

// structs.h

/** Structure containing NLSF codebook */
final class Jsilk_NLSF_CB_struct {
	final short   nVectors;
	final short   order;
	final short   quantStepSize_Q16;
	final short   invQuantStepSize_Q6;
	final char[]  CB1_NLSF_Q8;// java uint8 to char
	final short[] CB1_Wght_Q9;
	final char[]  CB1_iCDF;// java uint8 to char
	final char[]  pred_Q8;// java uint8 to char
	final char[]  ec_sel;// java uint8 to char
	final char[]  ec_iCDF;// java uint8 to char
	final char[]  ec_Rates_Q5;// java uint8 to char
	final short[] deltaMin_Q15;
	//
	Jsilk_NLSF_CB_struct(
			final short inVectors,
			final short iorder,
			final short iquantStepSize_Q16,
			final short iinvQuantStepSize_Q6,
			final char[] iCB1_NLSF_Q8,
			final short[] iCB1_Wght_Q9,
			final char[] iCB1_iCDF,
			final char[] ipred_Q8,
			final char[] iec_sel,
			final char[] iec_iCDF,
			final char[] iec_Rates_Q5,
			final short[] ideltaMin_Q15)
	{
		nVectors = inVectors;
		order = iorder;
		quantStepSize_Q16 = iquantStepSize_Q16;
		invQuantStepSize_Q6 = iinvQuantStepSize_Q6;
		CB1_NLSF_Q8 = iCB1_NLSF_Q8;
		CB1_Wght_Q9 = iCB1_Wght_Q9;
		CB1_iCDF = iCB1_iCDF;
		pred_Q8 = ipred_Q8;
		ec_sel = iec_sel;
		ec_iCDF = iec_iCDF;
		ec_Rates_Q5 = iec_Rates_Q5;
		deltaMin_Q15 = ideltaMin_Q15;
	}
}
