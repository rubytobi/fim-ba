package Util;

import java.util.UUID;
import java.util.regex.Pattern;

import Entity.Offer;

public class API {
	private static String api;
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

	public API marketplace() {
		uri += "/marketplace";
		return this;
	}

	public API demand(UUID uuid) {
		uri += "/demands/" + uuid;
		return this;
	}

	public API invalidate() {
		uri += "/invalidate";
		return this;
	}

	public API answer() {
		uri += "/answer";
		return this;
	}

	public API confirmLoadprofile() {
		uri += "/confirmLoadprofile";
		return this;
	}

	public Object loadprofiles() {
		uri += "/loadprofiles";
		return this;
	}

	public API replace(UUID uuid) {
		uri += "/replace/" + uuid.toString();
		return this;
	}

	public API cancel() {
		uri += "/cancel";
		return this;
	}

	public API changeRequests(UUID uuid) {
		uri += "/changeRequests/" + uuid;
		return this;
	}
}
