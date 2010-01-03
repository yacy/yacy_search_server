// Agent.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.12.2009 on http://yacy.net;
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-05-28 01:51:34 +0200 (Do, 28 Mai 2009) $
// $LastChangedRevision: 5988 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package net.yacy.ai.greedy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * the greedy agent
 * this is the main object that contains all elements of a greedy computation
 * To use an agent, one must define
 * - a Model (for the problem that shall be solved) and
 * - a Goal (specifies when an Agent reached a solution for the problem)
 * Then instances of the Model and the Goal must be handed over to the Agent,
 * and the Agent must be feeded into the Engine.
 * The Engine solves the problem and returns the solution as sequence of Findings
 * in this object instance that can be retrieved with takeBestResult()
 *
 * @param <SpecificRole>
 * @param <SpecificFinding>
 * @param <SpecificModel>
 */
public class Agent<
                   SpecificRole extends Role,
                   SpecificFinding extends Finding<SpecificRole>,
                   SpecificModel extends Model<SpecificRole, SpecificFinding>
                  > implements
                    Comparator<Agent<SpecificRole, SpecificFinding, SpecificModel>>,
                    Comparable<Agent<SpecificRole, SpecificFinding, SpecificModel>> {
    
    private Context<SpecificRole, SpecificFinding, SpecificModel> context;
    private SpecificModel model;
    private SpecificFinding finding;
    private boolean findingFail; // a flag set by child nodes that signal that a specific role has terminated the branch below the current node
    private Agent<SpecificRole, SpecificFinding, SpecificModel> parentAgent; // the next parent node
    private int pathlength;
    
    /**
     * create a poison agent
     */
    protected Agent() {
        context = null;
        model = null;
        finding = null;
        findingFail = false;
        parentAgent = null;
        pathlength = 0;
    }
    
    public Agent(Context<SpecificRole, SpecificFinding, SpecificModel> context) {
        this.context = context;
        this.model = context.getInitialModel();
        this.finding = null;
        this.findingFail = false;
        this.parentAgent = null;
        this.pathlength = 0;
    }
    
    /**
     * Create a clone of the current agent with an loaded finding as attached resource.
     * This is used to branch into alternatives of the given agent configuration.
     * Some elements of the agent must be cloned, other must be extended and one must be referenced without cloning:
     * - challenge: is cloned with an attached finding. This is the actual branching into alternatives
     * - findings: is cloned and extended at the same time. This carries the history of the branch
     * - result: is just referenced because it is used to place a feed-back to the original goal request
     * - initialModel: just referenced
     * - goal: just referenced
     * @param finding
     * @return
     */
    public Agent(Agent<SpecificRole, SpecificFinding, SpecificModel> parentAgent, SpecificModel newModel, SpecificFinding newFinding) {
        this.parentAgent = parentAgent;
        this.context = parentAgent.context;
        this.model = newModel;
        this.context.addModel(newModel);
        this.finding = newFinding;
        this.findingFail = false;
        this.pathlength = parentAgent.pathlength + 1;
    }
    
    public void checkInstanceCount() {
        // in case that there are no agents left, store the current state
        // and fire a shutdown signal
        if (this.context.getInstanceCount() > 0) return;
        addResult();
        //if (this.getContext().countResults() > 0) this.context.getGoal().announceFullfillment();
    }

    public void incInstances() {
        this.context.incInstances();
    }
    
    public int decInstances() {
        return this.context.decInstances();
    }
    
    public Context<SpecificRole, SpecificFinding, SpecificModel> getContext() {
        return this.context;
    }
    
    public SpecificModel getModel() {
        return this.model;
    }
    
    public Finding<SpecificRole> getFinding() {
        return this.finding;
    }
    
    public int getPathLength() {
        return this.pathlength;
    }
    
    @SuppressWarnings("unchecked")
    public void addResult() {
        // create a challenge that contains the ranking of the current model with the initial role
        // as priority setting attached to the initial move that the user must do to reach the current status
        // find the first move.
        if (this.finding == null) return;
        if (!this.context.getInitialModel().currentRole().equals(this.finding.getRole())) return;
        SpecificFinding finding = null;
        try {
            // because several branches may find the same finding at the root
            // they will attempt to assign different priorities as rankings from the
            // leaf of the search tree. Therefore the findings must be cloned.
            finding = (SpecificFinding) getResultFinding().clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        finding.setPriority(this.model.getRanking(this.pathlength, this.context.initialRole()));
        assert this.finding != null;
        assert this.finding.getRole() != null;
        //System.out.println("finding: " + finding);
        this.context.registerResult(this, finding);
    }
    
    
    public void setFindingFail() {
        assert this.finding != null;
        assert this.finding.getRole() != null;
        this.findingFail = true;
    }
    
    public boolean getFindingFail() {
        return this.findingFail;
    }
    
    public SpecificFinding isPrunedByTerminationInHistory() {
        Agent<SpecificRole, SpecificFinding, SpecificModel> a = this;
        while (a != null) {
            if (a.findingFail) return a.finding;
            // step up in the tree
            a = a.parentAgent;
        }
        return null;
    }
    
    public boolean isPrunedByTerminationInHistory(SpecificRole role) {
        Agent<SpecificRole, SpecificFinding, SpecificModel> a = this;
        while (a != null) {
            assert a != null;
            //assert a.finding != null;
            //assert a.finding.getRole() != null;
            if (a.findingFail && a.finding.getRole().equals(role)) return true;
            // step up in the tree
            a = a.parentAgent;
        }
        return false;
    }
    
    public List<SpecificFinding> listPrunedByTerminationInHistory() {
        ArrayList<SpecificFinding> list = new ArrayList<SpecificFinding>(this.pathlength);
        Agent<SpecificRole, SpecificFinding, SpecificModel> a = this;
        while (a != null) {
            if (a.findingFail) list.add(a.finding);
            // step up in the tree
            a = a.parentAgent;
        }
        return list;
    }
    
    public int hashCode() {
        return this.model.hashCode();
    }
    
    @SuppressWarnings("unchecked")
    public boolean equals(Object om) {
        if (!(om instanceof Agent)) return false;
        Agent<SpecificRole, SpecificFinding, SpecificModel> a = (Agent<SpecificRole, SpecificFinding, SpecificModel>) om;
        return this.model.equals(a.model);
    }
    
    @SuppressWarnings("unchecked")
    public SpecificFinding[] getFindings() {
        SpecificFinding[] findings = (SpecificFinding[]) new Finding[this.pathlength];
        int l = this.pathlength - 1;
        Agent<SpecificRole, SpecificFinding, SpecificModel> a = this;
        while (a != null && l >= 0) {
            findings[l--] = a.finding;
            a = a.parentAgent;
        }
        return findings;
    }
    
    public SpecificFinding getResultFinding() {
        int l = this.pathlength - 1;
        Agent<SpecificRole, SpecificFinding, SpecificModel> a = this;
        while (a != null && l >= 0) {
            if (l-- == 0) return a.finding;
            a = a.parentAgent;
        }
        return null;
    }
    
    public int getRanking(SpecificRole role) {
        return this.model.getRanking(this.pathlength, role);
    }
    
    public int compare(
            Agent<SpecificRole, SpecificFinding, SpecificModel> a1,
            Agent<SpecificRole, SpecificFinding, SpecificModel> a2) {
        
        // order of poison agents: they are the largest
        if (a1.context == null) return 1;
        if (a2.context == null) return -1;
        
        // by default order by ranking of the model
        SpecificRole role = a1.model.currentRole();
        if (!a2.model.currentRole().equals(role)) return 0;
        int r1 = a1.model.getRanking(a1.pathlength, role);
        int r2 = a2.model.getRanking(a2.pathlength, role);
        
        // reverse ordering to get the largest elements at the head of sort queues
        if (r1 < r2) return 1;
        if (r1 > r2) return -1;
        return 0;
    }

    public int compareTo(Agent<SpecificRole, SpecificFinding, SpecificModel> o) {
        return compare(this, o);
    }
    
}
