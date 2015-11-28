package Util;

import java.util.Collection;
import java.util.UUID;

import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.Graphs;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.view.Viewer;

import Entity.Loadprofile;
import Entity.Offer;
import start.GeneralController;

public class NetworkGraph {
	private static NetworkGraph instance;
	private Graph graph;
	private String offerStyle = "fill-color: green;";
	private String loadprofileStyle = "fill-color: red;";
	private String deltaLoadprofileStyle = "fill-color: red; shape: box;";
	private String title = "TRuby - Graph";

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
	public void update(String graphVersion) {
		if (graphVersion.equals("now")) {
			now();
		} else {
			next();
		}
	}

	private void next() {
		graph.clear();

		Collection<Offer> collectionOffers = GeneralController.getAllOffers().values();
		Offer[] allOffers = collectionOffers.toArray(new Offer[collectionOffers.size()]);

		for (Offer o : allOffers) {
			String a = o.getDate();
			String b = DateTime.ToString(DateTime.nextTimeSlot());

			if (a.equals(b)) {
				Node offer = graph.addNode(o.getUUID().toString());
				offer.addAttribute("ui.style", offerStyle);

				for (UUID c : o.getAllLoadprofiles().keySet()) {
					graph.addEdge(UUID.randomUUID().toString(), o.getUUID().toString(), c.toString());
				}
			}
		}

	}

	private void now() {
		graph.clear();

		Collection<Offer> collectionOffers = GeneralController.getAllOffers().values();
		Offer[] allOffers = collectionOffers.toArray(new Offer[collectionOffers.size()]);

		for (Offer o : allOffers) {
			if (o.getDate().equals(DateTime.currentTimeSlot())) {
				Node offer = graph.addNode(o.getUUID().toString());
				offer.addAttribute("ui.style", offerStyle);

				for (UUID c : o.getAllLoadprofiles().keySet()) {
					graph.addEdge(UUID.randomUUID().toString(), o.getUUID().toString(), c.toString());
				}
			}
		}
	}

}
