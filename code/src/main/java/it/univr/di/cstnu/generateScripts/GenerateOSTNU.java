package it.univr.di.cstnu.generateScripts;

import it.univr.di.cstnu.util.OSTNURandomGenerator;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GenerateOSTNU {

    private static final Logger LOG = Logger.getLogger(GenerateOSTNU.class.getName());

    public static void main(String[] args) throws IOException {
        int dcInstances = 1;
        int notDcInstances = 1;
        double edgeProbability = .2d;
        int nodes = 30;
        int contingentNodes = 5;
        int maxWeight = 150;
        int maxContingentWeight = 99;

        for (int i = 3; i < 5; i++) {
            int oracles = i;
            OSTNURandomGenerator generator = new OSTNURandomGenerator(dcInstances, notDcInstances, nodes, contingentNodes, edgeProbability, maxWeight, maxContingentWeight, oracles);
            generator.generateInstancesWithOracle();
        }
    }
}
