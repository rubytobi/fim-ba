package start;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import Entity.Marketplace;
import Entity.Offer;
import Packet.EndOfNegotiation;
import Packet.ChangeRequestLoadprofile;

@RestController
public class MarketplaceController {
	@RequestMapping(value = "/marketplace/status", method = RequestMethod.GET)
	public Map<String, Object> status() {
		return Marketplace.instance().status();
	}

	@RequestMapping(value = "/marketplace/eexprice", method = RequestMethod.GET)
	public Map<String, Object> eexPrice() {
		Map<String, Object> map = new TreeMap<String, Object>();
		map.put("eexprice", Marketplace.getEEXPrice());
		return map;
	}

	@RequestMapping(value = "/marketplace/prediction", method = RequestMethod.GET)
	public ResponseEntity<double[]> getPrediction() {
		return Marketplace.instance().getPrediction();
	}

	@RequestMapping(value = "/marketplace/supplies/{count}", method = RequestMethod.GET)
	public Offer[] getSupplies(@PathVariable int count) {
		return Marketplace.instance().getSupplies(count);
	}

	@RequestMapping(value = "/marketplace/demands", method = RequestMethod.POST)
	public void postDemand(@RequestBody Offer offer) {
		Marketplace.instance().putOffer(offer);
	}

	@RequestMapping(value = "/marketplace/demands/{uuid}", method = RequestMethod.GET)
	public Offer getDemand(@RequestBody UUID uuid) {
		return Marketplace.instance().getDemand(uuid);
	}

	@RequestMapping(value = "/marketplace/offer/{uuid}/invalidate", method = RequestMethod.GET)
	public void getOffer(@RequestBody UUID uuid) {
		Marketplace.instance().removeOffer(uuid, false);
	}

	@RequestMapping(value = "/marketplace/ping", method = RequestMethod.POST)
	public void ping() {
		Marketplace.instance().ping();
	}

	@RequestMapping(value = "/marketplace/endOfNegotiation", method = RequestMethod.POST)
	public void endOfNegotiation(@RequestBody EndOfNegotiation end) {
		Marketplace.instance().endOfNegotiation(end);
	}

	@RequestMapping(value = "/marketplace/offer/{uuid}/receiveAnswerChangeRequestLoadprofile", method = RequestMethod.GET)
	public void receiveAnswerChangeRequestLoadprofile(@RequestBody ChangeRequestLoadprofile cr) {
		Marketplace.instance().receiveAnswerChangeRequestLoadprofile(cr);
	}
}
