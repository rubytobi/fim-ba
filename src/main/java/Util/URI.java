package Util;

import java.util.UUID;

public class URI {
	public static String toConsumer(UUID uuid) {
		return "http://localhost:8080/consumers/" + uuid;
	}

}
