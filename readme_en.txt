Java versions of the Opus libraries
*****************************************************************************
Conformity:

opus-1.3.0
opusfile-0.11
libopusenc-0.2.1
opus-tools-0.2
libogg-1.3.3
+ tests
+ examples
+ java spi interface

*****************************************************************************
opus_demo and opus_compare are moved to /test.
*****************************************************************************
Java v1.6 was used.

It is recommended to use an obfuscator to reduce the size and speed up.
You should save the class names inside the /spi package.
Obuscator optimization should be used carefully,
it can reduce the library speed and increase the CPU usage.
*****************************************************************************
#undef SMALL_FOOTPRINT
#undef NORM_ALIASING_HACK
#undef FUZZING
#undef RESYNTH
#undef CUSTOM_MODES
#undef FIXED_POINT
#undef DISABLE_FLOAT_API
Since v1.3:/* Disable bitstream fixes from RFC 8251 */

#undef DISABLE_UPDATE_DRAFT = Jopus_defines.DISABLE_UPDATE_DRAFT = false;
*****************************************************************************
Source file labels:
FIXME - suspicious places in the C source code.
XXX - the labels for test code and comments.
TODO - possible, a java code needs to be refined.

*****************************************************************************
See also:
Opus, home page
http://opus-codec.org/
Files to download
http://downloads.xiph.org/releases/opus/
*****************************************************************************
email: dmilvdv@gmail.com