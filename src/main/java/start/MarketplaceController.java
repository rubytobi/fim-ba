package start;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import Entity.Marketplace;
import Entity.Offer;
import Packet.EndOfNegotiation;
import Packet.SearchParams;
import Util.DateTime;
import Util.ResponseBuilder;
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

	@RequestMapping(value = "/marketplace/search", method = RequestMethod.POST)
	public ResponseEntity<Offer[]> searchOffers(@RequestBody SearchParams params) {
		return Marketplace.instance().search(params);
	}

	@RequestMapping(value = "/marketplace/search/now", method = RequestMethod.GET)
	public ResponseEntity<Offer[]> searchOffersNow() {
		SearchParams params = new SearchParams(DateTime.currentTimeSlot(), Double.NEGATIVE_INFINITY,
				Double.POSITIVE_INFINITY);
		return Marketplace.instance().search(params);
	}

	@RequestMapping(value = "/marketplace/search/next", method = RequestMethod.GET)
	public ResponseEntity<Offer[]> searchOffersNextHour() {
		SearchParams params = new SearchParams(DateTime.nextTimeSlot(), Double.NEGATIVE_INFINITY,
				Double.POSITIVE_INFINITY);
		return Marketplace.instance().search(params);
	}

	@RequestMapping(value = "/marketplace/offers", method = RequestMethod.POST)
	public ResponseEntity<Void> addOffer(@RequestBody Offer offer) {
		Marketplace.instance().receiveOffer(offer);
		return ResponseBuilder.returnVoid(Marketplace.instance());
	}

	@RequestMapping(value = "/marketplace/offers/{uuid}", method = RequestMethod.GET)
	public Offer getOffer(@RequestBody UUID uuid) {
		return Marketplace.instance().getOffer(uuid);
	}

	@RequestMapping(value = "/marketplace/offer/{uuid}/invalidate", method = RequestMethod.GET)
	public ResponseEntity<Void> removeOffer(@RequestBody UUID uuid) {
		Marketplace.instance().removeOffer(uuid, false);
		return ResponseBuilder.returnVoid(Marketplace.instance());
	}

	@RequestMapping(value = "/marketplace/ping", method = RequestMethod.POST)
	public void ping() {
		Marketplace.instance().ping();
	}

	@RequestMapping(value = "/marketplace/endOfNegotiation", method = RequestMethod.POST)
	public ResponseEntity<Void> endOfNegotiation(@RequestBody EndOfNegotiation end) {
		Marketplace.instance().endOfNegotiation(end);
		return ResponseBuilder.returnVoid(Marketplace.instance());
	}

	@RequestMapping(value = "/marketplace/offer/{uuid}/receiveAnswerChangeRequestLoadprofile", method = RequestMethod.GET)
	public ResponseEntity<Void> receiveAnswerChangeRequestLoadprofile(@RequestBody ChangeRequestLoadprofile cr) {
		Marketplace.instance().receiveAnswerChangeRequestLoadprofile(cr);
		return ResponseBuilder.returnVoid(Marketplace.instance());
	}
}
