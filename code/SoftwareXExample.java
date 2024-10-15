import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import it.univr.di.cstnu.algorithms.CSTNU;
import it.univr.di.cstnu.algorithms.CSTNU.CSTNUCheckStatus;
import it.univr.di.cstnu.algorithms.WellDefinitionException;
import it.univr.di.cstnu.graph.CSTNUEdge;
import it.univr.di.cstnu.graph.EdgeSupplier;
import it.univr.di.cstnu.graph.TNGraph;
import it.univr.di.cstnu.graph.TNGraphMLReader;
import it.univr.di.cstnu.graph.TNGraphMLWriter;
import it.univr.di.cstnu.visualization.CSTNUStaticLayout;;

public class SoftwareXExample {
    
	public static void main(String[] args)  {
		String input = "../data/20210811SoftwareX.cstnu";
		String output = "../results/20210811SoftwareX_checked.cstnu";
		File graphSource = new File(input);
		TNGraphMLReader<CSTNUEdge> loader = new TNGraphMLReader<>();
		TNGraph<CSTNUEdge> graph = null;

		System.out.print("Loading the network "+ graphSource.getPath() +" ...");
		try {
			graph = loader.readGraph(graphSource, EdgeSupplier.DEFAULT_CSTNU_EDGE_CLASS);
		} catch (IOException | ParserConfigurationException | SAXException e) {
			System.err.println("Format error in the instance source file: " + e.getMessage());
			System.exit(1);
		}
		System.out.print("done.\nChecking its dynamic controllability...");

		CSTNU cstnu = new CSTNU(graph);
		CSTNUCheckStatus checkStatus = null;
		try {
			checkStatus = cstnu.dynamicControllabilityCheck();
		} catch (WellDefinitionException e) {
			System.err.println("The cstnu instance is not well defined: " + e.getMessage());
			System.exit(1);
		}
		System.out.println("done\n" + checkStatus);

		File resultGraphF = new File(output);
		System.out.println("Saving to "+ resultGraphF.getPath());
		TNGraph<CSTNUEdge> resultGraph = cstnu.getGChecked();
		TNGraphMLWriter graphWriter = new TNGraphMLWriter(new CSTNUStaticLayout<>(resultGraph));
		try {
			graphWriter.save(resultGraph, resultGraphF);
		} catch (IOException e) {
			System.err.println("Problem in saving the result network: " + e.getMessage());
			System.exit(1);
		}
		
		System.out.println("execution finished.");
	}
}