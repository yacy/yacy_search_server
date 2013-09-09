/**
 *  Hanoi.java
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
import java.util.List;

import net.yacy.cora.ai.greedy.AbstractFinding;
import net.yacy.cora.ai.greedy.AbstractModel;
import net.yacy.cora.ai.greedy.Agent;
import net.yacy.cora.ai.greedy.Challenge;
import net.yacy.cora.ai.greedy.Context;
import net.yacy.cora.ai.greedy.ContextFactory;
import net.yacy.cora.ai.greedy.Engine;
import net.yacy.cora.ai.greedy.Finding;
import net.yacy.cora.ai.greedy.Goal;
import net.yacy.cora.ai.greedy.Model;
import net.yacy.cora.ai.greedy.Unirole;

public class Hanoi {
    
    public static class Coin {
        public final int size;
        public Coin(int size) {
            this.size = size;
        }

        @Override
        public boolean equals(Object other) {
            return (other != null && other instanceof Coin && this.size == ((Coin) other).size);
        }

        @Override
        public int hashCode() {
            return this.size;
        }
        
        @Override
        public String toString() {
            return Integer.toString(size);
        }
    }
    
    public static class Move extends AbstractFinding<Unirole> implements Finding<Unirole> {
        public final int from, to;
        public Move(int from, int to) {
            super(Unirole.unirole);
            this.from = from;
            this.to = to;
        }
        
        public Move(int from, int to, int prio) {
            super(Unirole.unirole, prio);
            this.from = from;
            this.to = to;
        }
        
        @Override
        public Object clone() {
            return new Move(this.from, this.to, this.getPriority());
        }

        @Override
        public boolean equals(Object other) {
            return (other != null
                    && other instanceof Move
                    && this.from == ((Move) other).from
                    && this.to == ((Move) other).to);
        }

        @Override
        public int hashCode() {
            return 3 * this.from + 7 * this.to;
        }
        
        @Override
        public String toString() {
            return from + " -> " + to;
        }
    }
    
    public static class Board extends AbstractModel<Unirole, Move> implements Model<Unirole, Move>, Cloneable {
        private final List<Coin>[] stacks;
        public int moves;
        @SuppressWarnings("unchecked")
        public Board(int height) {
            super(Unirole.unirole);
            stacks = new ArrayList[3];
            for (int i = 0; i < 3; i++) this.stacks[i] = new ArrayList<Coin>();
            while (height > 0) {
                this.stacks[0].add(new Coin(height--));
            }
            this.moves = 0;
        }
        
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append(stacks[0].toString());
            s.append(",");
            s.append(stacks[1].toString());
            s.append(",");
            s.append(stacks[2].toString());
            return s.toString();
        }

        @Override
        public Object clone() {
            Board b = new Board(0);
            for (int i = 0; i < 3; i++) {
                for (Coin s: this.stacks[i]) b.stacks[i].add(s);
            }
            b.moves = this.moves;
            return b;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof Board)) return false;
            Board b = (Board) other;
            for (int i = 0; i < 3; i++) {
                if (this.stacks[i].size() != b.stacks[i].size()) return false;
                for (int j = 0; j < this.stacks[i].size(); j++) {
                    if (!this.stacks[i].get(j).equals(b.stacks[i].get(j))) return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int c = 0;
            for (int i = 0; i < 3; i++) {
                for (Coin s: this.stacks[i]) c += s.hashCode();
            }
            return c;
        }

        public void applyFinding(Move finding) {
            this.stacks[finding.to].add(this.stacks[finding.from].remove(this.stacks[finding.from].size() - 1));
            this.moves++;
        }
        
        private int getPriority(Move finding) {
            int p = 1000 - this.moves;
            if (finding.from == 0) p += 2;
            if (finding.from == 1) p--;
            if (finding.from == 2) p++;
            if (finding.to == 0) p--;
            if (finding.to == 1) p += 2;
            if (finding.to == 2) p++;
            return p;
        }

        public List<Move> explore() {
            final List<Move> moves = new ArrayList<Move>();
            for (int from = 0; from < 3; from++) {
                toloop: for (int to = 0; to < 3; to++) {
                    if (from == to) continue toloop;
                    Coin fromCoin = (this.stacks[from].isEmpty()) ? null : this.stacks[from].get(this.stacks[from].size() - 1);
                    if (fromCoin == null) continue toloop;
                    Coin toCoin = (this.stacks[to].isEmpty()) ? null : this.stacks[to].get(this.stacks[to].size() - 1);
                    if (toCoin != null && fromCoin.size >= toCoin.size) continue toloop;
                    Move move = new Move(from, to);
                    move.setPriority(getPriority(move));
                    moves.add(new Move(from, to));
                }
            }
            return moves;
        }

        public int getRanking(int findings, Unirole role) {
            return stacks[1].size() - stacks[0].size();
        }

        public boolean isTermination(Unirole role) {
            return stacks[0].isEmpty() && stacks[2].isEmpty();
        }

        public Unirole isTermination() {
            return (stacks[0].isEmpty() && stacks[2].isEmpty()) ? Unirole.unirole : null;
        }
    }
    
    public static class Strategy implements Goal<Unirole, Move, Board> {

        // we check if there was ever a fulfilled status
        // in case of a problem-solving algorithm we want to stop
        // as soon as a fulfilled status is reached.
        // therefore we use that status for the pruning method to prune all
        // tasks that may follow to the fullfillment board
        
        public Strategy() {
        }

        public boolean isFulfilled(Board model) {
            return model.isTermination(Unirole.unirole);
        }

        public boolean isSnapshot(Board model) {
            return false; // no snapshots
        }

        public boolean pruning(Board model) {
            // if the have ever found a solution then prune all other findings
            return false;
        }
        
    }
    
    public static void main(String[] args) {
        
        Engine<Unirole, Move, Board> engine = new Engine<Unirole, Move, Board>(Runtime.getRuntime().availableProcessors() + 1);
        engine.start();
        ContextFactory<Unirole, Move, Board> cfactory = new ContextFactory<Unirole, Move, Board>(new Strategy(), Long.MAX_VALUE, false, false);
        Context<Unirole, Move, Board> context = cfactory.produceContext(new Board(3));
        Agent<Unirole, Move, Board> agent = new Agent<Unirole, Move, Board>(context);
        engine.inject(agent);
        agent.getContext().awaitTermination(1000000, false);
        Challenge<Unirole, Move, Board> result = agent.getContext().takeResult();
        agent.getContext().announceCompletion();
        engine.stop();
        Finding<Unirole>[] moves = result.getAgent().getFindings();
        for (int i = 0; i < moves.length; i++) {
            System.out.println(i + ": " + moves[i].toString());
        }
        System.out.println("terminated");
    }
}
