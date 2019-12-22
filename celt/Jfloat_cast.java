package celt;

/* Copyright (C) 2001 Erik de Castro Lopo <erikd AT mega-nerd DOT com> */

// float_cast.h

/* Version 1.1 */

public final class Jfloat_cast {
	// arch.h
	public static final float CELT_SIG_SCALE = 32768.f;
	// #define SCALEIN(a)      ((a)*CELT_SIG_SCALE)
	// #define SCALEOUT(a)     ((a)*(1/CELT_SIG_SCALE)) // java: extracted in place

	public static final float Q15ONE = 1.0f;

	public static final float EPSILON    = 1e-15f;
	public static final float VERY_SMALL = 1e-30f;
	// end arch.h

/*============================================================================
**      On Intel Pentium processors (especially PIII and probably P4), converting
**      from float to int is very slow. To meet the C specs, the code produced by
**      most C compilers targeting Pentium needs to change the FPU rounding mode
**      before the float to int conversion is performed.
**
**      Changing the FPU rounding mode causes the FPU pipeline to be flushed. It
**      is this flushing of the pipeline which is so slow.
**
**      Fortunately the ISO C99 specifications define the functions lrint, lrintf,
**      llrint and llrintf which fix this problem as a side effect.
**
**      On Unix-like systems, the configure process should have detected the
**      presence of these functions. If they weren't found we have to replace them
**      here with a standard C cast.
*/

/*
**      The C99 prototypes for lrint and lrintf are as follows:
**
**              long int lrintf (float x) ;
**              long int lrint  (double x) ;
*/

/*      The presence of the required functions are detected during the configure
**      process and the values HAVE_LRINT and HAVE_LRINTF are set accordingly in
**      the config.h file.
*/

/*      These defines enable functionality introduced with the 1999 ISO C
**      standard. They must be defined before the inclusion of math.h to
**      engage them. If optimisation is enabled, these functions will be
**      inlined. With optimisation switched off, you have to link in the
**      maths library using -lm.
*/

	// float_cast.h
	/* public static short FLOAT2INT16(float x)// java extracted in place
	{
		x *= CELT_SIG_SCALE;
		x = x >= -32768 ? x : -32768;
		x = x <=  32767 ? x :  32767;
		return (short)Math.floor( .5 + (double)x );
	} */
	// end float_cast.h

}
