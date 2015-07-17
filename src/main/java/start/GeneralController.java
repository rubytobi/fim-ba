package start;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.UUID;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Container.ConsumerContainer;
import Entity.Consumer;
import Entity.Offer;
import Util.DateTime;
import it.uniroma1.dis.wsngroup.gexf4j.core.EdgeType;
import it.uniroma1.dis.wsngroup.gexf4j.core.Gexf;
import it.uniroma1.dis.wsngroup.gexf4j.core.Graph;
import it.uniroma1.dis.wsngroup.gexf4j.core.Mode;
import it.uniroma1.dis.wsngroup.gexf4j.core.Node;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.Attribute;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeClass;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeList;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeType;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.GexfImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.StaxGraphWriter;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.data.AttributeListImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.viz.NodeShape;

@RestController
public class GeneralController {
	private HashMap<UUID, Offer> getAllOffers() {
		HashMap<UUID, Offer> map = new HashMap<UUID, Offer>();

		for (Consumer c : ConsumerContainer.instance().getAll()) {
			for (Offer o : c.getAllOffers()) {
				map.put(o.getUUID(), o);
			}
		}

		return map;
	}

	@RequestMapping("/index")
	public HashMap<UUID, Offer> index() {
		return getAllOffers();
	}

	@RequestMapping("/graph")
	public String graph() {
		Gexf gexf = new GexfImpl();
		GregorianCalendar date = DateTime.now();

		gexf.getMetadata().setLastModified(date.getTime()).setCreator("Tobias Ruby")
				.setDescription("Consumer-Offer Network");
		gexf.setVisualization(true);
		Graph graph = gexf.getGraph();
		graph.setDefaultEdgeType(EdgeType.UNDIRECTED).setMode(Mode.STATIC);

		AttributeList attrList = new AttributeListImpl(AttributeClass.NODE);
		graph.getAttributeLists().add(attrList);

		Attribute attUrl = attrList.createAttribute("0", AttributeType.STRING, "url");
		Attribute attIndegree = attrList.createAttribute("1", AttributeType.FLOAT, "indegree");
		Attribute attFrog = attrList.createAttribute("2", AttributeType.BOOLEAN, "frog").setDefaultValue("true");

		HashMap<UUID, Node> map = new HashMap<UUID, Node>();

		for (Consumer c : ConsumerContainer.instance().getAll()) {
			Node node = graph.createNode(c.getUUID().toString());
			node.setLabel(c.getUUID().toString());
			node.setSize(20);
			node.getShapeEntity().setNodeShape(NodeShape.DIAMOND);

			map.put(c.getUUID(), node);
		}

		for (Offer o : getAllOffers().values()) {
			Node node = graph.createNode(o.getUUID().toString());
			node.getShapeEntity().setNodeShape(NodeShape.SQUARE);

			for (UUID consumerUUID : o.getAllLoadprofiles().keySet()) {
				node.connectTo("0", map.get(consumerUUID));
			}
		}

		// Node gephi = graph.createNode("0");
		// gephi.setLabel("Gephi").setSize(20).getAttributeValues().addValue(attUrl,
		// "http://gephi.org")
		// .addValue(attIndegree, "1");
		// gephi.getShapeEntity().setNodeShape(NodeShape.DIAMOND).setUri("GephiURI");
		//
		// Node webatlas = graph.createNode("1");
		// webatlas.setLabel("Webatlas").getAttributeValues().addValue(attUrl,
		// "http://webatlas.fr").addValue(attIndegree,
		// "2");
		//
		// gephi.connectTo("0", webatlas);

		String file = "C:/xampp/htdocs/gexf/static_graph_sample.gexf";

		StaxGraphWriter graphWriter = new StaxGraphWriter();
		File f = new File(file);
		Writer out;
		try {
			out = new FileWriter(f, false);
			graphWriter.writeToStream(gexf, out, "UTF-8");
			System.out.println(f.getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
		}

		return "<a href=\"http://localhost/gexf/\">http://localhost/gexf/</a>";
	}
}
