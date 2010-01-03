package net.yacy.ai.greedy;

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
            agent.getContext().awaitTermination(relaxtime);
            if (agent.getContext().hasNoResults()) {
                System.out.println("battle terminated, "+ agent.getModel().currentRole() + " found no finding");
                break;
            } else {
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
        }
        engine.stop();
    }
}
