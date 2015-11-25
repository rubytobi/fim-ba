package Util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.GregorianCalendar;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.sun.research.ws.wadl.Response;

import Entity.Identifiable;

@SuppressWarnings("hiding")
public class API<Request, Response> {
	private String uri = null;
	private Class<Response> responseType = null;
	private Response response = null;
	private UUID responseUUID;

	public API(Class<Response> responseType) {
		this.responseType = responseType;
		clear();
	}

	public API<Request, Response> consumers() {
		uri += "/consumers";
		return this;
	}

	public API<Request, Response> consumers(UUID uuid) {
		uri += "/consumers/" + uuid;
		return this;
	}

	public API<Request, Response> devices() {
		uri += "/devices";
		return this;
	}

	public API<Request, Response> devices(UUID uuid) {
		uri += "/devices/" + uuid;
		return this;
	}

	public API<Request, Response> offers() {
		uri += "/offers";
		return this;
	}

	public API<Request, Response> offers(UUID uuid) {
		uri += "/offers/" + uuid;
		return this;
	}

	public API<Request, Response> link(UUID uuid) {
		uri += "/link/" + uuid;
		return this;
	}

	public API<Request, Response> confirm() {
		uri += "/confirm";
		return this;
	}

	public API<Request, Response> decline() {
		uri += "/decline";
		return this;
	}

	public String toString() {
		return uri;
	}

	public API<Request, Response> confirm(UUID key) {
		uri += "/confirm/" + key;
		return this;
	}

	public API<Request, Response> confirm(GregorianCalendar date) {
		uri += "/confirm/" + DateTime.ToString(date);
		System.out.println(uri);
		return this;
	}

	public API<Request, Response> marketplace() {
		uri += "/marketplace";
		return this;
	}

	public API<Request, Response> invalidate() {
		uri += "/invalidate";
		return this;
	}

	public API<Request, Response> answer() {
		uri += "/answer";
		return this;
	}

	public API<Request, Response> confirmLoadprofile() {
		uri += "/confirmLoadprofile";
		return this;
	}

	public Object loadprofiles() {
		uri += "/loadprofiles";
		return this;
	}

	public API<Request, Response> replace(UUID uuid) {
		uri += "/replace/" + uuid.toString();
		return this;
	}

	public API<Request, Response> cancel() {
		uri += "/cancel";
		return this;
	}

	public API<Request, Response> changeRequests(UUID uuid) {
		uri += "/changeRequests/" + uuid;
		return this;
	}

	public API<Request, Response> changeRequestConsumer() {
		uri += "/changeRequestConsumer";
		return this;
	}

	public API<Request, Response> changeRequest() {
		uri += "/changeRequest";
		return this;
	}

	public API<Request, Response> answerChangeRequest() {
		uri += "/answerChangeRequest";
		return this;
	}

	public API<Request, Response> receiveAnswerChangeRequestLoadprofile() {
		uri += "/receiveAnswerChangeRequestLoadprofile";
		return this;
	}

	public API<Request, Response> confirmByMarketplace() {
		uri += "/confirmByMarketplace";
		return this;
	}

	public void call(Identifiable who, HttpMethod how, Request what) {
		Log.d(who.getUUID(), uri + " body: " + what);

		RestTemplate rest = new RestTemplate();

		HttpEntity<Request> entity = new HttpEntity<Request>(what, headers(who));

		ResponseEntity<Response> response = null;
		try {
			response = rest.exchange(uri, how, entity, responseType);
			this.response = response.getBody();

			try {
				this.responseUUID = UUID.fromString(response.getHeaders().getFirst("UUID"));
			} catch (Exception e) {
				System.out.println("no uuid: " + uri);
			}

			return;
		} catch (Exception e) {
			Log.d(who.getUUID(), e + " - -- - " + who + " - -- - " + what + " - -- - " + response);
		}

		this.response = null;
		this.responseUUID = null;
	}

	public Response getResponse() {
		return response;
	}

	private HttpHeaders headers(Identifiable who) {
		// Prepare acceptable media type
		List<MediaType> acceptableMediaTypes = new ArrayList<MediaType>();
		acceptableMediaTypes.add(MediaType.APPLICATION_JSON);

		// Prepare header
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(acceptableMediaTypes);
		// Pass the new person and header

		headers.add("UUID", who.getUUID().toString());
		headers.add("Class", who.getClass().getSimpleName());

		return headers;
	}

	public void clear() {
		uri = "http://localhost:8080";
		response = null;
	}

	public API<Request, Response> offers(int i) {
		uri += "/offers?count=" + i;
		return this;
	}

	public API<Request, Response> prediction() {
		uri += "/prediction";
		return this;
	}

	public UUID getSenderUUID() {
		return responseUUID;
	}

	public API<Request, Response> changeRequestMarketplace() {
		uri += "/changeRequestMarketplace";
		return this;
	}

	public API<Request, Response> negotiation(UUID uuid) {
		uri += "/negotiation/" + uuid;
		return this;
	}

	public API<Request, Response> priceChangeRequest() {
		uri += "/priceChangeRequest";
		return this;
	}

	public API<Request, Response> endOfNegotiation() {
		uri += "/endOfNegotiation";
		return this;
	}

	public API<Request, Response> fridge() {
		uri += "/fridge";
		return this;
	}

	public API<Request, Response> bhkw() {
		uri += "/bhkw";
		return this;
	}

	public API<Request, Response> negotiation() {
		uri += "/negotiation";
		return this;
	}

	public API<Request, Response> answerToPriceChangeRequest(UUID uuid) {
		uri += uuid + "/answerChangeRequest";
		return this;
	}

	public API<Request, Response> search() {
		uri += "/search";
		return this;
	}
}
