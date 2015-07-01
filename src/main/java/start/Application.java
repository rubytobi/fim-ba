package start;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
public class Application {
	private static String BASE_URI = "http://localhost:8080";
	private static int countFridges = 0;
	private static final int maxFridges = 10;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Scheduled(fixedRate = 1000)
	public static void init() {
		if (countFridges < maxFridges) {
			RestTemplate rest = new RestTemplate();

			Fridge fridge = new Fridge(5, 5, 5, 5, 5, 5, 5, 5);

			// Prepare acceptable media type
			List<MediaType> acceptableMediaTypes = new ArrayList<MediaType>();
			acceptableMediaTypes.add(MediaType.APPLICATION_JSON);

			// Prepare header
			HttpHeaders headers = new HttpHeaders();
			headers.setAccept(acceptableMediaTypes);
			// Pass the new person and header
			HttpEntity<Fridge> entity = new HttpEntity<Fridge>(fridge, headers);

			System.out.println(BASE_URI + "/devices" + " new Fridge: " + fridge.getUUID());
			rest.exchange(BASE_URI + "/devices", HttpMethod.POST, entity, UUID.class);

			countFridges++;
		}
	}

	@Scheduled(fixedRate = 15000)
	public static void pingAll() {
		RestTemplate rest = new RestTemplate();

		ResponseEntity<Fridge[]> devices = rest.exchange(BASE_URI + "/devices", HttpMethod.GET, null, Fridge[].class);

		for (Device d : devices.getBody()) {
			ResponseEntity<String> s = rest.exchange(BASE_URI + "/devices/" + d.getUUID() + "/ping", HttpMethod.GET,
					null, String.class);
		}
	}
}
