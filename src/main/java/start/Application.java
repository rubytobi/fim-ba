package start;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import Entity.Consumer;
import Entity.Fridge;
import Packet.FridgeCreation;
import Util.Log;

@SpringBootApplication
@EnableScheduling
public class Application {
	private static String BASE_URI = "http://localhost:8080";
	private static int countFridges = 0;
	private static final int maxFridges = 5;
	private DateFormat myDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+0200'");

	public static void main(String[] args) {
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");

		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));

		SpringApplicationBuilder builder = new SpringApplicationBuilder(Application.class);
		builder.headless(false).run(args);
	}

	@Bean
	public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
		MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
		ObjectMapper mapper = new ObjectMapper();

		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

		mapper.setDateFormat(myDateFormat);

		jsonConverter.setObjectMapper(mapper);
		return jsonConverter;
	}

	@Scheduled(fixedRate = 100)
	public static void init() {
		if (countFridges < maxFridges) {
			countFridges++;
			RestTemplate rest = new RestTemplate();

			String url;

			FridgeCreation fridgeCreation = new FridgeCreation(8, 9, 4, 2, -0.5, 0.2, 1, 5);

			HttpEntity<FridgeCreation> entityFridge = new HttpEntity<FridgeCreation>(fridgeCreation, getRestHeader());

			url = BASE_URI + "/devices";
			ResponseEntity<UUID> responseFridge = rest.exchange(BASE_URI + "/devices", HttpMethod.POST, entityFridge,
					UUID.class);

			HttpEntity<Void> entityConsumer = new HttpEntity<Void>(getRestHeader());

			url = BASE_URI + "/consumers";
			ResponseEntity<UUID> responseConsumer = rest.exchange(url, HttpMethod.POST, entityConsumer, UUID.class);

			url = BASE_URI + "/consumers/" + responseConsumer.getBody() + "/link/" + responseFridge.getBody();
			rest.exchange(url, HttpMethod.POST, entityFridge, UUID.class);

			url = BASE_URI + "/devices/" + responseFridge.getBody() + "/link/" + responseConsumer.getBody();
			rest.exchange(url, HttpMethod.POST, entityFridge, UUID.class);
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

	@Scheduled(fixedRate = 1000)
	public static void pingAllDevices() {
		RestTemplate rest = new RestTemplate();

		ResponseEntity<Fridge[]> devices = rest.exchange(BASE_URI + "/devices", HttpMethod.GET, null, Fridge[].class);

		for (Device d : devices.getBody()) {
			try {
				rest.exchange(BASE_URI + "/devices/" + d.getUUID() + "/ping", HttpMethod.GET, null, Void.class);
			} catch (HttpServerErrorException e) {
			}
		}
	}

	@Scheduled(fixedRate = 1000)
	public static void pingAllConsumers() {
		RestTemplate rest = new RestTemplate();

		ResponseEntity<Consumer[]> consumers = rest.exchange(BASE_URI + "/consumers", HttpMethod.GET, null,
				Consumer[].class);

		for (Consumer c : consumers.getBody()) {
			String url = BASE_URI + "/consumers/" + c.getUUID() + "/ping";

			try {
				rest.exchange(url, HttpMethod.GET, null, Void.class);
			} catch (HttpServerErrorException e) {
			}
		}
	}

	@Scheduled(fixedRate = 5000)
	public static void pingMarketplace() {
		RestTemplate rest = new RestTemplate();

		try {
			rest.exchange(BASE_URI + "/marketplace/ping", HttpMethod.POST, null, Void.class);
		} catch (HttpServerErrorException e) {
		}
	}
}
