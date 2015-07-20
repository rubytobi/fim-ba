package Util;

import java.util.Collection;
import java.util.UUID;

import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.Graphs;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.view.Viewer;
import Entity.Offer;
import start.GeneralController;

public class NetworkGraph {
	private static NetworkGraph instance;
	private Graph graph;
	private String offerStyle = "fill-color: green;";
	private String title = "TRuby - Graph";
	private UUID graphUUID = UUID.randomUUID();

	public static NetworkGraph instance() {
		if (instance == null) {
			instance = new NetworkGraph();
		}

		return instance;
	}

	private NetworkGraph() {
		graph = Graphs.synchronizedGraph(new MultiGraph(title));

		// Basiseinstellungen
		graph.setStrict(false);
		graph.setAutoCreate(true);

		/**
		 * Input form here:
		 * http://graphstream-project.org/doc/Tutorials/Graph-Visualisation_1.1/
		 */
		graph.addAttribute("ui.title", "FIM-BA: Consumer <-> Offer relashionships @ " + DateTime.timestamp());
		graph.addAttribute("ui.quality");
		graph.addAttribute("ui.antialias");

		// anzeigen eines leeren graphen um den Viewer zu bekommen
		Viewer viewer = graph.display();

		// Dem Viewer sagen, dass nur das fenster gesschossen werden soll und
		// nicht das ganze Programm
		viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.HIDE_ONLY);
	}

	/**
	 * Wird periodisch aufgerufen
	 */
	public void update() {
		graph.clear();

		Collection<Offer> collectionOffers = GeneralController.getAllOffers().values();
		Offer[] allOffers = collectionOffers.toArray(new Offer[collectionOffers.size()]);

		for (Offer o : allOffers) {
			Log.d(graphUUID, "display offer [" + o.toString() + "]");

			Node n = graph.addNode(o.getUUID().toString());
			n.addAttribute("ui.style", offerStyle + " size: " + (o.getCount() + 10) + "px;");
			// n.addAttribute("ui.label", o.getUUID().toString());
		}

		for (Offer o : allOffers) {
			for (UUID consumerUUID : o.getAllLoadprofiles().keySet()) {
				Log.d(graphUUID,
						"adding graph edge from [" + consumerUUID.toString() + "] to [" + o.getUUID().toString() + "]");
				graph.addEdge(UUID.randomUUID().toString(), consumerUUID.toString(), o.getUUID().toString());
			}
		}
	}
}
