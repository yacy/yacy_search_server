/**
 *  ConnectFour.java
 *  Copyright 2009 by Michael Peter Christen, Frankfurt a. M., Germany
 *  First published 03.12.2009 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.cora.ai.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yacy.cora.ai.greedy.AbstractFinding;
import net.yacy.cora.ai.greedy.AbstractModel;
import net.yacy.cora.ai.greedy.Battle;
import net.yacy.cora.ai.greedy.ContextFactory;
import net.yacy.cora.ai.greedy.Finding;
import net.yacy.cora.ai.greedy.Goal;
import net.yacy.cora.ai.greedy.Model;
import net.yacy.cora.ai.greedy.Role;

public class ConnectFour {

    static final int width = 7;
    static final int height = 6;

    public static enum Coin implements Role {
        red('*'),
        blue('#');
        private final String c;
        Coin(char c) {this.c = String.valueOf(c);}
        @Override
        public Coin nextRole() {
            return (this == red) ? blue : red;
        }
        @Override
        public String toString() {return this.c;}
        public final static Coin[] allCoins = {red, blue};
    }

    public static class Move extends AbstractFinding<Coin> implements Finding<Coin> {
        protected final int column;
        public Move(Coin role, int column) {
            super(role, (column > (width / 2)) ? (width - column - 1) : column);
            this.column = column;
        }
        @Override
        public Object clone() {
            return new Move(this.getRole(), this.column);
        }
        public int getColumn() {
            return this.column;
        }
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Move)) return false;
            Move m = (Move) other;
            return this.column == m.column;
        }
        @Override
        public int hashCode() {
            return this.column;
        }
        @Override
        public String toString() {
            return super.getRole().toString() + ":" + Integer.toString(this.column);
        }
    }

    public static class Board extends AbstractModel<Coin, Move> implements Model<Coin, Move>, Cloneable {

        Coin[] b; // 2 dimensions folded: array starts in the bottom left and first position in second row is at index position <width>

        /**
         * create a board with start configuration: empty board
         * @param nextRole
         */
        public Board(Coin startPlayer) {
            super(startPlayer);
            this.b = new Coin[height * width];
            for (int i = 0; i < this.b.length; i++) this.b[i] = null;
        }

        /**
         * clone a board and return a new board
         * @param board
         */
        public Board(Board board) {
            super(board.currentRole());
            this.b = new Coin[board.b.length];
            System.arraycopy(board.b, 0, this.b, 0, this.b.length);
        }

        @Override
        public Object clone() {
            return new Board(this);
        }

        public boolean columnFull(int column) {
            return this.b[(height - 1) * width + column] != null;
        }

        public Coin getCell(int column, int row) {
            return this.b[row * width + column];
        }

        @Override
        public void applyFinding(Move nextStep) {
            int column = nextStep.getColumn();
            int row = 0;
            while (row < height && this.b[row * width + column] != null) row++;
            if (row == height) throw new RuntimeException("column " + column + " is full");
            this.b[row * width + column] = nextStep.getRole();
        }

        @Override
        public int hashCode() {
            int c = 0;
            Coin x;
            for (int i = 0; i < this.b.length; i++) {
                x = this.b[i];
                if (x != null) c += (i + 1) * (x.ordinal() + 1);
            }
            return c;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Board)) return false;
            Board om = (Board) o;
            Coin c0, c1;
            for (int i = 0; i < this.b.length; i++) {
                c0 = this.b[i];
                c1 = om.b[i];
                if (!(c0 == null ? c1 == null : c0.equals(c1))) return false;
            }
            return true;
        }

        private boolean count4(int x, int y, int xi, int yi, Coin c) {
            int steps = 4;
            Coin cell;
            while (steps-- > 0) {
                cell = this.b[y * width + x];
                if (cell == null || !cell.equals(c)) return false;
                x += xi;
                y += yi;
            }
            return true;
        }

        private int countSame(int x, int y, int xi, int yi, Coin c) {
            int rb = 0;
            int steps = 4;
            Coin cell;
            while (steps-- > 0) {
                cell = this.b[y * width + x];
                if (cell != null) {
                    if (cell.equals(c)) rb++; else return rb;
                }
                x += xi;
                y += yi;
            }
            return rb;
        }

        @Override
        public Coin isTermination() {
            for (Coin coin: Coin.allCoins) if (isTermination(coin)) return coin;
            return null;
        }

        @Override
        public boolean isTermination(Coin coin) {
            // waagerecht
            for (int x = 0; x < width - 3; x++)
                for (int y = 0; y < height; y++)
                    if (count4(x, y, 1, 0, coin)) return true;
            // senkrecht
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height - 3; y++)
                    if (count4(x, y, 0, 1, coin)) return true;
            // slash-schraeg
            for (int x = 0; x < width - 3; x++)
                for (int y = 0; y < height - 3; y++)
                    if (count4(x, y, 1, 1, coin)) return true;
            // backslash-schraeg
            for (int x = 0; x < width - 3; x++)
                for (int y = 3; y < height; y++)
                    if (count4(x, y, 1, -1, coin)) return true;

            return false;
        }

        @Override
        public int getRanking(int findings, Coin coin) {
            return 2 * getRankingSingle(coin) - getRankingSingle(coin.nextRole()) - 3 * findings;
        }

        private int getRankingSingle(Coin coin) {
            int r = 0;
            // waagerecht
            for (int x = 0; x < width - 3; x++)
                for (int y = 0; y < height; y++)
                    r += countSame(x, y, 1, 0, coin);
            // senkrecht
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height - 3; y++)
                    r += countSame(x, y, 0, 1, coin);
            // slash-schraeg
            for (int x = 0; x < width - 3; x++)
                for (int y = 0; y < height - 3; y++)
                    r += countSame(x, y, 1, 1, coin);
            // backslash-schraeg
            for (int x = 0; x < width - 3; x++)
                for (int y = 3; y < height; y++)
                    r += countSame(x, y, 1, -1, coin);
            return r;
        }

        private int getPriority(Move finding) {
            if (finding.column <= width / 2) return finding.column;
            return width - 1 - finding.column;
        }

        @Override
        public List<Move> explore() {
            ArrayList<Move> moves = new ArrayList<Move>();
            for (int i = 0; i < width; i++) {
                Move move = new Move(this.currentRole(), i);
                move.setPriority(getPriority(move));
                if (!columnFull(i)) moves.add(move);
            }
            return moves;
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder((width + 1) * height);
            Coin coin;
            for (int row = height - 1; row >= 0; row--) {
                s.append('\"');
                for (int column = 0; column < width; column++) {
                    coin = this.b[row * width + column];
                    s.append((coin == null) ? " " : coin.toString());
                }
                if (row == 0) s.append('\"'); else s.append("\"+\n");
            }
            return s.toString();
        }
    }

    public static class Strategy implements Goal<Coin, Move, Board> {

        public Strategy() {
        }

        @Override
        public boolean isFulfilled(Board model) {
            return false;
        }

        @Override
        public boolean isSnapshot(Board model) {
            return false;
        }

        @Override
        public boolean pruning(Board model) {
            return false;
        }

    }


    public static void battle() {
        Map<Coin, ContextFactory<Coin, Move, Board>> strategies = new HashMap<Coin, ContextFactory<Coin, Move, Board>>();
        ContextFactory<Coin, Move, Board> redFactroy = new ContextFactory<Coin, Move, Board>(new Strategy(), 3000, false, false);
        ContextFactory<Coin, Move, Board> blueFactroy = new ContextFactory<Coin, Move, Board>(new Strategy(), 50, false, false);
        strategies.put(Coin.red, redFactroy);
        strategies.put(Coin.blue, blueFactroy);
        /*Battle<Coin, Move, Board> battle =*/ new Battle<Coin, Move, Board>(new Board(Coin.red), strategies, 2000);
    }

    public static void main(String[] args) {

        battle();
        /*
        int cores = Runtime.getRuntime().availableProcessors();
        Engine<Coin, Move, Board> engine = new Engine<Coin, Move, Board>(cores);
        Agent<Coin, Move, Board> agent;
        engine.start();
        long comptime = 60;
        long relaxtime = 20;

        agent = new Agent<Coin, Move, Board>(new Board(Coin.red), new Strategy(comptime, false, false));  // red begins
        engine.inject(agent);
        agent.getTeorem().getGoal().awaitTermination(relaxtime);
        System.out.println("=========== terminated ==========");
        agent.getTeorem().printReport(100);
        */
        /*
        agent = new Agent<Coin, Move, Board>(new Board(), Coin.red, new Strategy(comptime, false, true));  // red begins
        engine.inject(agent);
        agent.getGoal().awaitTermination(relaxtime);
        System.out.println("=========== terminated ==========");
        agent.printReport(10);
         */

        //engine.stop();
    }

}
