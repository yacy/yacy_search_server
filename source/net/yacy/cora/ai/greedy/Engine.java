/**
 *  Engine.java
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
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

public class Engine<
                    SpecificRole extends Role,
                    SpecificFinding extends Finding<SpecificRole>,
                    SpecificModel extends Model<SpecificRole, SpecificFinding>
                   > {

    protected final PriorityBlockingQueue<Agent<SpecificRole, SpecificFinding, SpecificModel>> agentQueue;
    protected final PriorityBlockingQueue<Challenge<SpecificRole, SpecificFinding, SpecificModel>> challengeQueue;
    protected final Agent<SpecificRole, SpecificFinding, SpecificModel> poisonAgent;
    protected final Challenge<SpecificRole, SpecificFinding, SpecificModel> poisonChallenge;
    protected final ConcurrentHashMap<SpecificModel, List<SpecificFinding>> settings;
    protected final ConcurrentHashMap<Asset<SpecificRole, SpecificFinding, SpecificModel>, SpecificModel> assets;
    private final int cores;
    
    
    public Engine(int cores) {
        this.cores = cores;
        this.poisonAgent = new Agent<SpecificRole, SpecificFinding, SpecificModel>();
        this.poisonChallenge = new Challenge<SpecificRole, SpecificFinding, SpecificModel>();
        this.agentQueue = new PriorityBlockingQueue<Agent<SpecificRole, SpecificFinding, SpecificModel>>();
        this.challengeQueue = new PriorityBlockingQueue<Challenge<SpecificRole, SpecificFinding, SpecificModel>>();
        this.settings = new ConcurrentHashMap<SpecificModel, List<SpecificFinding>>();
        this.assets = new ConcurrentHashMap<Asset<SpecificRole, SpecificFinding, SpecificModel>, SpecificModel>();
    }
    
    public void start() {
        int c = (this.cores == 0) ? Runtime.getRuntime().availableProcessors() : this.cores;
        for (int i = 0; i < c; i++) {
            new SettingRunner().start();
            new AssetRunner().start();
        }
    }
    
    public void stop() {
        int c = (this.cores == 0) ? Runtime.getRuntime().availableProcessors() : this.cores;
        for (int i = 0; i < c; i++) {
            this.agentQueue.put(this.poisonAgent);
            this.challengeQueue.put(this.poisonChallenge);
        }
    }
    
    public void inject(Agent<SpecificRole, SpecificFinding, SpecificModel> agent) {
        agent.getContext().reset();
        this.agentQueue.put(agent);
    }
    
    
    public class SettingRunner extends Thread {
        @Override
        public void run() {
            Agent<SpecificRole, SpecificFinding, SpecificModel> agent;
            Challenge<SpecificRole, SpecificFinding, SpecificModel> challenge;
            Context<SpecificRole, SpecificFinding, SpecificModel> context;
            SpecificModel model;
            List<SpecificFinding> findings;
            PriorityQueue<Challenge<SpecificRole, SpecificFinding, SpecificModel>> preChallenge = new PriorityQueue<Challenge<SpecificRole, SpecificFinding, SpecificModel>>();
            try {
                while ((agent = agentQueue.take()) != poisonAgent) {
                    // check termination of that goal
                    context = agent.getContext();
                    if (context.isCompleted()) continue;
                    
                    // produce findings in a setting environment and try to get it from a cache
                    model = agent.getModel();
                    findings = settings.get(model);
                    if (findings == null) {
                        findings = model.explore();
                        settings.put(model, findings);
                    }
                    
                    // branch
                    for (SpecificFinding finding: findings) {
                        challenge = new Challenge<SpecificRole, SpecificFinding, SpecificModel>(agent, finding);
                        //System.out.println("finding: " + finding.toString() + ", priority: " + finding.getPriority());
                        preChallenge.add(challenge);
                    }
                    challengefeeder: while (!preChallenge.isEmpty()) {
                        if (context.isCompleted()) break challengefeeder;
                        challengeQueue.put(preChallenge.poll());
                        agent.incInstances();
                    }
                    //while (!challengeQueue.isEmpty()) System.out.println("finding: " + challengeQueue.take().getChallenge().getFinding().toString());
                }
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public class AssetRunner extends Thread {
        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            Challenge<SpecificRole, SpecificFinding, SpecificModel> challenge;
            Agent<SpecificRole, SpecificFinding, SpecificModel> agent, nextAgent;
            Goal<SpecificRole, SpecificFinding, SpecificModel> goal;
            Context<SpecificRole, SpecificFinding, SpecificModel> context;
            Asset<SpecificRole, SpecificFinding, SpecificModel> asset;
            SpecificRole role;
            SpecificModel nextModel = null;
            try {
                while ((challenge = challengeQueue.take()) != poisonChallenge) {
                    assert challenge != null;
                    agent = challenge.getAgent();
                    agent.decInstances();
                    context = agent.getContext();
                    goal = context.getGoal();
                    role = challenge.getFinding().getRole();
                    
                    // check termination by catching the global termination signal
                    // and check expiration before applying finding
                    // this shall not place the model to the results because
                    // it has not the last finding assigned to the current user
                    
                    if (context.isCompleted()) continue;
                    
                    String debug = agent.getModel().toString() + "\nwill apply " + challenge.getFinding().toString();
                    System.out.println(debug);
                    if (debug.equals("[],[3, 2],[1]\nwill apply 2 -> 1")) {
                        System.out.println("one more please");
                    }
                    // apply finding: compute next model
                    // avoid double computation of findings using cached assets
                    if (context.useAssetCache()) {
                        asset = new Asset<SpecificRole, SpecificFinding, SpecificModel>(agent.getModel(), challenge.getFinding());
                        nextModel = assets.get(asset);
                        if (nextModel == null) {
                            // generate model clone and apply finding
                            try {
                                nextModel = (SpecificModel) agent.getModel().clone();
                            } catch (final CloneNotSupportedException e) {
                                e.printStackTrace();
                            }
                            nextModel.applyFinding(challenge.getFinding());
                            nextModel.nextRole();
                            if (context.feedAssetCache()) assets.put(asset, nextModel);
                        }
                    } else {
                        // generate model clone and apply finding
                        try {
                            nextModel = (SpecificModel) agent.getModel().clone();
                        } catch (final CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                        nextModel.applyFinding(challenge.getFinding());
                        nextModel.nextRole();
                        if (context.feedAssetCache()) {
                            asset = new Asset<SpecificRole, SpecificFinding, SpecificModel>(agent.getModel(), challenge.getFinding());
                            assets.put(asset, nextModel);
                        }
                    }
                   
                    // prune double-occurring models:
                    // while it appears not to be a very good idea to produce models from the
                    // asset cache above and then prune with double appearing models here, this is
                    // still useful if the assets come from an earlier injection of an other agent.
                    // Because the asset cache is a global cache owned by the engine it can produce
                    // ready-computed models that not already exist in the agent cache
                    if (agent.getContext().isKnownModel(nextModel)) {
                        agent.checkInstanceCount();
                        continue;
                    }
                    // place new model into agent and record finding
                    nextAgent = new Agent<SpecificRole, SpecificFinding, SpecificModel>(agent, nextModel, challenge.getFinding());
                    nextAgent.checkInstanceCount();
                    
                    // check if we arrived at a termination point
                    SpecificRole terminationRole = nextModel.isTermination();
                    if (terminationRole != null) {
                        // the current role has a termination situation. In case that it is the start user, add a result
                        nextAgent.addResult();
                        if (goal.isFulfilled(nextAgent.getModel())) nextAgent.getContext().announceCompletion();
                        // one of the roles has terminated.
                        // prune this branch for other branches from the parent
                        //System.out.println("terminationRole = " + terminationRole);
                        if (agent.getFinding() == null) {
                            // this is the root of the search tree: a fail in the search
                            //agent.getContext().getGoal().announceFullfillment();
                        } else {
                            assert agent.getFinding() != null;
                            assert agent.getFinding().getRole() != null;
                            agent.setFindingFail();
                        }
                        //System.out.println("found winner model for " + terminationRole.toString() + ", latest finding: " + challenge.getFinding().toString() + "\n" + nextModel.toString());
                        agent.checkInstanceCount();
                        continue;
                    }
                    
                    // check time-out for snapshot
                    if (nextAgent.getContext().isSnapshotTimeout()) {
                        nextAgent.addResult();
                    }
                    
                    // check pruning
                    
                    if (goal.pruning(nextModel)) {
                        agent.checkInstanceCount();
                        continue;
                    }

                    // do not follow situations where it is known that somebody has made a fatal move in the past
                    if (agent.isPrunedByTerminationInHistory() != null) {
                        agent.checkInstanceCount();
                        continue;
                    }
                    
                    // check best move criteria
                    int ranking = agent.getRanking(role);
                    if (context.setBestMove(role, ranking)) {
                        nextAgent.addResult();
                    }
                    
                    // check snapshot
                    if (goal.isSnapshot(nextModel)) {
                        nextAgent.addResult();
                        // no pruning here
                    }
                    
                    if (context.hasNoResults()) nextAgent.addResult();
                    if (context.isCompleted()) {
                        continue;
                    }
                    
                    // stack agent for next loop
                    // using the priority of the next role
                    agentQueue.put(nextAgent);
                }
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
}
