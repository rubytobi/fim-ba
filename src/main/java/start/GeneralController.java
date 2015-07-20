package start;

import java.util.HashMap;
import java.util.UUID;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Container.ConsumerContainer;
import Entity.Consumer;
import Entity.Offer;
import Util.NetworkGraph;

@RestController
public class GeneralController {

	public static HashMap<UUID, Offer> getAllOffers() {
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
	public void updateGraph() {
		NetworkGraph.instance().update();
	}
}
