package Util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.sun.research.ws.wadl.Response;

import Entity.Consumer;
import start.Device;

@SuppressWarnings("hiding")
public class API2<Request, Response> {
	private String uri = null;
	private Class<Response> responseType = null;
	private Response response = null;

	public API2(Class<Response> responseType) {
		this.responseType = responseType;
		clear();
	}

	public API2<Request, Response> consumers() {
		uri += "/consumers";
		return this;
	}

	public API2<Request, Response> consumers(UUID uuid) {
		uri += "/consumers/" + uuid;
		return this;
	}

	public API2<Request, Response> devices() {
		uri += "/devices";
		return this;
	}

	public API2<Request, Response> devices(UUID uuid) {
		uri += "/devices/" + uuid;
		return this;
	}

	public API2<Request, Response> offers() {
		uri += "/offers";
		return this;
	}

	public API2<Request, Response> offers(UUID uuid) {
		uri += "/offers/" + uuid;
		return this;
	}

	public API2<Request, Response> link() {
		uri += "/link";
		return this;
	}

	public API2<Request, Response> confirm() {
		uri += "/confirm";
		return this;
	}

	public API2<Request, Response> decline() {
		uri += "/decline";
		return this;
	}

	public String toString() {
		return uri;
	}

	public API2<Request, Response> confirm(UUID key) {
		uri += "/confirm/" + key;
		return this;
	}

	public API2<Request, Response> marketplace() {
		uri += "/marketplace";
		return this;
	}

	public API2<Request, Response> demand(UUID uuid) {
		uri += "/demands/" + uuid;
		return this;
	}

	public API2<Request, Response> invalidate() {
		uri += "/invalidate";
		return this;
	}

	public API2<Request, Response> answer() {
		uri += "/answer";
		return this;
	}

	public API2<Request, Response> confirmLoadprofile() {
		uri += "/confirmLoadprofile";
		return this;
	}

	public Object loadprofiles() {
		uri += "/loadprofiles";
		return this;
	}

	public API2<Request, Response> replace(UUID uuid) {
		uri += "/replace/" + uuid.toString();
		return this;
	}

	public API2<Request, Response> cancel() {
		uri += "/cancel";
		return this;
	}

	public API2<Request, Response> changeRequests(UUID uuid) {
		uri += "/changeRequests/" + uuid;
		return this;
	}

	public API2<Request, Response> receiveAnswerChangeRequestLoadprofile() {
		uri += "/receiveAnswerChangeRequestLoadprofile";
		return this;
	}

	public API2<Request, Response> confirmByMarketplace() {
		uri += "/confirmByMarketplace";
		return this;
	}

	public void call(Identifiable who, HttpMethod how, Request what) {
		Log.d(who.getUUID(), uri);

		RestTemplate rest = new RestTemplate();

		HttpEntity<Request> entity = new HttpEntity<Request>(what, headers(who));

		try {
			ResponseEntity<Response> response = rest.exchange(uri, how, entity, responseType);

			if (response.getBody() instanceof Void && response.getBody() == null) {
				Log.d(who.getUUID(), "no response expected and nothing received");
			}

			this.response = response.getBody();
		} catch (Exception e) {
			Log.d(who.getUUID(), e.toString() + " - -- - " + who.toString() + " - -- - " + what.toString());
		}

		this.response = null;
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

		if (who instanceof Device) {
			headers.add("Device-UUID", who.getUUID().toString());
		} else if (who instanceof Consumer) {
			headers.add("Consumer-UUID", who.getUUID().toString());
		}

		return headers;
	}

	public API2<Request, Response> changeRequest() {
		uri += "/changeRequest";
		return this;
	}

	public void clear() {
		uri = "http://localhost:8080";
		response = null;
	}

}
