/**
 *  Model.java
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


/**
 * a Model is the universe where specific Roles act with specific Findings.
 * The Model may be a playing field if applied for game playing or it may
 * be a theorem set if applied for automated theorem proving.
 * A specific Model should provide a simple initializer that creates a start model,
 * which may be a startup board in case of game playing or a standard model in case
 * of automated theorem proving.
 * Models provide exploration (creation of possible Findings) and ranking (evaluation
 * of a model situation to produce a ordering on possible model instances)
 *
 * @param <SpecificRole>
 * @param <SpecificFinding>
 */
public interface Model<SpecificRole extends Role, SpecificFinding extends Finding<SpecificRole>> extends Cloneable {
    
    /**
     * Create a list of possible findings in the current model instance.
     * This may be the list of possible moves for the current role/player in case of
     * game playing or the list of possible inference rule applications in case of
     * automated theorem proving.
     * @return an iterator on the list of all possible findings for the current model situation
     */
    public List<SpecificFinding> explore();
    
    /**
     * apply a finding to the current model.
     */
    public void applyFinding(SpecificFinding finding);

    /**
     * compute the ranking for the given role, the higher the better.
     * @param findings the number of findings that have applied so far
     * @param role the role for which the ranking shall be computed
     * @return the ranking for the given finding size and role
     */
    public int getRanking(int findings, SpecificRole role);

    /**
     * the model contains a status about roles that may act next
     * @return the next role
     */
    public SpecificRole currentRole();

    /**
     * switch to the next role. The current role is migrated to the
     * new role status, or the current rule is replaced with a new role
     */
    public void nextRole();
    
    /**
     * based on the model content and the model next-role compute a hash code
     * do not include a computation based on latest actions
     * @return a hash code
     */
    @Override
    public int hashCode();

    /**
     * equal mathod according to hash method
     * @param om
     * @return true if other model is equal to the current model.
     */
    @Override
    public boolean equals(Object om);

    /**
     * clone the model
     * @return a top-level cloned model
     * @throws CloneNotSupportedException
     */
    public Object clone() throws CloneNotSupportedException;
    
    /**
     * check if this model is in a termination status for a given role
     * 
     * @param role the role that is checked
     * @return true if the role caused termination
     */
    public boolean isTermination(SpecificRole role);
    
    /**
     * check if this model is in a termination status for anyone
     * 
     * @return the role for which this is a termination
     *         or null if there is no termination yet
     */
    public SpecificRole isTermination();
    
}
