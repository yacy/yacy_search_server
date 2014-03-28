/**
 *  Rating
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 25.08.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-03-08 02:51:51 +0100 (Di, 08 Mrz 2011) $
 *  $LastChangedRevision: 7567 $
 *  $LastChangedBy: low012 $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.sorting;

import java.util.Comparator;

public class Rating<A> {

    private final A object;
    private long score;

    public Rating(final A o, final long score) {
        this.object = o;
        this.score = score;
    }

    public void setScore(final long score) {
        this.score = score;
    }

    public long getScore() {
        return this.score;
    }

    public A getObject() {
        return this.object;
    }

    public final static ScoreComparator scoreComparator = new ScoreComparator();

    public static class ScoreComparator implements Comparator<Rating<?>> {

        @Override
        public int compare(final Rating<?> arg0, final Rating<?> arg1) {
            if (arg0.getScore() < arg1.getScore()) return -1;
            if (arg0.getScore() > arg1.getScore()) return 1;
            return 0;
        }
    }

    public static class FoldedScoreComparator<B extends Comparable<B>> implements Comparator<Rating<B>> {

        @Override
        public int compare(final Rating<B> arg0, final Rating<B> arg1) {
            final int c = scoreComparator.compare(arg0, arg1);
            if (c != 0) return c;
            return arg0.getObject().compareTo(arg1.getObject());
        }
    }

}
