/**
 *  Context.java
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

package net.yacy.cora.ai.greedy;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Context<
                     SpecificRole extends Role,
                     SpecificFinding extends Finding<SpecificRole>,
                     SpecificModel extends Model<SpecificRole, SpecificFinding>
                    >{

    private static final Object PRESENT = "";
    private final Goal<SpecificRole, SpecificFinding, SpecificModel> goal;
    private final SpecificModel initialModel;
    private final SpecificRole initialRole;
    private final PriorityBlockingQueue<Challenge<SpecificRole, SpecificFinding, SpecificModel>> result;
    private final ConcurrentHashMap<SpecificModel, Object> models; // caches all observed models for a double-check
    private final ConcurrentHashMap<SpecificRole, Integer> bestMove;
    private final AtomicInteger instances;
    private final long timeoutForSnapshot;
    private final Semaphore termination;
    private final boolean feedAssetCache, useAssetCache;
    private long startTime;
    private boolean fullfilled;

    protected Context(
            Goal<SpecificRole, SpecificFinding, SpecificModel> goal,
            SpecificModel initialModel,
            long timeoutForSnapshot, boolean feedAssetCache, boolean useAssetCache) {
        this.goal = goal;
        this.initialModel = initialModel;
        this.initialRole = initialModel.currentRole();
        this.models = new ConcurrentHashMap<SpecificModel, Object>();
        this.result = new PriorityBlockingQueue<Challenge<SpecificRole, SpecificFinding, SpecificModel>>();
        this.bestMove = new ConcurrentHashMap<SpecificRole, Integer>();
        this.instances = new AtomicInteger(0);
        this.timeoutForSnapshot = timeoutForSnapshot;
        this.startTime = System.currentTimeMillis();
        this.fullfilled = false;
        this.termination = new Semaphore(0);
        this.feedAssetCache = feedAssetCache;
        this.useAssetCache = useAssetCache;
    }

    public int getInstanceCount() {
        return this.instances.get();
    }

    public void incInstances() {
        this.instances.incrementAndGet();
    }

    public int decInstances() {
        return this.instances.decrementAndGet();
    }


    public boolean setBestMove(SpecificRole role, int ranking) {
        Integer b = this.bestMove.get(role);
        if (b == null) {
            this.bestMove.put(role, ranking);
            System.out.println("first move");
            return true;
        }
        if (b.intValue() < ranking) {
            System.out.println("best next move");
            this.bestMove.put(role, ranking);
            return true;
        }
        return false;
    }

    public int getBestMove(SpecificRole role) {
        Integer b = this.bestMove.get(role);
        if (b == null) return Integer.MIN_VALUE;
        return b.intValue();
    }

    public void addModel(SpecificModel model) {
        if (model.toString().equals("[],[3, 2, 1],[]")) {
            System.out.println("target");
        }
        this.models.put(model, PRESENT);
    }

    public Goal<SpecificRole, SpecificFinding, SpecificModel> getGoal() {
        return this.goal;
    }

    public SpecificModel getInitialModel() {
        return this.initialModel;
    }

    public boolean isKnownModel(SpecificModel model) {
        return this.models.containsKey(model);
    }

    public int getKnownModelsCount() {
        return this.models.size();
    }

    public void registerResult(Agent<SpecificRole, SpecificFinding, SpecificModel> agent, SpecificFinding finding) {
        assert agent != null;
        assert agent.getFinding() != null;
        assert agent.getFinding().getRole() != null;
        assert finding != null;
        this.result.offer(new Challenge<SpecificRole, SpecificFinding, SpecificModel>(agent, finding));
    }

    public SpecificRole initialRole() {
        return this.initialRole;
    }

    /**
     * return one of the results from the problem solving computation.
     * if there is no result available, then return null.
     * a null result shows that there is either no solution at all
     * or it was not possible to find one within the given time-out frame
     * @return e challenge as a result or null if there is no result.
     */
    public Challenge<SpecificRole, SpecificFinding, SpecificModel> takeResult() {
        try {
            while (!this.result.isEmpty()) {
                Challenge<SpecificRole, SpecificFinding, SpecificModel> resultChallenge = this.result.take();
                Agent<SpecificRole, SpecificFinding, SpecificModel> resultAgent = resultChallenge.getAgent();
                if (resultAgent.isPrunedByTerminationInHistory(this.initialRole())) continue;
                return resultChallenge;
            }
            // if this state is reached then all possible findings will cause a lost situation

            return null;
        } catch (final InterruptedException e) {
            return null;
        }
    }

    public boolean hasNoResults() {
        return this.result.isEmpty();
    }

    public int countResults() {
        return this.result.size();
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.termination.drainPermits();
    }

    public boolean feedAssetCache() {
        return this.feedAssetCache;
    }

    public boolean useAssetCache() {
        return this.useAssetCache;
    }

    public void announceCompletion() {
        this.fullfilled = true;
        this.termination.release();
    }

    public boolean isCompleted() {
        if (this.fullfilled) return true;
        //System.out.println("runtime = " + runtime);
        //boolean expired = System.currentTimeMillis() - this.startTime > this.timeoutForSnapshot;
        //if (expired) this.termination.release();
        //return expired;
        return false;
    }

    public boolean isSnapshotTimeout() {
        if (this.fullfilled) return true;
        boolean expired = System.currentTimeMillis() - this.startTime > this.timeoutForSnapshot;
        return expired;
    }

    public void awaitTermination(long pauseAfterAquire, boolean announceCompletionAfterTimeOut) {
        try {
            if (this.termination.tryAcquire(this.timeoutForSnapshot, TimeUnit.MILLISECONDS)) {
                Thread.sleep(pauseAfterAquire);
            } else {
                System.out.println("timed-out termination");
            }
        } catch (final InterruptedException e) {}
        if (announceCompletionAfterTimeOut) announceCompletion();
    }

    public void printReport(int count) {
        System.out.println("==== " + this.getKnownModelsCount() + " models computed");
        Challenge<SpecificRole, SpecificFinding, SpecificModel> resultChallenge;
        Agent<SpecificRole, SpecificFinding, SpecificModel> resultAgent;
        SpecificFinding resultFinding;
        int i = 0;
        while (count > 0) {
            if (this.countResults() == 0) break;
            resultChallenge = this.takeResult();
            resultAgent = resultChallenge.getAgent();
            resultFinding = resultChallenge.getFinding();
            List<SpecificFinding> p = resultAgent.listPrunedByTerminationInHistory();
            //if (p != null) continue;
            //if (resultAgent.isPrunedByTerminationInHistory(resultAgent.initialModel.currentRole())) continue;
            System.out.println("==== result " + i++ + "/" + this.countResults());
            Finding<SpecificRole>[] moves = resultAgent.getFindings();
            System.out.print("==== moves: ");
            if (moves == null) System.out.println("null"); else {
                for (int j = 0; j < moves.length; j++) {System.out.print(moves[j]); if (j < moves.length - 1) System.out.print(", "); }
            } System.out.println();
            System.out.println("==== first move: " + resultFinding);
            assert resultFinding.getPriority() == resultAgent.getRanking(this.initialRole());
            System.out.println("==== ranking: " + resultFinding.getPriority());
            SpecificRole winner = resultAgent.getModel().isTermination();
            if (winner != null) System.out.println("==== the winner is " + winner.toString());
            if (p == null) {
                System.out.println("==== pruning is null");
            } else {
                System.out.print("==== fail-moves are ");
                for (SpecificFinding f: p) System.out.print(f + " ");
                System.out.println("");
            }
            System.out.println(resultAgent.getModel().toString());
            count--;
        }
    }
}
