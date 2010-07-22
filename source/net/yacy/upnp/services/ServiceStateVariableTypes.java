/*
 * ============================================================================
 *                 The Apache Software License, Version 1.1
 * ============================================================================
 *
 * Copyright (C) 2002 The Apache Software Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modifica-
 * tion, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must  retain the above copyright  notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following  acknowledgment: "This product includes software
 *    developed by SuperBonBon Industries (http://www.sbbi.net/)."
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "UPNPLib" and "SuperBonBon Industries" must not be
 *    used to endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    info@sbbi.net.
 *
 * 5. Products  derived from this software may not be called 
 *    "SuperBonBon Industries", nor may "SBBI" appear in their name, 
 *    without prior written permission of SuperBonBon Industries.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED. IN NO EVENT SHALL THE
 * APACHE SOFTWARE FOUNDATION OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT,INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLU-
 * DING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software  consists of voluntary contributions made by many individuals
 * on behalf of SuperBonBon Industries. For more information on 
 * SuperBonBon Industries, please see <http://www.sbbi.net/>.
 */
package net.yacy.upnp.services;

/**
 * Interface to defined allowed values for service state variables data types
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */
public interface ServiceStateVariableTypes {

  /**
   * Unsigned 1 Byte int. Same format as int without leading sign.
   */
  public final static String UI1         = "ui1";
  /**
   * Unsigned 2 Byte int. Same format as int without leading sign.
   */
  public final static String UI2         = "ui2";
  /**
   * Unsigned 4 Byte int. Same format as int without leading sign.
   */
  public final static String UI4         = "ui4";
  /**
   * 1 Byte int. Same format as int.
   */
  public final static String I1          = "i1";
   /**
   * 2 Byte int. Same format as int.
   */
  public final static String I2          = "i2";
   /**
   * 4 Byte int. Same format as int.
   */
  public final static String I4          = "i4";
  /**
   * Fixed point, integer number. May have leading sign. May have leading zeros.
   * (No currency symbol.) (No grouping of digits to the left of the decimal, e.g., no commas.)
   */
  public final static String INT         = "int";
  /**
   * 4 Byte float. Same format as float. Must be between 3.40282347E+38 to 1.17549435E-38.
   */
  public final static String R4          = "r4";
  /**
   * 8 Byte float. Same format as float. Must be between -1.79769313486232E308 and -4.94065645841247E-324
   * for negative values, and between 4.94065645841247E-324 and 1.79769313486232E308 for positive values,
   * i.e., IEEE 64-bit (8-Byte) double.
   */
  public final static String R8          = "r8";
  /**
   * Same as r8.
   */
  public final static String NUMBER      = "number";
  /**
   * Same as r8 but no more than 14 digits to the left of the decimal point and no more than 4 to the right.
   */
  public final static String FIXED_14_4  = "fixed.14.4";
  /**
   * Floating point number. Mantissa (left of the decimal) and/or exponent may have a leading sign.
   * Mantissa and/or exponent may have leading zeros. Decimal character in mantissa is a period, i.e.,
   * whole digits in mantissa separated from fractional digits by period. Mantissa separated from exponent
   * by E. (No currency symbol.) (No grouping of digits in the mantissa, e.g., no commas.)
   */
  public final static String FLOAT       = "float";
  /**
   * Unicode string. One character long.
   */
  public final static String CHAR        = "char";
  /**
   * Unicode string. No limit on length.
   */
  public final static String STRING      = "string";
  /**
   * Date in a subset of ISO 8601 format without time data.
   */
  public final static String DATE        = "date";
  /**
   * Date in ISO 8601 format with optional time but no time zone.
   */
  public final static String DATETIME    = "dateTime";
  /**
   * Date in ISO 8601 format with optional time and optional time zone.
   */
  public final static String DATETIME_TZ = "dateTime.tz";
  /**
   * Time in a subset of ISO 8601 format with no date and no time zone.
   */
  public final static String TIME        = "time";
  /**
   * Time in a subset of ISO 8601 format with optional time zone but no date.
   */
  public final static String TIME_TZ     = "time.tz";
  /**
   * 0, false, or no for false; 1, true, or yes for true.
   */
  public final static String BOOLEAN     = "boolean";
  /**
   * MIME-style Base64 encoded binary BLOB. Takes 3 Bytes, splits them into 4 parts,
   * and maps each 6 bit piece to an octet. (3 octets are encoded as 4.) No limit on size.
   */
  public final static String BIN_BASE64  = "bin.base64";
  /**
   * Hexadecimal digits representing octets. Treats each nibble as a hex digit and encodes
   * as a separate Byte. (1 octet is encoded as 2.) No limit on size.
   */
  public final static String BIN_HEX     = "bin.hex";
  /**
   * Universal Resource Identifier.
   */
  public final static String URI         = "uri";
  /**
   * Universally Unique ID. Hexadecimal digits representing octets.
   * Optional embedded hyphens are ignored.
   */
  public final static String UUID        = "uuid";
  
  public final static int UI1_INT          = "ui1".hashCode();
  public final static int UI2_INT          = "ui2".hashCode();
  public final static int UI4_INT          = "ui4".hashCode();
  public final static int I1_INT           = "i1".hashCode();
  public final static int I2_INT           = "i2".hashCode();
  public final static int I4_INT           = "i4".hashCode();
  public final static int INT_INT          = "int".hashCode();
  public final static int R4_INT           = "r4".hashCode();
  public final static int R8_INT           = "r8".hashCode();
  public final static int NUMBER_INT       = "number".hashCode();
  public final static int FIXED_14_4_INT   = "fixed.14.4".hashCode();
  public final static int FLOAT_INT        = "float".hashCode();
  public final static int CHAR_INT         = "char".hashCode();
  public final static int STRING_INT       = "string".hashCode();
  public final static int DATE_INT         = "date".hashCode();
  public final static int DATETIME_INT     = "dateTime".hashCode();
  public final static int DATETIME_TZ_INT  = "dateTime.tz".hashCode();
  public final static int TIME_INT         = "time".hashCode();
  public final static int TIME_TZ_INT      = "time.tz".hashCode();
  public final static int BOOLEAN_INT      = "boolean".hashCode();
  public final static int BIN_BASE64_INT   = "bin.base64".hashCode();
  public final static int BIN_HEX_INT      = "bin.hex".hashCode();
  public final static int URI_INT          = "uri".hashCode();
  public final static int UUID_INT         = "uuid".hashCode();

}
