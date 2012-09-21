/**
 *  Battle.java
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

import java.util.Map;


public class Battle<
                    SpecificRole extends Role,
                    SpecificFinding extends Finding<SpecificRole>,
                    SpecificModel extends Model<SpecificRole, SpecificFinding>
                   >{


    public Battle(
            SpecificModel startModel,
            Map<SpecificRole, ContextFactory<SpecificRole, SpecificFinding, SpecificModel>> contexts,
            long relaxtime) {
        int cores = Runtime.getRuntime().availableProcessors();
        Engine<SpecificRole, SpecificFinding, SpecificModel> engine = new Engine<SpecificRole, SpecificFinding, SpecificModel>(cores);
        Agent<SpecificRole, SpecificFinding, SpecificModel> agent;
        engine.start();
        SpecificModel currentModel = startModel;
        ContextFactory<SpecificRole, SpecificFinding, SpecificModel> cfactroy;
        while (true) {
            cfactroy = contexts.get(currentModel.currentRole());
            agent = new Agent<SpecificRole, SpecificFinding, SpecificModel>(cfactroy.produceContext(currentModel));
            engine.inject(agent);
            agent.getContext().awaitTermination(relaxtime, false);
            if (agent.getContext().hasNoResults()) {
                System.out.println("battle terminated, "+ agent.getModel().currentRole() + " found no finding");
                break;
            }
            Challenge<SpecificRole, SpecificFinding, SpecificModel> challenge = agent.getContext().takeResult();
            if (challenge == null) {
                // lost the game
                System.out.println("lost the game: " + agent.getModel().currentRole());
                System.exit(1);
            }
            System.out.println("finding " + challenge.getFinding().toString());
            currentModel.applyFinding(challenge.getFinding());
            currentModel.nextRole();
            System.out.println(currentModel.toString());
        }
        engine.stop();
    }
}
