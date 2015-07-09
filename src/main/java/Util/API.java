package Util;

import java.util.UUID;

public class API {
	private String uri = "http://localhost:8080";

	public API consumers() {
		uri += "/consumers";
		return this;
	}

	public API consumers(UUID uuid) {
		uri += "/consumers/" + uuid;
		return this;
	}

	public API devices() {
		uri += "/devices";
		return this;
	}

	public API devices(UUID uuid) {
		uri += "/devices/" + uuid;
		return this;
	}

	public API offers() {
		uri += "/offers";
		return this;
	}

	public API offers(UUID uuid) {
		uri += "/offers/" + uuid;
		return this;
	}

	public API link() {
		uri += "/link";
		return this;
	}

	public API confirm() {
		uri += "/confirm";
		return this;
	}

	public API decline() {
		uri += "/decline";
		return this;
	}

	public String toString() {
		return uri;
	}

	public API confirm(UUID key) {
		uri += "/confirm/" + key;
		return this;
	}

}
