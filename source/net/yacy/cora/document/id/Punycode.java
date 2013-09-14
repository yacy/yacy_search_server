/**
 * Copyright (C) 2004, 2005, 2006, 2007  Free Software Foundation, Inc.
 *
 * Author: Oliver Hitz
 *
 * This file is part of GNU Libidn.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package net.yacy.cora.document.id;


public class Punycode {
  /* Punycode parameters */
  private final static int TMIN = 1;
  private final static int TMAX = 26;
  private final static int BASE = 36;
  private final static int INITIAL_N = 128;
  private final static int INITIAL_BIAS = 72;
  private final static int DAMP = 700;
  private final static int SKEW = 38;
  private final static char DELIMITER = '-';

  /**
   * Punycodes a unicode string.
   *
   * @param input Unicode string.
   * @return Punycoded string.
   */
  public static String encode(final String input) throws PunycodeException {
    int n = INITIAL_N;
    int delta = 0;
    int bias = INITIAL_BIAS;
    final StringBuilder output = new StringBuilder(input.length() + 1);

    // Copy all basic code points to the output
    int b = 0;
    for (int i = 0; i < input.length(); i++) {
        final char c = input.charAt(i);
        if (isBasic(c)) {
            output.append(c);
            b++;
        }
    }

    // Append delimiter
    if (b > 0) {
      output.append(DELIMITER);
    }

    int h = b;
    while (h < input.length()) {
      int m = Integer.MAX_VALUE;

      // Find the minimum code point >= n
      for (int i = 0; i < input.length(); i++) {
	final int c = input.charAt(i);
	if (c >= n && c < m) {
	  m = c;
	}
      }

      if (m - n > (Integer.MAX_VALUE - delta) / (h + 1)) {
	throw new PunycodeException(PunycodeException.OVERFLOW);
      }
      delta = delta + (m - n) * (h + 1);
      n = m;

      for (int j = 0; j < input.length(); j++) {
	final int c = input.charAt(j);
	if (c < n) {
	  delta++;
	  if (0 == delta) {
	    throw new PunycodeException(PunycodeException.OVERFLOW);
	  }
	}
	if (c == n) {
	  int q = delta;

	  for (int k = BASE;; k += BASE) {
	    int t;
	    if (k <= bias) {
	      t = TMIN;
	    } else if (k >= bias + TMAX) {
	      t = TMAX;
	    } else {
	      t = k - bias;
	    }
	    if (q < t) {
	      break;
	    }
	    output.append((char) digit2codepoint(t + (q - t) % (BASE - t)));
	    q = (q - t) / (BASE - t);
	  }

	  output.append((char) digit2codepoint(q));
	  bias = adapt(delta, h + 1, h == b);
	  delta = 0;
	  h++;
	}
      }

      delta++;
      n++;
    }

    return output.toString();
  }

  /**
   * Decode a punycoded string.
   *
   * @param input Punycode string
   * @return Unicode string.
   */
  public static String decode(final String input)
    throws PunycodeException
  {
    int n = INITIAL_N;
    int i = 0;
    int bias = INITIAL_BIAS;

    int d = input.lastIndexOf(DELIMITER);
    final StringBuilder output = new StringBuilder(d + 1);
    if (d > 0) {
        for (int j = 0; j < d; j++) {
            final char c = input.charAt(j);
            if (!isBasic(c)) {
                throw new PunycodeException(PunycodeException.BAD_INPUT);
            }
            output.append(c);
        }
        d++;
    } else {
        d = 0;
    }

    while (d < input.length()) {
      final int oldi = i;
      int w = 1;

      for (int k = BASE; ; k += BASE) {
	if (d == input.length()) {
	  throw new PunycodeException(PunycodeException.BAD_INPUT);
	}
	final int c = input.charAt(d++);
	final int digit = codepoint2digit(c);
	if (digit > (Integer.MAX_VALUE - i) / w) {
	  throw new PunycodeException(PunycodeException.OVERFLOW);
	}

	i = i + digit * w;

	int t;
	if (k <= bias) {
	  t = TMIN;
	} else if (k >= bias + TMAX) {
	  t = TMAX;
	} else {
	  t = k - bias;
	}
	if (digit < t) {
	  break;
	}
	w = w * (BASE - t);	
      }

      bias = adapt(i - oldi, output.length()+1, oldi == 0);

      if (i / (output.length() + 1) > Integer.MAX_VALUE - n) {
	throw new PunycodeException(PunycodeException.OVERFLOW);
      }

      n = n + i / (output.length() + 1);
      i = i % (output.length() + 1);
      output.insert(i, (char) n);
      i++;
    }

    return output.toString();
  }

  public final static int adapt(int delta, final int numpoints, final boolean first)
  {
    if (first) {
      delta = delta / DAMP;
    } else {
      delta = delta / 2;
    }

    delta = delta + (delta / numpoints);

    int k = 0;
    while (delta > ((BASE - TMIN) * TMAX) / 2) {
      delta = delta / (BASE - TMIN);
      k = k + BASE;
    }

    return k + ((BASE - TMIN + 1) * delta) / (delta + SKEW);
  }

  public final static boolean isBasic(final char c)
  {
    return c < 0x80;
  }

  // the following method has been added by Michael Christen
  public static boolean isBasic(final String input) {
      if (input == null) return true;
      for (int j = 0; j < input.length(); j++) {
          if (!isBasic(input.charAt(j))) return false;
      }
      return true;
  }
  
  public final static int digit2codepoint(final int d)
    throws PunycodeException
  {
    if (d < 26) {
      // 0..25 : 'a'..'z'
      return d + 'a';
    } else if (d < 36) {
      // 26..35 : '0'..'9';
      return d - 26 + '0';
    } else {
      throw new PunycodeException(PunycodeException.BAD_INPUT);
    }
  }

  public final static int codepoint2digit(final int c)
    throws PunycodeException
  {
    if (c - '0' < 10) {
      // '0'..'9' : 26..35
      return c - '0' + 26;
    } else if (c - 'a' < 26) {
      // 'a'..'z' : 0..25
      return c - 'a';
    } else {
      throw new PunycodeException(PunycodeException.BAD_INPUT);
    }
  }
  
  public static class PunycodeException
    extends Exception
  {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static final String OVERFLOW = "Overflow.";
    public static final String BAD_INPUT = "Bad input.";

    /**
     * Creates a new PunycodeException.
     *
     * @param m message.
     */
    public PunycodeException(final String m)
    {
      super(m);
    }
  }
}