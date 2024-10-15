package it.univr.di.cstnu.algorithms;

import it.univr.di.cstnu.graph.CSTNPSUEdge;
import it.univr.di.cstnu.graph.EdgeSupplier;
import it.univr.di.cstnu.graph.TNGraph;
import it.univr.di.cstnu.graph.TNGraphMLReader;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * FTNU model was introduced as an evolution of CSTNPSU one for having a simpler name and a DC checking algorithm able
 * to determine the guarded bounds in a correct way.
 * <br>
 * In this library, we maintain the class CSTNPSU updated and the class FTNU as a wrapper of CSTNPSU.
 *
 * @author posenato
 */
@SuppressWarnings("ClassWithoutLogger")
public class FTNU extends CSTNPSU {

	/**
	 * Helper constructor for FTNU.
	 * <br>
	 * This constructor is useful for making easier the use of this class in environment like Node.js-Java
	 *
	 * @param graphXML the TNGraph to check in GraphML format
	 *
	 * @throws java.io.IOException                            if any error occurs during the graphXML reading
	 * @throws javax.xml.parsers.ParserConfigurationException if graphXML contains character that cannot be parsed
	 * @throws org.xml.sax.SAXException                       if graphXML is not valid
	 */
	public FTNU(String graphXML) throws IOException, ParserConfigurationException, SAXException {
		this();
		setG((new TNGraphMLReader<CSTNPSUEdge>()).readGraph(graphXML, EdgeSupplier.DEFAULT_CSTNPSU_EDGE_CLASS));
	}

	/**
	 * Default constructor, package use only!
	 */
	protected FTNU() {
	}

	/**
	 * @param graph TNGraph to check
	 */
	public FTNU(TNGraph<CSTNPSUEdge> graph) {
		super(graph);
	}

	/**
	 * @param graph        TNGraph to check
	 * @param givenTimeOut timeout for the check in seconds
	 */
	public FTNU(TNGraph<CSTNPSUEdge> graph, int givenTimeOut) {
		super(graph, givenTimeOut);
	}

	/**
	 *
	 * @param args ignored
	 */
	@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
	public static void main(String[] args) {
		final FTNU ftnu = new FTNU();
		try {
			ftnu.dynamicControllabilityCheck();
			ftnu.getPrototypalLink();
		} catch (WellDefinitionException e) {
			throw new RuntimeException(e);
		}
	}
}
