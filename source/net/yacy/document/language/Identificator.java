// Identificator.java
// -----------------------
// (C) by Marc Nause; marc.nause@audioattack.de
// first published on http://www.yacy.net
// Braunschweig, Germany, 2008
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.language;

import java.util.ArrayList;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.Language;

import net.yacy.cora.util.ConcurrentLog;

/**
 * This class can try to identify the language a text is written in.
 */
public final class Identificator {

    private Detector detector;
    private Language language;

    /**
     * Default constructor. Requires the DetectorFactory language profiles to be loaded before.
     */
    public Identificator() {
        try {
            this.detector = DetectorFactory.create();
        } catch (LangDetectException e) {
            ConcurrentLog.logException(e);
        }
    }

    /**
     * Append a word to the text to be analyzed.
     * Analysis takes letter case into account (this means word should not be upper- or lower cased)
     * @param word
     */
    public void add(final String word) {
        if (word == null || this.detector == null) {
            return;
        }
        this.detector.append(" " + word); // detector internally caches text up to maxtextlen = default = 10000 chars
    }

    /**
     * Get the detected language with highest probability
     * if detection probability is above 0.3 (30%)
     * Underlying detector differentiates zh-cn and zh-tw, these are returned as zh here.
     * @return 2 char language code (ISO 639-1)
     */
    public String getLanguage() {
        if(this.detector != null) {
            try {
                ArrayList<Language> probabilities = this.detector.getProbabilities();
                if(probabilities.isEmpty()) return null;
                this.language = this.detector.getProbabilities().get(0);
            } catch (LangDetectException e) {
                // this contains mostly the message "no features in text"
                //ConcurrentLog.logException(e);
                return null;
            }
            // Return language only if probability is higher than 30% to account for missing language profiles
            if (this.language.prob > 0.3) {
                if (this.language.lang.length() == 2) {
                    return this.language.lang;
                }
                return this.language.lang.substring(0,2);
            }
        }

        return null;

    }

    /**
     * Get the probability of the detected language (returned by {@link #getLanguage()})
     * @return 0.0 to 1.0
     */
    public double getProbability() {
        if (language != null) {
            return language.prob;
        }
        return 0.0;
    }

}
