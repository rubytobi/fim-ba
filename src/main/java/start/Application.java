package start;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.HttpMapperProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import Entity.Consumer;
import Entity.Fridge;

@SpringBootApplication
@EnableScheduling
public class Application {
	private static String BASE_URI = "http://localhost:8080";
	private static int countFridges = 0;
	private static final int maxFridges = 10;

	public static void main(String[] args) {
		ObjectMapper jacksonMapper = new ObjectMapper();
		jacksonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		SpringApplication.run(Application.class, args);
	}

	@Scheduled(fixedRate = 1000)
	public static void init() {
		if (countFridges < maxFridges) {
			RestTemplate rest = new RestTemplate();

			Fridge fridge = new Fridge(8, 9, 4, 2, -0.5, 0.2, 1, 5);
			Consumer consumer = new Consumer();

			fridge.setConsumer(consumer.getUUID());
			consumer.setDevice(fridge.getUUID());

			HttpEntity<Fridge> entityFridge = new HttpEntity<Fridge>(fridge, getRestHeader());

			System.out.println(BASE_URI + "/devices" + " new Fridge: " + fridge.getUUID());
			rest.exchange(BASE_URI + "/devices", HttpMethod.POST, entityFridge, UUID.class);

			HttpEntity<Consumer> entityConsumer = new HttpEntity<Consumer>(consumer, getRestHeader());

			System.out.println(BASE_URI + "/consumers" + " new Consumer: " + consumer.getUUID());
			rest.exchange(BASE_URI + "/consumers", HttpMethod.POST, entityConsumer, UUID.class);

			countFridges++;
		}
	}

	public static HttpHeaders getRestHeader() {
		// Prepare acceptable media type
		List<MediaType> acceptableMediaTypes = new ArrayList<MediaType>();
		acceptableMediaTypes.add(MediaType.APPLICATION_JSON);

		// Prepare header
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(acceptableMediaTypes);
		// Pass the new person and header

		return headers;
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
