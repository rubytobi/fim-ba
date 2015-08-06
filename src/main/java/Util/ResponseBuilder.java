package Util;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import Entity.Identifiable;

public class ResponseBuilder<T> {
	private HttpHeaders headers = new HttpHeaders();
	private T body = null;
	private URI location = null;
	private Identifiable who = null;

	public ResponseBuilder(Identifiable who) {
		this.who = who;
	}

	public ResponseBuilder<T> location(URI uri) {
		this.location = uri;
		return this;
	}

	public ResponseBuilder<T> body(T body) {
		this.body = body;
		return this;
	}

	public ResponseBuilder<T> headers(String key, String value) {
		this.headers.add(key, value);
		return this;
	}

	public ResponseEntity<T> build() {
		if (location != null) {
			headers.setLocation(location);
		}

		headers.set("UUID", who.getUUID().toString());
		headers.set("UUID", who.getClass().getSimpleName());

		Log.d(who.getUUID(), "response [headers=" + headers + ", body=" + body + "]");
		return new ResponseEntity<T>(body, headers, HttpStatus.OK);
	}

}
