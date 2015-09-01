package start;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
			for (Offer o : c.getAllOffers().getBody()) {
				map.put(o.getUUID(), o);
			}
		}

		return map;
	}

	@RequestMapping("/index")
	public List<Offer> index() {
		Collection<Offer> offers = getAllOffers().values();
		List<Offer> offerList = Arrays.asList(offers.toArray(new Offer[offers.size()]));

		Collections.sort(offerList, new Comparator<Offer>() {

			@Override
			public int compare(Offer o1, Offer o2) {
				return -1 * Integer.compare(o1.getAllLoadprofiles().size(), o2.getAllLoadprofiles().size());
			}
		});

		return offerList;
	}

	@RequestMapping("/graph")
	public void updateGraph() {
		NetworkGraph.instance().update();
	}
}
