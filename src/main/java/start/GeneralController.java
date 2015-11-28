package start;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wordnik.swagger.annotations.ApiOperation;

import Container.ConsumerContainer;
import Entity.Consumer;
import Entity.Offer;
import Util.DateTime;
import Util.NetworkGraph;

@RestController
@RequestMapping(value = Application.Params.VERSION)
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

	@ApiOperation(value = "Get all the currently available offers", notes = "timeindependent")
	@RequestMapping("/index")
	public List<Offer> index() {
		Collection<Offer> offers = getAllOffers().values();
		List<Offer> offerList = Arrays.asList(offers.toArray(new Offer[offers.size()]));

		Collections.sort(offerList, new Comparator<Offer>() {

			@Override
			public int compare(Offer o1, Offer o2) {
				return -1 * Integer.compare(o1.getNumLoadprofiles(), o2.getNumLoadprofiles());
			}
		});

		return offerList;
	}

	@RequestMapping("/time")
	public String[] time() {
		String[] array = new String[] { DateTime.ToString(DateTime.currentTimeSlot()),
				DateTime.ToString(DateTime.nextTimeSlot()) };

		System.out.println(array[0]);
		System.out.println(array[1]);

		return array;
	}

	@RequestMapping("/graph/{version}")
	public void updateGraph(@PathVariable String version) {
		NetworkGraph.instance().update(version);
	}
}
